package dev.hex.teamprojecte.mixin;

import dev.hex.teamprojecte.GlobalKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "moze_intel.projecte.PECore", remap = false)
public class PECoreCapabilityMixin {

    @Inject(method = "registerCapabilities", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRegisterCapabilities(RegisterCapabilitiesEvent event, CallbackInfo ci) {
        ci.cancel();

        event.registerEntity(PECapabilities.KNOWLEDGE_CAPABILITY, EntityType.PLAYER, (player, context) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                return new GlobalKnowledgeProvider(serverPlayer);
            }
            return new moze_intel.projecte.impl.capability.KnowledgeImpl(player);
        });

        event.registerEntity(
            moze_intel.projecte.api.capabilities.PECapabilities.ALCH_BAG_CAPABILITY,
            EntityType.PLAYER,
            (player, context) -> new moze_intel.projecte.impl.capability.AlchBagImpl(player)
        );
    }
}
