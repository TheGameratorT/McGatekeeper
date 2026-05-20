package com.thegameratort.mcgatekeeper.client.network;

import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import com.thegameratort.mcgatekeeper.client.screen.AuthorizationScreen;
import com.thegameratort.mcgatekeeper.network.AuthResultPayload;
import com.thegameratort.mcgatekeeper.network.ChallengePayload;
import com.thegameratort.mcgatekeeper.network.ResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;

public class ClientResponseHandler {

    public static void register(ClientKeyStore keyStore) {
        ClientPlayNetworking.registerGlobalReceiver(ChallengePayload.ID, (payload, context) -> {
            byte[] nonce = payload.nonce();
            byte[] signature = Ed25519Util.sign(nonce, keyStore.getPrivateKey());
            byte[] pubKeyBytes = keyStore.getPublicKey().getEncoded();
            context.responseSender().sendPacket(new ResponsePayload(pubKeyBytes, signature));

            // Show the pending-authorization screen over the terrain loading screen
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof LevelLoadingScreen loadingScreen) {
                    context.client().setScreen(new AuthorizationScreen(loadingScreen, payload.timeoutSeconds()));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AuthResultPayload.ID, (payload, context) -> {
            // Auth succeeded — restore the terrain loading screen so Minecraft can finish loading
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof AuthorizationScreen authScreen) {
                    context.client().setScreen(authScreen.getLoadingScreen());
                }
            });
        });
    }
}
