package com.thegameratort.mcgatekeeper.client.network;

import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.client.auth.ClientAuthState;
import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import com.thegameratort.mcgatekeeper.network.AwaitingAdminPayload;
import com.thegameratort.mcgatekeeper.network.ChallengePayload;
import com.thegameratort.mcgatekeeper.network.ResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientResponseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcgatekeeper");

    public static void register(ClientKeyStore keyStore) {
        ClientConfigurationNetworking.registerGlobalReceiver(ChallengePayload.ID, (payload, context) -> {
            byte[] message = Ed25519Util.buildSignedMessage(payload.serverPublicKey(), payload.nonce());

            if (!Ed25519Util.verify(message, payload.serverSignature(), payload.serverPublicKey())) {
                LOGGER.warn("[McGatekeeper] Server signature invalid — possible relay attack. Disconnecting.");
                context.client().execute(() -> context.client().disconnect(
                    Text.literal("Server signature invalid.")
                ));
                return;
            }

            byte[] clientPrivKey = keyStore.getPrivateKey(payload.serverPublicKey());
            byte[] clientPubKey = keyStore.getPublicKey(payload.serverPublicKey());
            byte[] clientSignature = Ed25519Util.sign(message, clientPrivKey);
            context.responseSender().sendPacket(new ResponsePayload(clientPubKey, clientSignature));
        });

        ClientConfigurationNetworking.registerGlobalReceiver(AwaitingAdminPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientAuthState.setAwaitingAdmin(payload.timeoutSeconds()));
        });
    }
}
