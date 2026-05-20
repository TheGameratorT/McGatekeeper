package com.thegameratort.mcgatekeeper.auth;

import com.thegameratort.mcgatekeeper.Mcgatekeeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class ServerIdentity {

    private static String id;

    public static void load(Path configDir) {
        Path idFile = configDir.resolve("server.id");
        try {
            if (Files.exists(idFile)) {
                id = Files.readString(idFile).strip();
            } else {
                id = UUID.randomUUID().toString();
                Files.createDirectories(configDir);
                Files.writeString(idFile, id);
                Mcgatekeeper.LOGGER.info("[McGatekeeper] Generated new server identity: {}", id);
            }
        } catch (IOException e) {
            // Fall back to a runtime-only UUID so the server still starts
            id = UUID.randomUUID().toString();
            Mcgatekeeper.LOGGER.error("[McGatekeeper] Failed to persist server.id — using ephemeral identity", e);
        }
    }

    public static String get() {
        return id;
    }
}
