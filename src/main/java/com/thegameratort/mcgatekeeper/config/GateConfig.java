package com.thegameratort.mcgatekeeper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GateConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static GateConfig INSTANCE = new GateConfig();

    public int authTimeoutSeconds = 30;
    public boolean replaceOfflineModeWarning = true;

    public static void load(Path configDir) {
        Path path = configDir.resolve("config.json");
        if (Files.exists(path)) {
            try {
                GateConfig loaded = GSON.fromJson(Files.readString(path), GateConfig.class);
                if (loaded != null) INSTANCE = loaded;
            } catch (IOException | JsonParseException e) {
                // Keep defaults if file is corrupt
            }
        }
        save(configDir);
    }

    public static void save(Path configDir) {
        Path path = configDir.resolve("config.json");
        try {
            Files.createDirectories(configDir);
            Files.writeString(path, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            // Ignore
        }
    }
}
