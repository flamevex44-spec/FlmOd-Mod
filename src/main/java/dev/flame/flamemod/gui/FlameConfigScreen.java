package dev.flame.flamemod.gui;

import dev.flame.flamemod.config.FlameConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * In-game configuration screen for FlameMod.
 *
 * Accessible from the Mod Menu (if installed) or via the /flameconfig command.
 * Uses only vanilla Minecraft widget types — no external UI library required.
 *
 * Layout (top-to-bottom):
 *   Title
 *   [Toggle] Shield-disable follow-up: ON / OFF
 *   [Toggle] Auto mace switching: ON / OFF
 *   [Slider] Mace slot: 1–9
 *   [Slider] Attack delay: 3–6 ticks
 *   [Button] Done
 */
public final class FlameConfigScreen extends Screen {

    private final Screen parent;
    private final FlameConfig cfg;

    // Mutable copies updated while the screen is open; committed on Done.
    private boolean shieldEnabled;
    private boolean switchEnabled;
    private int maceSlot;
    private int delayTicks;

    public FlameConfigScreen(Screen parent) {
        super(Text.literal("FlameMod Configuration"));
        this.parent   = parent;
        this.cfg      = FlameConfig.get();
        this.shieldEnabled = cfg.shieldDisableEnabled;
        this.switchEnabled = cfg.autoSwitchEnabled;
        this.maceSlot  = cfg.maceSlot;
        this.delayTicks = cfg.attackDelayTicks;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int startY  = height / 4;
        int spacing = 28;
        int btnW    = 250;

        // ── Shield-disable toggle ────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                shieldButtonText(),
                btn -> {
                    shieldEnabled = !shieldEnabled;
                    btn.setMessage(shieldButtonText());
                }
        ).dimensions(centerX - btnW / 2, startY, btnW, 20).build());

        // ── Auto-switch toggle ───────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                switchButtonText(),
                btn -> {
                    switchEnabled = !switchEnabled;
                    btn.setMessage(switchButtonText());
                }
        ).dimensions(centerX - btnW / 2, startY + spacing, btnW, 20).build());

        // ── Mace slot slider (1–9) ───────────────────────────────────────────
        addDrawableChild(new SliderWidget(
                centerX - btnW / 2, startY + spacing * 2, btnW, 20,
                maceSlotText(), (maceSlot - 1) / 8.0
        ) {
            @Override
            protected void updateMessage() {
                setMessage(maceSlotText());
            }

            @Override
            protected void applyValue() {
                // value is [0, 1]; map to [1, 9]
                FlameConfigScreen.this.maceSlot = (int) Math.round(value * 8) + 1;
            }
        });

        // ── Delay slider (3–6 ticks) ─────────────────────────────────────────
        addDrawableChild(new SliderWidget(
                centerX - btnW / 2, startY + spacing * 3, btnW, 20,
                delayText(), (delayTicks - 3) / 3.0
        ) {
            @Override
            protected void updateMessage() {
                setMessage(delayText());
            }

            @Override
            protected void applyValue() {
                // value is [0, 1]; map to [3, 6]
                FlameConfigScreen.this.delayTicks = (int) Math.round(value * 3) + 3;
            }
        });

        // ── Done button ───────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                btn -> commitAndClose()
        ).dimensions(centerX - 100, startY + spacing * 5, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 4 - 20, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        commitAndClose();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void commitAndClose() {
        cfg.shieldDisableEnabled = shieldEnabled;
        cfg.autoSwitchEnabled    = switchEnabled;
        cfg.maceSlot             = maceSlot;
        cfg.attackDelayTicks     = delayTicks;
        cfg.save();
        assert client != null;
        client.setScreen(parent);
    }

    private Text shieldButtonText() {
        return Text.literal("Shield-Disable Follow-up: " + (shieldEnabled ? "§aON" : "§cOFF"));
    }

    private Text switchButtonText() {
        return Text.literal("Auto Mace Switch: " + (switchEnabled ? "§aON" : "§cOFF"));
    }

    private Text maceSlotText() {
        return Text.literal("Mace Slot: §e" + maceSlot);
    }

    private Text delayText() {
        return Text.literal("Attack Delay: §e" + delayTicks + " ticks");
    }
}
