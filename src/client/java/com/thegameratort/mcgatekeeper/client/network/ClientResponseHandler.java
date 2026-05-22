package com.thegameratort.mcgatekeeper.client.network;

import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.client.auth.ClientAuthState;
import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import com.thegameratort.mcgatekeeper.network.AwaitingAdminPayload;
import com.thegameratort.mcgatekeeper.network.ChallengePayload;
import com.thegameratort.mcgatekeeper.network.ResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;

public class ClientResponseHandler {

    public static void register(ClientKeyStore keyStore) {
        ClientConfigurationNetworking.registerGlobalReceiver(ChallengePayload.ID, (payload, context) -> {
            byte[] privKey = keyStore.getPrivateKey(payload.serverId());
            byte[] pubKey = keyStore.getPublicKey(payload.serverId());
            byte[] signature = Ed25519Util.sign(payload.nonce(), privKey);
            context.responseSender().sendPacket(new ResponsePayload(pubKey, signature));
        });

        ClientConfigurationNetworking.registerGlobalReceiver(AwaitingAdminPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientAuthState.setAwaitingAdmin(payload.timeoutSeconds()));
        });
    }
}
