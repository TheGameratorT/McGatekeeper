package com.thegameratort.mcgatekeeper.auth;

import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.network.GateConfigurationTask;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PendingAuthManager {

    /**
     * One in-flight authentication. Keyed by handler (not UUID) so that
     * multiple connections for the same UUID can coexist — only at response
     * time do we decide who wins.
     */
    public static final class Entry {
        public final UUID uuid;
        public final String username;
        public final ServerConfigurationNetworkHandler handler;
        public final byte[] nonce;
        public final long createdMs;
        /** Set once the client submits a response. Non-null means awaiting admin approval. */
        @Nullable public volatile String pendingPublicKey;

        Entry(UUID uuid, String username, ServerConfigurationNetworkHandler handler, byte[] nonce) {
            this.uuid = uuid;
            this.username = username;
            this.handler = handler;
            this.nonce = nonce;
            this.createdMs = System.currentTimeMillis();
        }
    }

    private static final ConcurrentMap<ServerConfigurationNetworkHandler, Entry> pending = new ConcurrentHashMap<>();
    /**
     * UUIDs that have authenticated but haven't yet appeared in PlayerManager.
     * Closes the race where a sibling unknown-key response arrives between
     * {@link #complete} and ServerPlayConnectionEvents.JOIN.
     */
    private static final Set<UUID> inTransition = ConcurrentHashMap.newKeySet();
    private static final SecureRandom RNG = new SecureRandom();
    private static final Object LOCK = new Object();

    public static byte[] register(UUID uuid, String username, ServerConfigurationNetworkHandler handler) {
        byte[] nonce = new byte[32];
        RNG.nextBytes(nonce);
        pending.put(handler, new Entry(uuid, username, handler, nonce));
        return nonce;
    }

    public static @Nullable Entry get(ServerConfigurationNetworkHandler handler) {
        return pending.get(handler);
    }

    /**
     * Atomically transition this entry to the awaiting-admin state, unless
     * any other entry with the same UUID is already in flight. A concurrent
     * sibling might still authenticate, so the unauthorized newcomer must
     * not be parked on the waiting screen — it's rejected outright.
     *
     * @return true if this entry now holds the awaiting-admin slot.
     */
    public static boolean tryAwaitAdmin(ServerConfigurationNetworkHandler handler, String publicKey) {
        synchronized (LOCK) {
            Entry self = pending.get(handler);
            if (self == null || self.pendingPublicKey != null) return false;
            if (inTransition.contains(self.uuid)) return false;
            for (Entry other : pending.values()) {
                if (other != self && other.uuid.equals(self.uuid)) {
                    return false;
                }
            }
            self.pendingPublicKey = publicKey;
            return true;
        }
    }

    /** Mark a UUID as authenticated and transitioning to play; cleared on JOIN. */
    public static void markInTransition(UUID uuid) {
        inTransition.add(uuid);
    }

    public static void clearInTransition(UUID uuid) {
        inTransition.remove(uuid);
    }

    /** Completes auth for this handler: transitions it from configuration phase into play. */
    public static void complete(ServerConfigurationNetworkHandler handler) {
        Entry e = pending.remove(handler);
        if (e != null) {
            ((FabricServerConfigurationNetworkHandler) handler).completeTask(GateConfigurationTask.KEY);
        }
    }

    /** Disconnect every other pending entry with the same UUID (used on authenticated takeover). */
    public static void disconnectOthers(UUID uuid, ServerConfigurationNetworkHandler self, Text reason) {
        for (Entry e : new ArrayList<>(pending.values())) {
            if (e.handler != self && e.uuid.equals(uuid)) {
                pending.remove(e.handler);
                e.handler.disconnect(reason);
            }
        }
    }

    public static void remove(ServerConfigurationNetworkHandler handler) {
        pending.remove(handler);
    }

    /** Returns the awaiting-admin entry matching this username, if any (case-insensitive). */
    public static @Nullable Entry findAwaitingAdminByName(String username) {
        for (Entry e : pending.values()) {
            if (e.pendingPublicKey != null && username.equalsIgnoreCase(e.username)) return e;
        }
        return null;
    }

    /** All entries currently awaiting admin approval (used for /gate allow tab-complete). */
    public static Collection<Entry> getAwaitingAdminEntries() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : pending.values()) {
            if (e.pendingPublicKey != null) out.add(e);
        }
        return Collections.unmodifiableList(out);
    }

    /** Called each server tick to disconnect entries whose challenge has timed out. */
    public static void tick() {
        long now = System.currentTimeMillis();
        long timeoutMs = GateConfig.INSTANCE.authTimeoutSeconds * 1000L;
        for (Entry e : new ArrayList<>(pending.values())) {
            if (now - e.createdMs > timeoutMs) {
                if (pending.remove(e.handler, e)) {
                    e.handler.disconnect(Text.translatable("disconnect.mcgatekeeper.not_authorized"));
                }
            }
        }
    }
}
