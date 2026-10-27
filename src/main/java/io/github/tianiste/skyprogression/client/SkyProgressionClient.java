package io.github.tianiste.skyprogression.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class SkyProgressionClient implements ClientModInitializer {
    private boolean open;
    private boolean previewed;
    @Override public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            for (String command : new String[]{"skyprogression", "sp"}) {
                dispatcher.register(literal(command).executes(context -> { open = true; return 1; }));
            }
        });
        // Open on the following tick: the chat screen closes after command execution.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!previewed && Boolean.getBoolean("skyprogression.preview")
                    && Compat.screen(client) instanceof net.minecraft.client.gui.screens.TitleScreen && Compat.overlay(client) == null) {
                ClientData.INSTANCE.initialize(client::execute, () -> {});
                if (ClientData.INSTANCE.ready) { previewed = true; ClientData.INSTANCE.demo(); open = true; }
            }
            if (open) { open = false; Compat.setScreen(client, new ProgressionScreen()); }
        });
    }
}
