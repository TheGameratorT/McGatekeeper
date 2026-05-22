package com.thegameratort.mcgatekeeper.mixin;

import com.mojang.authlib.GameProfile;
import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.network.ServerLoginNetworkHandler;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin {

    @Shadow public abstract void disconnect(Text reason);

    @Inject(method = "tickVerify", at = @At("HEAD"), cancellable = true)
    private void gate_rejectDuplicateLimboLogin(GameProfile profile, CallbackInfo ci) {
        if (LimboManager.isInLimbo(profile.id())) {
            disconnect(Text.literal("You are already pending authorization on this server."));
            ci.cancel();
        }
    }
}
