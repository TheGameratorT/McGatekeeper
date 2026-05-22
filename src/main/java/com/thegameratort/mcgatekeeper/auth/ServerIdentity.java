package com.thegameratort.mcgatekeeper.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.thegameratort.mcgatekeeper.Mcgatekeeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerIdentity {

    private record StoredKey(String privateKey, String publicKey) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static byte[] privateKey;
    private static byte[] publicKey;

    public static void load(Path configDir) {
        Path keyFile = configDir.resolve("server.key");
        try {
            if (Files.exists(keyFile)) {
                String json = Files.readString(keyFile);
                StoredKey stored = GSON.fromJson(json, StoredKey.class);
                privateKey = Ed25519Util.decodeKey(stored.privateKey());
                publicKey = Ed25519Util.decodeKey(stored.publicKey());
            } else {
                Ed25519Util.KeyPair kp = Ed25519Util.generateKeyPair();
                privateKey = kp.privateKey();
                publicKey = kp.publicKey();
                Files.createDirectories(configDir);
                Files.writeString(keyFile, GSON.toJson(new StoredKey(
                    Ed25519Util.encodeKey(privateKey),
                    Ed25519Util.encodeKey(publicKey)
                )));
                Mcgatekeeper.LOGGER.info("[McGatekeeper] Generated new server signing key (fingerprint: {}).",
                    Ed25519Util.fingerprint(Ed25519Util.encodeKey(publicKey)));
            }
        } catch (IOException | JsonParseException e) {
            Mcgatekeeper.LOGGER.error("[McGatekeeper] Failed to read or write server.key — cannot start safely. Check file permissions under the config directory.", e);
            throw new RuntimeException("[McGatekeeper] Fatal: could not persist server signing key. Server startup aborted.", e);
        }
    }

    public static byte[] getPublicKey() {
        return publicKey;
    }

    public static byte[] getPrivateKey() {
        return privateKey;
    }
}
