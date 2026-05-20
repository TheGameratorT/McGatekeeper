package com.thegameratort.mcgatekeeper.client;

import com.thegameratort.mcgatekeeper.client.auth.ClientAuthState;
import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import com.thegameratort.mcgatekeeper.client.network.ClientResponseHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class McgatekeeperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientKeyStore keyStore = new ClientKeyStore();
        keyStore.load();

        ClientResponseHandler.register(keyStore);

        // Clear auth state on disconnect so it doesn't bleed into the next session
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientAuthState.clear());
    }
}
