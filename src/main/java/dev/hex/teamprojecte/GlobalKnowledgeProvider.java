package dev.hex.teamprojecte;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.gameObjs.items.Tome;
import moze_intel.projecte.network.packets.IPEPacket;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncChangePKT;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncEmcPKT;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncInputsAndLocksPKT;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncPKT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.*;

/**
 * Global knowledge provider - all players share the same knowledge and EMC pool.
 */
public class GlobalKnowledgeProvider implements IKnowledgeProvider, INBTSerializable<CompoundTag> {

    private final ItemStackHandler inputLocks;
    private static boolean isSyncing = false;

    public GlobalKnowledgeProvider(@NotNull ServerPlayer player) {
        this.inputLocks = new ItemStackHandler(9) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return EMCMappingHandler.hasEmcValue(ItemInfo.fromStack(stack));
            }

            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                // Prevent recursive syncing.
                if (isSyncing) return;
                
                isSyncing = true;
                try {
                    TeamProjectE.getAllOnlinePlayers().forEach(p -> {
                        if (!p.getUUID().equals(player.getUUID())) {
                            var cap = p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
                            if (cap instanceof GlobalKnowledgeProvider provider) {
                                provider.inputLocks.setStackInSlot(slot, getStackInSlot(slot));
                            }
                        }
                    });
                } finally {
                    isSyncing = false;
                }
            }
        };
    }

    public GlobalKnowledgeProvider(UUID uuid) {
        this.inputLocks = new ItemStackHandler(9);
    }

    private void fireChangedEvent() {
        TeamProjectE.getAllOnlinePlayers()
                .forEach(player -> NeoForge.EVENT_BUS.post(new PlayerKnowledgeChangeEvent(player.getUUID())));
    }

    private GlobalKnowledgeData getGlobalData() {
        return TPSavedData.getData().globalData;
    }

    @Override
    public boolean hasFullKnowledge() {
        return getGlobalData().hasFullKnowledge();
    }

    @Override
    public void setFullKnowledge(boolean fullKnowledge) {
        boolean changed = hasFullKnowledge() != fullKnowledge;
        getGlobalData().setFullKnowledge(fullKnowledge);
        if (changed) {
            fireChangedEvent();
            markDirty();
        }
    }

    @Override
    public void clearKnowledge() {
        boolean hasKnowledge = hasFullKnowledge() || !getGlobalData().getKnowledge().isEmpty();
        getGlobalData().clearKnowledge();
        if (hasKnowledge) {
            fireChangedEvent();
            markDirty();
        }
    }

    @Override
    public boolean hasKnowledge(@NotNull ItemInfo info) {
        if (getGlobalData().hasFullKnowledge()) {
            return true;
        }
        return getGlobalData().getKnowledge().contains(info);
    }

    @Override
    public boolean addKnowledge(@NotNull ItemInfo info) {
        if (getGlobalData().hasFullKnowledge()) {
            return false;
        }
        if (info.getItem() instanceof Tome) {
            if (info.hasModifiedComponents()) {
                info = ItemInfo.fromItem(info.getItem());
            }
            getGlobalData().addKnowledge(info);
            getGlobalData().setFullKnowledge(true);
            fireChangedEvent();
            markDirty();
            return true;
        }
        return tryAdd(info);
    }

    private boolean tryAdd(@NotNull ItemInfo cleanedInfo) {
        if (getGlobalData().addKnowledge(cleanedInfo)) {
            fireChangedEvent();
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean removeKnowledge(@NotNull ItemInfo info) {
        if (getGlobalData().hasFullKnowledge()) {
            if (info.getItem() instanceof Tome) {
                if (info.hasModifiedComponents()) {
                    info = ItemInfo.fromItem(info.getItem());
                }
                getGlobalData().removeKnowledge(info);
                getGlobalData().setFullKnowledge(false);
                fireChangedEvent();
                markDirty();
                return true;
            }
            return tryRemove(info);
        }
        return tryRemove(info);
    }

    private boolean tryRemove(@NotNull ItemInfo cleanedInfo) {
        if (getGlobalData().removeKnowledge(cleanedInfo)) {
            fireChangedEvent();
            markDirty();
            return true;
        }
        return false;
    }

    @NotNull
    @Override
    public Set<ItemInfo> getKnowledge() {
        if (getGlobalData().hasFullKnowledge()) {
            Set<ItemInfo> allKnowledge = EMCMappingHandler.getMappedItems();
            allKnowledge.addAll(getGlobalData().getKnowledge());
            return Collections.unmodifiableSet(allKnowledge);
        }
        return Collections.unmodifiableSet(getGlobalData().getKnowledge());
    }

    @NotNull
    @Override
    public IItemHandlerModifiable getInputAndLocks() {
        return inputLocks;
    }

    @Override
    public BigInteger getEmc() {
        return getGlobalData().getEmc();
    }

    @Override
    public void setEmc(BigInteger emc) {
        getGlobalData().setEmc(emc);
        markDirty();
        // Sync to all players immediately
        TeamProjectE.getAllOnlinePlayers().forEach(p -> {
            var cap = p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            if (cap != null) {
                cap.syncEmc(p);
            }
        });
    }

    @Override
    public void sync(@NotNull ServerPlayer player) {
        // Sync to all online players since everyone shares the same data
        TeamProjectE.getAllOnlinePlayers().forEach(GlobalKnowledgeProvider::sendKnowledgeSync);
    }

    private static void sendKnowledgeSync(ServerPlayer player) {
        IKnowledgeProvider cap = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if (cap != null) {
            try {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new KnowledgeSyncPKT(createAttachment((GlobalKnowledgeProvider) cap)));
            } catch (Exception e) {
                TeamProjectE.LOGGER.error("Failed to send knowledge sync packet", e);
            }
        }
    }

    private static moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment createAttachment(GlobalKnowledgeProvider provider) throws Exception {
        var attachment = new moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment();
        
        var knowledgeField = moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment.class.getDeclaredField("knowledge");
        knowledgeField.setAccessible(true);
        knowledgeField.set(attachment, provider.getKnowledge());

        var inputLocksField = moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment.class.getDeclaredField("inputLocks");
        inputLocksField.setAccessible(true);
        inputLocksField.set(attachment, provider.inputLocks);

        var fullKnowledgeField = moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment.class.getDeclaredField("fullKnowledge");
        fullKnowledgeField.setAccessible(true);
        fullKnowledgeField.setBoolean(attachment, provider.hasFullKnowledge());

        var emcField = moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment.class.getDeclaredField("emc");
        emcField.setAccessible(true);
        emcField.set(attachment, provider.getEmc());

        return attachment;
    }

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag properties = new CompoundTag();
        properties.put("inputlock", inputLocks.serializeNBT(provider));
        return properties;
    }

    public CompoundTag serializeNBT() {
        return serializeNBT(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess());
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag properties) {
        inputLocks.deserializeNBT(provider, properties.getCompound("inputlock"));
    }

    public void deserializeNBT(CompoundTag properties) {
        deserializeNBT(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess(), properties);
    }

    @Override
    public void syncEmc(@NotNull ServerPlayer player) {
        sendPacket(new KnowledgeSyncEmcPKT(getEmc()), player);
    }

    @Override
    public void syncKnowledgeChange(@NotNull ServerPlayer player, ItemInfo change, boolean learned) {
        sendPacket(new KnowledgeSyncChangePKT(change, learned), player);
    }

    private static void sendPacket(IPEPacket packet, ServerPlayer player) {
        TeamProjectE.getAllOnlinePlayers()
                .forEach(p -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, packet));
    }

    @Override
    public void syncInputAndLocks(@NotNull ServerPlayer player, it.unimi.dsi.fastutil.ints.IntList slotsChanged, TargetUpdateType updateTargets) {
        if (!slotsChanged.isEmpty()) {
            int slots = inputLocks.getSlots();
            it.unimi.dsi.fastutil.ints.Int2ObjectMap<ItemStack> stacksToSync = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
            for (int slot : slotsChanged) {
                if (slot >= 0 && slot < slots) {
                    stacksToSync.put(slot, inputLocks.getStackInSlot(slot));
                }
            }
            if (!stacksToSync.isEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new KnowledgeSyncInputsAndLocksPKT(stacksToSync, updateTargets));
            }
        }
    }

    @Override
    public void receiveInputsAndLocks(it.unimi.dsi.fastutil.ints.Int2ObjectMap<ItemStack> changes) {
        int slots = inputLocks.getSlots();
        for (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<ItemStack> entry : changes.int2ObjectEntrySet()) {
            int slot = entry.getIntKey();
            if (slot >= 0 && slot < slots) {
                inputLocks.setStackInSlot(slot, entry.getValue());
            }
        }
    }

    private void markDirty() {
        TPSavedData data = TPSavedData.getData();
        if (data != null) {
            data.setDirty();
        }
    }
}
