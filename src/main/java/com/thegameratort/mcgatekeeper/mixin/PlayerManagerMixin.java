package com.thegameratort.mcgatekeeper.mixin;

import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    private void gate_captureConnecting(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        LimboManager.setCurrentlyConnecting(player);
    }

    @Inject(method = "onPlayerConnect", at = @At("RETURN"))
    private void gate_clearConnecting(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        LimboManager.clearCurrentlyConnecting();
    }

    @Redirect(
        method = "onPlayerConnect",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V")
    )
    private void gate_suppressJoinMessage(PlayerManager instance, Text message, boolean overlay) {
        ServerPlayerEntity connecting = LimboManager.getCurrentlyConnecting();
        if (connecting == null || !LimboManager.isInLimbo(connecting.getUuid())) {
            instance.broadcast(message, overlay);
        }
    }
}
