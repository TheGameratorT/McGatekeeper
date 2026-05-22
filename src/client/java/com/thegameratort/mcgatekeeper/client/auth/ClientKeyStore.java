package com.thegameratort.mcgatekeeper.client.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores one Ed25519 keypair per server (keyed by server identity UUID).
 * Persisted in &lt;config-dir&gt;/mcgatekeeper/server-keys.json.
 */
public class ClientKeyStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcgatekeeper");

    private record StoredPair(String privateKey, String publicKey) {}

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, StoredPair> keyMap = new HashMap<>(); // server public key (base64) → client keypair
    private Path keysFile;

    public void load() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("mcgatekeeper");
        keysFile = dir.resolve("server-keys.json");
        if (!Files.exists(keysFile)) return;
        try {
            String json = Files.readString(keysFile);
            Type type = new TypeToken<Map<String, StoredPair>>() {}.getType();
            Map<String, StoredPair> loaded = gson.fromJson(json, type);
            if (loaded != null) keyMap.putAll(loaded);
        } catch (IOException | JsonParseException ignored) {
            // Start fresh if the file is corrupt
        }
    }

    /** Returns the raw private key bytes for the given server, generating and persisting a new keypair if needed. */
    public byte[] getPrivateKey(byte[] serverPublicKey) {
        return Ed25519Util.decodeKey(getOrCreate(Ed25519Util.encodeKey(serverPublicKey)).privateKey());
    }

    /** Returns the raw public key bytes for the given server, generating and persisting a new keypair if needed. */
    public byte[] getPublicKey(byte[] serverPublicKey) {
        return Ed25519Util.decodeKey(getOrCreate(Ed25519Util.encodeKey(serverPublicKey)).publicKey());
    }

    private StoredPair getOrCreate(String serverKeyB64) {
        StoredPair pair = keyMap.get(serverKeyB64);
        if (pair != null) return pair;

        Ed25519Util.KeyPair kp = Ed25519Util.generateKeyPair();
        pair = new StoredPair(
            Ed25519Util.encodeKey(kp.privateKey()),
            Ed25519Util.encodeKey(kp.publicKey())
        );
        keyMap.put(serverKeyB64, pair);
        save();
        LOGGER.info("[McGatekeeper] New server key (fingerprint: {}). Generated client keypair.",
            Ed25519Util.fingerprint(serverKeyB64));
        return pair;
    }

    private void save() {
        if (keysFile == null) return;
        try {
            Files.createDirectories(keysFile.getParent());
            Files.writeString(keysFile, gson.toJson(keyMap));
        } catch (IOException ignored) {}
    }
}
