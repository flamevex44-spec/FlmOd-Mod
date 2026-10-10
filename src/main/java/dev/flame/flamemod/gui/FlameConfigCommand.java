package dev.flame.flamemod.gui;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

/**
 * Registers the client-side {@code /flameconfig} command.
 * Invoking it opens {@link FlameConfigScreen}.
 */
public final class FlameConfigCommand {

    private FlameConfigCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("flameconfig")
                        .executes(ctx -> {
                            MinecraftClient.getInstance().send(() ->
                                    MinecraftClient.getInstance().setScreen(
                                            new FlameConfigScreen(null)
                                    )
                            );
                            return 1;
                        })
        );
    }
}
