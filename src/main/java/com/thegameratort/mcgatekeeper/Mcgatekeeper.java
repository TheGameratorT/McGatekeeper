package com.thegameratort.mcgatekeeper;

import com.thegameratort.mcgatekeeper.auth.ChallengeStore;
import com.thegameratort.mcgatekeeper.auth.KeyStore;
import com.thegameratort.mcgatekeeper.auth.ServerIdentity;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.command.GateCommand;
import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import com.thegameratort.mcgatekeeper.limbo.LimboPacketQueue;
import com.thegameratort.mcgatekeeper.network.AuthResultPayload;
import com.thegameratort.mcgatekeeper.network.AwaitingAdminPayload;
import com.thegameratort.mcgatekeeper.network.ChallengeHandler;
import com.thegameratort.mcgatekeeper.network.ChallengePayload;
import com.thegameratort.mcgatekeeper.network.ResponseHandler;
import com.thegameratort.mcgatekeeper.network.ResponsePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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

        // Load server identity only when a server actually starts, not on every client launch
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ServerIdentity.load(configDir));

        // Register custom payload types
        PayloadTypeRegistry.playS2C().register(ChallengePayload.ID, ChallengePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AuthResultPayload.ID, AuthResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AwaitingAdminPayload.ID, AwaitingAdminPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ResponsePayload.ID, ResponsePayload.CODEC);

        // Put every player in limbo on join and send them a challenge
        ServerPlayConnectionEvents.JOIN.register(ChallengeHandler::onPlayerJoin);

        // Clean up state on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var uuid = handler.player.getUuid();
            LimboManager.remove(uuid);
            ChallengeStore.remove(uuid);
            LimboPacketQueue.discard(uuid);
        });

        // Receive and verify signed responses
        ResponseHandler.register();

        // Register /gate commands
        CommandRegistrationCallback.EVENT.register(GateCommand::register);

        // Kick timed-out limbo players once per tick
        ServerTickEvents.END_SERVER_TICK.register(LimboManager::tick);
    }
}
