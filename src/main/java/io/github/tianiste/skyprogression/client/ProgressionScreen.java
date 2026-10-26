package io.github.tianiste.skyprogression.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProgressionScreen extends Screen {
    public ProgressionScreen() { super(Component.literal("SkyProgression")); }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.text(font, "SkyProgression", 20, 20, 0xFFFFCE66, true);
        g.text(font, "Mining progression", 20, 40, 0xFFFFFFFF, false);
    }
    @Override public boolean isPauseScreen() { return false; }
}
