package dev.hex.teamprojecte;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

public class TPSavedData extends SavedData {

    private static TPSavedData DATA;

    static TPSavedData getData() {
        if (DATA == null && ServerLifecycleHooks.getCurrentServer() != null) {
            DATA = ServerLifecycleHooks.getCurrentServer().overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(TPSavedData::new, TPSavedData::new), "teamprojecte");
        }
        return DATA;
    }

    static void onServerStopped() {
        DATA = null;
    }

    final GlobalKnowledgeData globalData = new GlobalKnowledgeData();

    TPSavedData() {
    }

    TPSavedData(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        if (tag.contains("globalData")) {
            globalData.deserialize(tag.getCompound("globalData"));
        }
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        tag.put("globalData", globalData.serialize());
        return tag;
    }
}
