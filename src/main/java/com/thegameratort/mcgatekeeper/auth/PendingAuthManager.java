package com.thegameratort.mcgatekeeper.auth;

import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.network.GateConfigurationTask;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PendingAuthManager {

    private record Entry(
        byte[] nonce,
        long createdMs,
        String username,
        ServerConfigurationNetworkHandler handler,
        @Nullable String pendingPublicKey
    ) {
        Entry withPublicKey(String pk) {
            return new Entry(nonce, createdMs, username, handler, pk);
        }
    }

    private static final ConcurrentMap<UUID, Entry> pending = new ConcurrentHashMap<>();
    private static final SecureRandom RNG = new SecureRandom();

    public static byte[] register(UUID uuid, String username, ServerConfigurationNetworkHandler handler) {
        byte[] nonce = new byte[32];
        RNG.nextBytes(nonce);
        pending.put(uuid, new Entry(nonce, System.currentTimeMillis(), username, handler, null));
        return nonce;
    }

    public static boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public static byte[] getNonce(UUID uuid) {
        Entry e = pending.get(uuid);
        return e == null ? null : e.nonce();
    }

    public static void setPendingPublicKey(UUID uuid, String key) {
        pending.computeIfPresent(uuid, (k, e) -> e.withPublicKey(key));
    }

    public static @Nullable String getPendingPublicKey(UUID uuid) {
        Entry e = pending.get(uuid);
        return e == null ? null : e.pendingPublicKey();
    }

    public static @Nullable String getUsername(UUID uuid) {
        Entry e = pending.get(uuid);
        return e == null ? null : e.username();
    }

    public static boolean isExpired(UUID uuid, int timeoutSeconds) {
        Entry e = pending.get(uuid);
        if (e == null) return true;
        return System.currentTimeMillis() - e.createdMs() > timeoutSeconds * 1000L;
    }

    /** Completes auth: transitions the player from configuration phase into play. */
    public static void complete(UUID uuid) {
        Entry e = pending.remove(uuid);
        if (e != null) {
            ((FabricServerConfigurationNetworkHandler) e.handler()).completeTask(GateConfigurationTask.KEY);
        }
    }

    public static void remove(UUID uuid) {
        pending.remove(uuid);
    }

    public static Set<UUID> getPendingUuids() {
        return Collections.unmodifiableSet(pending.keySet());
    }

    /** Called each server tick to disconnect players whose challenge has timed out. */
    public static void tick() {
        for (UUID uuid : pending.keySet()) {
            if (isExpired(uuid, GateConfig.INSTANCE.limboTimeoutSeconds)) {
                Entry e = pending.remove(uuid);
                if (e != null) {
                    e.handler().disconnect(Text.literal("Not authorized"));
                }
            }
        }
    }
}
