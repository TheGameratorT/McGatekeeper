package com.thegameratort.mcgatekeeper.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin {

    // Vanilla calls disconnectDuplicateLogins at login time, before we know whether
    // the new connection will authenticate. Suppress it here; ResponseHandler handles
    // the kick once the auth outcome is known.
    @Redirect(method = "tickVerify", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/PlayerManager;disconnectDuplicateLogins(Ljava/util/UUID;)Z"))
    private boolean gate_deferDuplicateDisconnect(PlayerManager manager, UUID uuid) {
        return false;
    }
}
