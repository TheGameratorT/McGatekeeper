package com.thegameratort.mcgatekeeper.network;

import com.thegameratort.mcgatekeeper.auth.ChallengeStore;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ChallengeHandler {

    public static void onPlayerJoin(ServerPlayNetworkHandler handler, net.fabricmc.fabric.api.networking.v1.PacketSender sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.player;

        if (!ServerPlayNetworking.canSend(player, ChallengePayload.ID)) {
            player.networkHandler.disconnect(Text.literal("This server requires the McGatekeeper client mod."));
            return;
        }

        LimboManager.addToLimbo(player.getUuid());
        byte[] nonce = ChallengeStore.createChallenge(player.getUuid());
        ServerPlayNetworking.send(player, new ChallengePayload(nonce, GateConfig.LIMBO_TIMEOUT_SECONDS));
    }
}
