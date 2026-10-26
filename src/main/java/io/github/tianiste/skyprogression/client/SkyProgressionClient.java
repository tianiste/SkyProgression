package io.github.tianiste.skyprogression.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class SkyProgressionClient implements ClientModInitializer {
    private boolean open;
    @Override public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            for (String command : new String[]{"skyprogression", "sp"}) {
                dispatcher.register(literal(command).executes(context -> { open = true; return 1; }));
            }
        });
        // Open on the following tick: the chat screen closes after command execution.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (open) { open = false; client.setScreen(new ProgressionScreen()); }
        });
    }
}
