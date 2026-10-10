package dev.flame.flamemod.combat;

import dev.flame.flamemod.config.FlameConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Schedules a single delayed mace attack after a detected shield-disable.
 *
 * The attack fires exactly once per scheduled event, using game-tick counting
 * rather than wall-clock timers. All scheduling state is discarded on world
 * change / disconnect via {@link #reset()}.
 *
 * ── Attack validity checks ──────────────────────────────────────────────────
 * Immediately before executing the attack the scheduler verifies:
 *   • The target entity still exists in the world.
 *   • The target is not dead.
 *   • The player is alive and not in a menu.
 *   • The configured mace slot contains a mace (Items.MACE).
 *   • The player's attack cooldown is ready (getCooldownProgress >= 0.9).
 *
 * If any check fails the attack is silently cancelled.
 *
 * ── Limitations ─────────────────────────────────────────────────────────────
 *   • Network round-trip time means the server may not yet see the same game
 *     state the client does at the moment the attack packet is sent.
 *   • The "attack" is implemented via the standard Minecraft client attack
 *     interaction; it is not a guarantee of damage.
 */
public final class MaceAttackScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("FlameMod/Scheduler");

    /** How close (in blocks) the target must be for the attack to proceed. */
    private static final double MAX_ATTACK_REACH_SQ = 6.0 * 6.0; // 6-block generous reach

    /** A pending attack, or null if nothing is scheduled. */
    private PendingAttack pending = null;

    private record PendingAttack(UUID targetUuid, int targetNetId, long fireAtTick) {}

    /**
     * Schedule a mace attack for the configured delay after the current tick.
     * Replaces any previously pending attack (only one can be in-flight).
     */
    public void schedule(LivingEntity target, long currentTick) {
        int delay = FlameConfig.get().attackDelayTicks; // already validated 3–6
        long fireTick = currentTick + delay;
        pending = new PendingAttack(target.getUuid(), target.getId(), fireTick);
        LOGGER.debug("[FlameMod] Attack scheduled: target={} fireAt={}", target.getName().getString(), fireTick);
    }

    /**
     * Called every game tick. Fires the attack when the scheduled tick arrives.
     *
     * @param currentTick the current client world game time
     */
    public void tick(long currentTick) {
        if (pending == null) return;
        if (currentTick < pending.fireAtTick()) return;

        PendingAttack p = pending;
        pending = null; // consume — prevents double-fire

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        // ── Revalidate target ────────────────────────────────────────────────
        var targetEntity = mc.world.getEntityById(p.targetNetId());
        if (!(targetEntity instanceof LivingEntity target)) {
            LOGGER.debug("[FlameMod] Attack cancelled — target entity gone.");
            return;
        }
        if (!target.getUuid().equals(p.targetUuid())) {
            // Net-ID was recycled to a different entity (edge case on busy servers)
            LOGGER.debug("[FlameMod] Attack cancelled — entity ID reused.");
            return;
        }
        if (target.isDead() || target.getHealth() <= 0f) {
            LOGGER.debug("[FlameMod] Attack cancelled — target is dead.");
            return;
        }

        // ── Revalidate player ────────────────────────────────────────────────
        ClientPlayerEntity player = mc.player;
        if (player.isDead() || player.getHealth() <= 0f) {
            LOGGER.debug("[FlameMod] Attack cancelled — player is dead.");
            return;
        }
        if (mc.currentScreen != null) {
            // Player has a screen open (inventory, etc.) — don't attack
            LOGGER.debug("[FlameMod] Attack cancelled — screen open.");
            return;
        }

        // ── Reach check ─────────────────────────────────────────────────────
        double distSq = player.squaredDistanceTo(target);
        if (distSq > MAX_ATTACK_REACH_SQ) {
            LOGGER.debug("[FlameMod] Attack cancelled — target out of reach ({} blocks).",
                    String.format("%.1f", Math.sqrt(distSq)));
            return;
        }

        // ── Cooldown check ───────────────────────────────────────────────────
        float cooldownProgress = player.getAttackCooldownProgress(0f);
        if (cooldownProgress < 0.9f) {
            LOGGER.debug("[FlameMod] Attack cancelled — cooldown not ready ({}).",
                    String.format("%.2f", cooldownProgress));
            return;
        }

        // ── Mace slot management ─────────────────────────────────────────────
        FlameConfig cfg = FlameConfig.get();
        if (cfg.autoSwitchEnabled) {
            int maceSlotIndex = cfg.maceSlot - 1; // convert 1-indexed → 0-indexed
            ItemStack slotStack = player.getInventory().getStack(maceSlotIndex);
            if (!slotStack.isOf(Items.MACE)) {
                LOGGER.warn("[FlameMod] Attack cancelled — slot {} does not contain a mace (found: {}).",
                        cfg.maceSlot, slotStack.getItem());
                return;
            }
            // Switch only if not already on that slot
            if (player.getInventory().selectedSlot != maceSlotIndex) {
                player.getInventory().selectedSlot = maceSlotIndex;
                // Notify server of the slot change
                mc.getNetworkHandler().sendPacket(
                        new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(maceSlotIndex)
                );
                LOGGER.debug("[FlameMod] Switched to mace slot {}.", cfg.maceSlot);
            }
        }

        // ── Execute attack ────────────────────────────────────────────────────
        // mc.interactionManager.attackEntity mirrors exactly what happens when
        // the player left-clicks on a living entity in-game.
        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        LOGGER.debug("[FlameMod] Mace attack executed on {}.", target.getName().getString());
    }

    /**
     * Cancel any pending attack and clear all state.
     * Must be called on world-change, disconnect, and respawn.
     */
    public void reset() {
        pending = null;
        LOGGER.debug("[FlameMod] Scheduler reset.");
    }

    /** True if an attack is currently waiting to fire. */
    public boolean hasPending() {
        return pending != null;
    }
}
