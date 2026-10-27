package io.github.tianiste.skyprogression.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.1.x: the open screen and overlay live on Minecraft itself. */
final class Compat {
    private Compat() {}
    static Screen screen(Minecraft client) { return client.screen; }
    static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }
    static Overlay overlay(Minecraft client) { return client.getOverlay(); }
}
