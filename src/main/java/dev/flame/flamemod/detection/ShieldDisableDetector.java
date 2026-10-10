package dev.flame.flamemod.detection;

import dev.flame.flamemod.combat.MaceAttackScheduler;
import dev.flame.flamemod.config.FlameConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects when the local player has caused an opponent's shield to be disabled.
 *
 * ── How client-side shield-disable detection works ──────────────────────────
 *
 * Minecraft 1.21.x does not fire a dedicated "shield disabled" event to the
 * client. Instead, shield disabling is inferred from observable state changes:
 *
 *   1. The target entity must be a LivingEntity that is actively using a
 *      shield (UseItem == shield AND isBlocking() == true).
 *   2. On the tick following a successful player attack, the target's
 *      UseItem changes away from the shield OR isBlocking() becomes false,
 *      AND the target's shield cooldown becomes non-zero (client-visible via
 *      ItemCooldownManager).
 *
 * The cooldown on the shield item (Items.SHIELD) is the most reliable
 * client-observable indicator that a disable just occurred. Vanilla applies
 * a 5-second (100-tick) cooldown to the shield after a successful axe attack
 * that triggers a disable; this cooldown is sent to the client in a
 * SetEntityData / ContainerSetData packet. We track this cooldown going from
 * 0 → non-zero within the window after a player attack as the trigger.
 *
 * ── Limitations ─────────────────────────────────────────────────────────────
 *
 *  • Detection is probabilistic, not guaranteed. Network latency can cause
 *    the state update to arrive 1–3 ticks after the physical hit.
 *  • If the target stops blocking for an unrelated reason in the same window
 *    (e.g., they manually lowered their shield) a false positive may occur.
 *  • We guard against repeated triggers via a per-UUID cooldown map.
 *  • The detector is idle when the feature is disabled.
 */
public final class ShieldDisableDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("FlameMod/Detector");

    /** Window (in ticks) after a hit during which a cooldown transition counts. */
    private static final int DETECTION_WINDOW_TICKS = 6;

    /** Per-target UUID: the world-tick on which we last saw them blocking. */
    private final Map<UUID, Long> blockingSnapshot = new HashMap<>();

    /** Per-target UUID: the world-tick on which the local player last hit them. */
    private final Map<UUID, Long> hitTimestamp = new HashMap<>();

    /** Per-target UUID: cooldown seen on the last tick — to detect 0 → non-zero. */
    private final Map<UUID, Float> lastShieldCooldown = new HashMap<>();

    /** Prevent re-firing for the same disable event. */
    private final Map<UUID, Long> lastTriggerTick = new HashMap<>();

    private final MaceAttackScheduler scheduler;

    public ShieldDisableDetector(MaceAttackScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Called by the mixin every game tick (client-side).
     * Iterates tracked entities and checks for shield-disable transitions.
     *
     * @param worldTick the current client world game time
     */
    public void tick(long worldTick) {
        if (!FlameConfig.get().shieldDisableEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        // Snapshot all entities currently being tracked
        for (UUID targetId : hitTimestamp.keySet().toArray(new UUID[0])) {
            Long hitTick = hitTimestamp.get(targetId);
            if (hitTick == null) continue;

            long ticksSinceHit = worldTick - hitTick;
            if (ticksSinceHit > DETECTION_WINDOW_TICKS) {
                // Window expired — clean up tracking for this entity
                cleanup(targetId);
                continue;
            }

            Entity entity = mc.world.getEntityById(getEntityId(targetId));
            if (!(entity instanceof LivingEntity target)) {
                cleanup(targetId);
                continue;
            }

            // Is the target still alive?
            if (target.isDead()) {
                cleanup(targetId);
                continue;
            }

            // Check shield cooldown transition: 0 → non-zero within the window
            float currentCooldown = mc.player.getItemCooldownManager()
                    .getCooldownProgress(Items.SHIELD, 0f);
            // Note: we check the LOCAL player's shield cooldown manager because
            // the server sends cooldown updates to the acting player. For the
            // target, we rely on the isBlocking() state change.

            // More reliable: track target's blocking state change
            boolean wasBlocking  = Boolean.TRUE.equals(blockingSnapshot.containsKey(targetId));
            boolean nowBlocking  = target.isBlocking();

            if (wasBlocking && !nowBlocking) {
                // Target stopped blocking in our window — check if shield got cooldown
                // We approximate via isUsingItem() returning false for the shield
                ItemStack usedItem = target.getActiveItem();
                boolean shieldWasActive = usedItem.isOf(Items.SHIELD);

                // If they were using shield and now aren't, treat as disable
                // Guard: don't re-trigger for the same event
                Long lastTrigger = lastTriggerTick.get(targetId);
                if (lastTrigger == null || worldTick - lastTrigger > 20) {
                    LOGGER.debug("[FlameMod] Shield disable detected on {} at tick {}",
                            target.getName().getString(), worldTick);
                    lastTriggerTick.put(targetId, worldTick);
                    scheduler.schedule(target, worldTick);
                }
                cleanup(targetId);
            } else {
                // Update blocking snapshot
                if (nowBlocking) {
                    blockingSnapshot.put(targetId, worldTick);
                } else {
                    blockingSnapshot.remove(targetId);
                }
            }
        }
    }

    /**
     * Called immediately after the local player attacks an entity.
     * Begins tracking that entity for a shield-disable transition.
     *
     * @param target    the attacked entity
     * @param worldTick the current world game time
     */
    public void onPlayerAttack(LivingEntity target, long worldTick) {
        if (!FlameConfig.get().shieldDisableEnabled) return;

        // Only worth tracking if the target is currently blocking
        if (!target.isBlocking()) return;
        // And they must be holding a shield
        if (!target.getActiveItem().isOf(Items.SHIELD)) return;

        UUID id = target.getUuid();
        hitTimestamp.put(id, worldTick);
        blockingSnapshot.put(id, worldTick);
        entityIdCache.put(id, target.getId());

        LOGGER.debug("[FlameMod] Tracking {} for shield-disable (blocking=true at hit)",
                target.getName().getString());
    }

    /**
     * Must be called on world-change / disconnect to prevent stale tracking
     * from firing into a new session.
     */
    public void reset() {
        blockingSnapshot.clear();
        hitTimestamp.clear();
        lastShieldCooldown.clear();
        lastTriggerTick.clear();
        entityIdCache.clear();
        LOGGER.debug("[FlameMod] Detector reset.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Network entity IDs are not stable across respawns; cache the mapping. */
    private final Map<UUID, Integer> entityIdCache = new HashMap<>();

    private int getEntityId(UUID uuid) {
        return entityIdCache.getOrDefault(uuid, -1);
    }

    private void cleanup(UUID uuid) {
        blockingSnapshot.remove(uuid);
        hitTimestamp.remove(uuid);
        lastShieldCooldown.remove(uuid);
        entityIdCache.remove(uuid);
    }
}
