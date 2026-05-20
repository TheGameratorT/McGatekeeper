package com.thegameratort.mcgatekeeper.limbo;

import com.thegameratort.mcgatekeeper.Mcgatekeeper;
import com.thegameratort.mcgatekeeper.auth.ChallengeStore;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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

    /** Release from limbo: broadcast join message and remove from limbo set. */
    public static void release(MinecraftServer server, ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        limboSet.remove(uuid);
        ChallengeStore.remove(uuid);

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
