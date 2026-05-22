package com.thegameratort.mcgatekeeper.auth;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class KeyStore {

    public record KeyEntry(String label, String publicKey) {}

    private static class PlayerData {
        String username;
        List<KeyEntry> keys;

        PlayerData(String username) {
            this.username = username;
            this.keys = new ArrayList<>();
        }
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, PlayerData> data = new HashMap<>(); // keyed by UUID string
    private Path filePath;

    public synchronized void load(Path configDir) {
        filePath = configDir.resolve("players.json");
        if (!Files.exists(filePath)) return;
        try {
            String json = Files.readString(filePath);
            Type mapType = new TypeToken<Map<String, PlayerData>>(){}.getType();
            Map<String, PlayerData> loaded = gson.fromJson(json, mapType);
            if (loaded != null) data.putAll(loaded);
        } catch (IOException | JsonParseException e) {
            // Start with empty store if file is corrupt
        }
    }

    public synchronized void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, gson.toJson(data));
        } catch (IOException e) {
            // Logged by caller
        }
    }

    public synchronized void addKey(UUID uuid, String username, String label, String publicKey) {
        String id = uuid.toString();
        data.computeIfAbsent(id, k -> new PlayerData(username)).username = username;
        PlayerData pd = data.get(id);
        pd.keys.removeIf(e -> e.label().equals(label)); // replace if same label
        pd.keys.add(new KeyEntry(label, publicKey));
    }

    public synchronized void removeAllKeys(UUID uuid) {
        data.remove(uuid.toString());
    }

    public synchronized boolean removeKey(UUID uuid, String label) {
        PlayerData pd = data.get(uuid.toString());
        if (pd == null) return false;
        return pd.keys.removeIf(e -> e.label().equals(label));
    }

    public synchronized List<KeyEntry> getKeys(UUID uuid) {
        PlayerData pd = data.get(uuid.toString());
        return pd == null ? Collections.emptyList() : new ArrayList<>(pd.keys);
    }

    public synchronized boolean hasAnyKey(UUID uuid) {
        PlayerData pd = data.get(uuid.toString());
        return pd != null && !pd.keys.isEmpty();
    }

    public synchronized String getUsername(UUID uuid) {
        PlayerData pd = data.get(uuid.toString());
        return pd == null ? uuid.toString() : pd.username;
    }

    /** Returns all UUIDs that have at least one key stored. */
    public synchronized Set<String> getAllUuids() {
        return new HashSet<>(data.keySet());
    }
}
