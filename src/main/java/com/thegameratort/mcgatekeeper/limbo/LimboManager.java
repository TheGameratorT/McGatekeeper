package com.thegameratort.mcgatekeeper.limbo;

import com.thegameratort.mcgatekeeper.Mcgatekeeper;
import com.thegameratort.mcgatekeeper.auth.ChallengeStore;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.network.AuthResultPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LimboManager {

    private static final Set<UUID> limboSet = ConcurrentHashMap.newKeySet();

    // Tracks the player currently going through PlayerManager.onPlayerConnect so the
    // PlayerManagerMixin can suppress their join message without a local-variable capture.
    private static ServerPlayerEntity currentlyConnecting = null;

    public static void addToLimbo(UUID uuid) {
        limboSet.add(uuid);
    }

    public static boolean isInLimbo(UUID uuid) {
        return limboSet.contains(uuid);
    }

    public static void remove(UUID uuid) {
        limboSet.remove(uuid);
    }

    /** Release from limbo: signal the client, flush queued packets, broadcast join message. */
    public static void release(MinecraftServer server, ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        limboSet.remove(uuid);
        ChallengeStore.remove(uuid);

        // Tell the client mod that auth succeeded so it can dismiss the pending screen
        ServerPlayNetworking.send(player, new AuthResultPayload());

        // Flush all queued world packets now that the player is trusted
        List<Packet<?>> queued = LimboPacketQueue.flush(uuid);
        for (Packet<?> packet : queued) {
            player.networkHandler.sendPacket(packet);
        }

        Text joinMessage = Text.translatable("multiplayer.player.joined", player.getDisplayName());
        server.getPlayerManager().broadcast(joinMessage, false);
        Mcgatekeeper.LOGGER.info("[McGatekeeper] {} authenticated and released from limbo.", player.getName().getString());
    }

    /** Called every server tick; kicks players whose challenge has timed out. */
    public static void tick(MinecraftServer server) {
        for (UUID uuid : limboSet) {
            if (ChallengeStore.isExpired(uuid, GateConfig.LIMBO_TIMEOUT_SECONDS)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    player.networkHandler.disconnect(Text.literal("Not authorised"));
                } else {
                    // Player already disconnected; clean up state
                    limboSet.remove(uuid);
                    ChallengeStore.remove(uuid);
                }
            }
        }
    }

    public static void setCurrentlyConnecting(ServerPlayerEntity player) {
        currentlyConnecting = player;
    }

    public static void clearCurrentlyConnecting() {
        currentlyConnecting = null;
    }

    public static ServerPlayerEntity getCurrentlyConnecting() {
        return currentlyConnecting;
    }
}
