package dev.flame.flamemod.mixin;

import dev.flame.flamemod.FlameMod;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@code ClientPlayerInteractionManager#attackEntity} to notify
 * the shield-disable detector immediately after the client sends an attack
 * packet for a LivingEntity that is currently blocking.
 *
 * We inject BEFORE the vanilla call so we capture the target's blocking
 * state prior to any local state change. The detector then watches for the
 * subsequent state transition over the next few ticks.
 *
 * Why BEFORE and not AFTER? Vanilla's {@code attackEntity} on the client
 * triggers the swing animation and sends the attack packet but does not
 * immediately mutate the target's use-item state (that comes via S2C packets
 * from the server). The target's {@code isBlocking()} is therefore still
 * accurate at inject time.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(
            method = "attackEntity",
            at = @At("HEAD"),
            cancellable = false
    )
    private void flamemod$onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        // Only process local-player attacks on LivingEntities
        if (!(target instanceof LivingEntity living)) return;

        var detector  = FlameMod.getDetector();
        var scheduler = FlameMod.getScheduler();
        if (detector == null || scheduler == null) return;

        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.world == null) return;

        long currentTick = mc.world.getTime();
        detector.onPlayerAttack(living, currentTick);
    }
}
