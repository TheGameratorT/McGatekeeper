package com.thegameratort.mcgatekeeper.client;

import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import com.thegameratort.mcgatekeeper.client.network.ClientResponseHandler;
import com.thegameratort.mcgatekeeper.network.ChallengePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class McgatekeeperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientKeyStore keyStore = new ClientKeyStore();
        keyStore.load();

        // Register the S2C challenge payload type (client side mirrors server registration)
        // PayloadTypeRegistry.playS2C() is already registered on the server side;
        // the client only needs to register its own C2S payload types here.
        // The S2C type is shared and already registered in Mcgatekeeper.onInitialize().

        ClientResponseHandler.register(keyStore);
    }
}
