package dev.flame.flamemod;

import dev.flame.flamemod.combat.MaceAttackScheduler;
import dev.flame.flamemod.config.FlameConfig;
import dev.flame.flamemod.detection.ShieldDisableDetector;
import dev.flame.flamemod.gui.FlameConfigCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FlameMod client initializer.
 *
 * Wires together the config, detector, and scheduler; registers tick and
 * lifecycle events. Attack-triggering from player hit events is handled via
 * the mixin ({@link dev.flame.flamemod.mixin.ClientPlayerInteractionManagerMixin}).
 */
public final class FlameMod implements ClientModInitializer {

    public static final String MOD_ID = "flamemod";
    public static final Logger LOGGER  = LoggerFactory.getLogger("FlameMod");

    // Singletons accessible by the mixin
    private static MaceAttackScheduler  scheduler;
    private static ShieldDisableDetector detector;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[FlameMod] Initialising — full rise.");

        // Eagerly load config so validation runs at startup
        FlameConfig.get();

        scheduler = new MaceAttackScheduler();
        detector  = new ShieldDisableDetector(scheduler);

        // ── Tick loop ────────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            long tick = client.world.getTime();
            detector.tick(tick);
            scheduler.tick(tick);
        });

        // ── World leave / disconnect — reset all state ────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.info("[FlameMod] Disconnected — resetting state.");
            detector.reset();
            scheduler.reset();
        });

        // ── Client command: /flameconfig ──────────────────────────────────────
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> FlameConfigCommand.register(dispatcher)
        );

        LOGGER.info("[FlameMod] Ready.");
    }

    public static MaceAttackScheduler getScheduler()  { return scheduler; }
    public static ShieldDisableDetector getDetector() { return detector;  }
}
