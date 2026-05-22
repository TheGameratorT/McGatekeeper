package com.thegameratort.mcgatekeeper.client;

import com.thegameratort.mcgatekeeper.client.auth.ClientAuthState;
import com.thegameratort.mcgatekeeper.client.auth.ClientKeyStore;
import com.thegameratort.mcgatekeeper.client.network.ClientResponseHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class McgatekeeperClient implements ClientModInitializer {

    public static ClientKeyStore KEY_STORE;

    @Override
    public void onInitializeClient() {
        KEY_STORE = new ClientKeyStore();
        KEY_STORE.load();

        ClientResponseHandler.register(KEY_STORE);

        // Clear auth state when configuration phase ends (player enters play)
        ClientPlayConnectionEvents.INIT.register((handler, client) -> ClientAuthState.clear());

        // Clear auth state if the player disconnects during configuration
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> ClientAuthState.clear());
    }
}
