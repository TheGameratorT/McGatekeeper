package com.thegameratort.mcgatekeeper.mixin.client;

import com.thegameratort.mcgatekeeper.client.auth.ClientAuthState;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {

    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"),
        index = 1
    )
    private Text gate_awaitingAdminText(Text original) {
        if (!ClientAuthState.isAwaitingAdmin()) return original;
        long elapsed = (System.currentTimeMillis() - ClientAuthState.getStartMs()) / 1000L;
        int remaining = Math.max(0, ClientAuthState.getTimeoutSeconds() - (int) elapsed);
        return Text.literal("Waiting for admin authorization... (" + remaining + "s)");
    }
}
