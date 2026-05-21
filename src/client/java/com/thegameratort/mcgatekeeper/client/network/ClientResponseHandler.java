package com.thegameratort.mcgatekeeper.client.network;

import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.client.auth.ClientAuthState;
import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import com.thegameratort.mcgatekeeper.network.AuthResultPayload;
import com.thegameratort.mcgatekeeper.network.AwaitingAdminPayload;
import com.thegameratort.mcgatekeeper.network.ChallengePayload;
import com.thegameratort.mcgatekeeper.network.ResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientResponseHandler {

    public static void register(ClientKeyStore keyStore) {
        ClientPlayNetworking.registerGlobalReceiver(ChallengePayload.ID, (payload, context) -> {
            String serverId = payload.serverId();
            byte[] privKey = keyStore.getPrivateKey(serverId);
            byte[] pubKey = keyStore.getPublicKey(serverId);
            byte[] signature = Ed25519Util.sign(payload.nonce(), privKey);
            context.responseSender().sendPacket(new ResponsePayload(pubKey, signature));
            // No UI change for normal auth — if the key is known, terrain loads normally.
        });

        ClientPlayNetworking.registerGlobalReceiver(AwaitingAdminPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientAuthState.setAwaitingAdmin(payload.timeoutSeconds()));
        });

        ClientPlayNetworking.registerGlobalReceiver(AuthResultPayload.ID, (payload, context) -> {
            context.client().execute(ClientAuthState::clear);
        });
    }
}
