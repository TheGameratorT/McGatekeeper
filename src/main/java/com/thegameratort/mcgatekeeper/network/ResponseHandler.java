package com.thegameratort.mcgatekeeper.network;

import com.mojang.authlib.GameProfile;
import com.thegameratort.mcgatekeeper.Mcgatekeeper;
import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.auth.KeyStore;
import com.thegameratort.mcgatekeeper.auth.PendingAuthManager;
import com.thegameratort.mcgatekeeper.auth.ServerIdentity;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.mixin.ServerConfigurationNetworkHandlerAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

public class ResponseHandler {

    public static void register() {
        ServerConfigurationNetworking.registerGlobalReceiver(ResponsePayload.ID, ResponseHandler::handle);
    }

    private static void handle(ResponsePayload payload, ServerConfigurationNetworking.Context context) {
        GameProfile profile = ((ServerConfigurationNetworkHandlerAccessor) context.networkHandler()).mcgatekeeper_getProfile();
        UUID uuid = profile.id();

        if (!PendingAuthManager.isPending(uuid)) return;
        if (PendingAuthManager.getPendingPublicKey(uuid) != null) return;

        byte[] nonce = PendingAuthManager.getNonce(uuid);
        if (nonce == null) return;

        String submittedKeyB64 = Ed25519Util.encodeKey(payload.publicKey());
        PendingAuthManager.setPendingPublicKey(uuid, submittedKeyB64);

        byte[] message = Ed25519Util.buildSignedMessage(ServerIdentity.getPublicKey(), nonce);
        List<KeyStore.KeyEntry> storedKeys = Mcgatekeeper.KEY_STORE.getKeys(uuid);
        boolean authenticated = false;
        for (KeyStore.KeyEntry entry : storedKeys) {
            if (entry.publicKey().equals(submittedKeyB64)) {
                authenticated = Ed25519Util.verify(message, payload.signature(), payload.publicKey());
                if (authenticated) break;
            }
        }

        if (authenticated) {
            context.server().getPlayerManager().disconnectDuplicateLogins(uuid);
            PendingAuthManager.complete(uuid);
            Mcgatekeeper.LOGGER.info("[McGatekeeper] {} authenticated.", profile.name());
        } else {
            if (context.server().getPlayerManager().getPlayer(uuid) != null) {
                context.networkHandler().disconnect(Text.literal("An authenticated session for your account is already active"));
                return;
            }
            ServerConfigurationNetworking.send(context.networkHandler(), new AwaitingAdminPayload(GateConfig.INSTANCE.limboTimeoutSeconds));
            Mcgatekeeper.LOGGER.info("[McGatekeeper] {} connected with an unregistered key; an admin can run /gate allow.", profile.name());
        }
    }
}
