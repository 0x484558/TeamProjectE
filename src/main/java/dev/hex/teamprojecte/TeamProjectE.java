package dev.hex.teamprojecte;

import com.mojang.logging.LogUtils;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.gameObjs.registries.PEAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.List;

@Mod("teamprojecte")
public class TeamProjectE {
    public static final Logger LOGGER = LogUtils.getLogger();

    public TeamProjectE(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        TPSavedData.onServerStopped();
    }

    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().dimension() == Level.OVERWORLD && event.getEntity() instanceof ServerPlayer player)
            sync(player);
    }

    public static void sync(ServerPlayer player) {
        var cap = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if (cap != null)
            cap.sync(player);
    }

    public static List<ServerPlayer> getAllOnlinePlayers() {
        return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers();
    }
}
