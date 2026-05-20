package com.thegameratort.mcgatekeeper.auth;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChallengeStore {

    private record Entry(byte[] nonce, long createdMs, String pendingPublicKey) {
        Entry withPublicKey(String pk) {
            return new Entry(nonce, createdMs, pk);
        }
    }

    private static final ConcurrentMap<UUID, Entry> challenges = new ConcurrentHashMap<>();
    private static final SecureRandom RNG = new SecureRandom();

    public static byte[] createChallenge(UUID uuid) {
        byte[] nonce = new byte[32];
        RNG.nextBytes(nonce);
        challenges.put(uuid, new Entry(nonce, System.currentTimeMillis(), null));
        return nonce;
    }

    public static byte[] getChallenge(UUID uuid) {
        Entry e = challenges.get(uuid);
        return e == null ? null : e.nonce();
    }

    public static void setPendingPublicKey(UUID uuid, String base64PublicKey) {
        challenges.computeIfPresent(uuid, (k, e) -> e.withPublicKey(base64PublicKey));
    }

    public static String getPendingPublicKey(UUID uuid) {
        Entry e = challenges.get(uuid);
        return e == null ? null : e.pendingPublicKey();
    }

    public static boolean isExpired(UUID uuid, int timeoutSeconds) {
        Entry e = challenges.get(uuid);
        if (e == null) return true;
        return System.currentTimeMillis() - e.createdMs() > timeoutSeconds * 1000L;
    }

    public static void remove(UUID uuid) {
        challenges.remove(uuid);
    }
}
