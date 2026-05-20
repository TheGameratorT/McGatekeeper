package com.thegameratort.mcgatekeeper.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

public class AuthorizationScreen extends Screen {

    private final LevelLoadingScreen loadingScreen;
    private final int timeoutSeconds;
    private int ticksElapsed;

    public AuthorizationScreen(LevelLoadingScreen loadingScreen, int timeoutSeconds) {
        super(Text.literal("Pending Authorization"));
        this.loadingScreen = loadingScreen;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        ticksElapsed++;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF000000);

        int secondsRemaining = Math.max(0, timeoutSeconds - ticksElapsed / 20);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Pending Authorization"),
                width / 2, height / 2 - 16, Colors.WHITE);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Authenticating with server... (" + secondsRemaining + "s)"),
                width / 2, height / 2 + 2, 0xAAAAAA);
    }

    @Override
    protected void addElementNarrations(NarrationMessageBuilder builder) {}

    public LevelLoadingScreen getLoadingScreen() {
        return loadingScreen;
    }
}
