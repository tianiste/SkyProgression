package io.github.tianiste.skyprogression.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.2: the open screen and overlay moved to Minecraft.gui. */
final class Compat {
    private Compat() {}
    static Screen screen(Minecraft client) { return client.gui.screen(); }
    static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    static Overlay overlay(Minecraft client) { return client.gui.overlay(); }
}
