package dev.flame.flamemod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent configuration for FlameMod.
 *
 * Stored as JSON in <config-dir>/flamemod.json.
 * All public fields are the live settings; call {@link #save()} after
 * mutating any of them.
 */
public final class FlameConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("FlameMod/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "flamemod.json";

    // ── Settings ─────────────────────────────────────────────────────────────

    /** Master toggle: whether to react to a detected shield-disable at all. */
    public boolean shieldDisableEnabled = true;

    /** Whether to automatically switch to the mace slot on shield-disable. */
    public boolean autoSwitchEnabled = true;

    /**
     * Hotbar slot that should contain the mace (1-indexed, 1–9).
     * Stored 1-indexed for human readability; convert to 0-indexed at use.
     */
    public int maceSlot = 5;

    /**
     * Ticks to wait after shield-disable detection before attacking.
     * Valid range: 3–6 (inclusive).
     */
    public int attackDelayTicks = 4;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static FlameConfig instance;

    private FlameConfig() {}

    public static FlameConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Clamps all values to their legal ranges. Called after deserialization
     * and before any save to prevent storing illegal values.
     */
    public void validate() {
        maceSlot        = Math.max(1, Math.min(9, maceSlot));
        attackDelayTicks = Math.max(3, Math.min(6, attackDelayTicks));
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static FlameConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                FlameConfig cfg = GSON.fromJson(r, FlameConfig.class);
                if (cfg == null) cfg = new FlameConfig();
                cfg.validate();
                return cfg;
            } catch (IOException e) {
                LOGGER.error("[FlameMod] Failed to read config — using defaults", e);
            }
        }
        FlameConfig fresh = new FlameConfig();
        fresh.save(); // write defaults on first run
        return fresh;
    }

    public void save() {
        validate();
        try (Writer w = Files.newBufferedWriter(configPath())) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            LOGGER.error("[FlameMod] Failed to save config", e);
        }
    }
}
