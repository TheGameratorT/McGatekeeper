package com.thegameratort.mcgatekeeper;

import com.thegameratort.mcgatekeeper.auth.KeyStore;
import com.thegameratort.mcgatekeeper.auth.PendingAuthManager;
import com.thegameratort.mcgatekeeper.auth.ServerIdentity;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.command.GateCommand;
import com.thegameratort.mcgatekeeper.mixin.ServerConfigurationNetworkHandlerAccessor;
import com.thegameratort.mcgatekeeper.network.AwaitingAdminPayload;
import com.thegameratort.mcgatekeeper.network.ChallengePayload;
import com.thegameratort.mcgatekeeper.network.GateConfigurationTask;
import com.thegameratort.mcgatekeeper.network.ResponseHandler;
import com.thegameratort.mcgatekeeper.network.ResponsePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Mcgatekeeper implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("mcgatekeeper");
    public static final KeyStore KEY_STORE = new KeyStore();

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("mcgatekeeper");
        GateConfig.load(configDir);
        KEY_STORE.load(configDir);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> ServerIdentity.load(configDir));

        // Register custom payload types for the configuration phase
        PayloadTypeRegistry.configurationS2C().register(ChallengePayload.ID, ChallengePayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(AwaitingAdminPayload.ID, AwaitingAdminPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(ResponsePayload.ID, ResponsePayload.CODEC);

        // Add the auth task when a player enters the configuration phase
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!ServerConfigurationNetworking.canSend(handler, ChallengePayload.ID)) {
                handler.disconnect(net.minecraft.text.Text.literal(
                    "Access to this server is restricted.\nInstall the McGatekeeper mod to connect."
                ));
                return;
            }
            ((FabricServerConfigurationNetworkHandler) handler).addTask(new GateConfigurationTask(handler));
        });

        // Clean up if the player disconnects during configuration
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            var uuid = ((ServerConfigurationNetworkHandlerAccessor) handler).mcgatekeeper_getProfile().id();
            PendingAuthManager.remove(uuid);
        });

        // Receive and verify signed responses
        ResponseHandler.register();

        // Register /gate commands
        CommandRegistrationCallback.EVENT.register(GateCommand::register);

        // Tick pending auth to kick timed-out players
        ServerTickEvents.END_SERVER_TICK.register(server -> PendingAuthManager.tick());
    }
}
