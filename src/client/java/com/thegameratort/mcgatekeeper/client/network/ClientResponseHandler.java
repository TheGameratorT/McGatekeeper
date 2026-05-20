package com.thegameratort.mcgatekeeper.client.network;

import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import com.thegameratort.mcgatekeeper.network.ChallengePayload;
import com.thegameratort.mcgatekeeper.network.ResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientResponseHandler {

    public static void register(ClientKeyStore keyStore) {
        ClientPlayNetworking.registerGlobalReceiver(ChallengePayload.ID, (payload, context) -> {
            byte[] nonce = payload.nonce();
            byte[] signature = Ed25519Util.sign(nonce, keyStore.getPrivateKey());
            byte[] pubKeyBytes = keyStore.getPublicKey().getEncoded();
            context.responseSender().sendPacket(new ResponsePayload(pubKeyBytes, signature));
        });
    }
}
