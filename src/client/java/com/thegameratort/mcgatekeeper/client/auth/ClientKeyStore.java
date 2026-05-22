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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores one Ed25519 keypair per server (keyed by server public key, base64).
 * Persisted outside the instance directory so keys are not accidentally shared
 * when exporting or duplicating a modpack instance:
 *   Linux/Flatpak : $XDG_DATA_HOME/mcgatekeeper/<gameDirHash>/server-keys.json
 *   macOS         : ~/Library/Application Support/mcgatekeeper/<gameDirHash>/server-keys.json
 *   Windows       : %APPDATA%/mcgatekeeper/<gameDirHash>/server-keys.json
 * The hash is the first 16 hex digits of SHA-256(absoluteGameDir).
 */
public class ClientKeyStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcgatekeeper");

    /** One server's client keypair as stored in JSON. */
    private record StoredPair(String privateKey, String publicKey) {}

    /** A displayable entry: server public key (base64) and the corresponding client public key (base64). */
    public record KeyEntry(String serverKeyB64, String clientPublicKeyB64) {}

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, StoredPair> keyMap = new HashMap<>(); // server public key (base64) → client keypair
    private Path keysFile;

    public void load() {
        Path dir = resolveKeysDir();
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

    /** Returns a snapshot of all known server→client key pairs for display. */
    public List<KeyEntry> getEntries() {
        List<KeyEntry> list = new ArrayList<>(keyMap.size());
        for (Map.Entry<String, StoredPair> e : keyMap.entrySet()) {
            list.add(new KeyEntry(e.getKey(), e.getValue().publicKey()));
        }
        return list;
    }

    /** Removes the entry for the given server public key (base64). Returns true if it existed. */
    public boolean removeEntry(String serverKeyB64) {
        if (keyMap.remove(serverKeyB64) != null) {
            save();
            return true;
        }
        return false;
    }

    /**
     * Exports all keypairs to a JSON file at the given path.
     * The file contains private keys — keep it secure.
     * @return number of entries written
     */
    public int exportTo(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, gson.toJson(keyMap));
        return keyMap.size();
    }

    /**
     * Imports keypairs from a JSON file, skipping entries whose server key already exists.
     * @return int[]{imported, skipped}
     */
    public int[] importFrom(Path path) throws IOException {
        String json = Files.readString(path);
        Type type = new TypeToken<Map<String, StoredPair>>() {}.getType();
        Map<String, StoredPair> loaded;
        try {
            loaded = gson.fromJson(json, type);
        } catch (JsonParseException e) {
            throw new IOException("Invalid key file: " + e.getMessage(), e);
        }
        if (loaded == null) return new int[]{0, 0};
        int imported = 0, skipped = 0;
        for (Map.Entry<String, StoredPair> entry : loaded.entrySet()) {
            if (entry.getValue() == null) continue;
            if (!keyMap.containsKey(entry.getKey())) {
                keyMap.put(entry.getKey(), entry.getValue());
                imported++;
            } else {
                skipped++;
            }
        }
        if (imported > 0) save();
        return new int[]{imported, skipped};
    }

    /** Returns the path to the on-disk keys file, or null if load() has not been called. */
    public Path getStoragePath() {
        return keysFile;
    }

    private void save() {
        if (keysFile == null) return;
        try {
            Files.createDirectories(keysFile.getParent());
            Files.writeString(keysFile, gson.toJson(keyMap));
        } catch (IOException ignored) {}
    }

    private static Path resolveKeysDir() {
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        String hash = sha256Prefix(gameDir.toString());
        return resolveDataBase().resolve("mcgatekeeper").resolve(hash);
    }

    private static Path resolveDataBase() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return appData != null
                ? Path.of(appData)
                : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
        }
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support");
        }
        // Linux / BSD / other — respect XDG (Flatpak sets XDG_DATA_HOME automatically)
        String xdg = System.getenv("XDG_DATA_HOME");
        return (xdg != null && !xdg.isBlank())
            ? Path.of(xdg)
            : Path.of(System.getProperty("user.home"), ".local", "share");
    }

    private static String sha256Prefix(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // SHA-256 is always available
        }
    }
}
