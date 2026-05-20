package com.thegameratort.mcgatekeeper.client.auth;

import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import net.fabricmc.loader.api.FabricLoader;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientKeyStore {

    private Ed25519PrivateKeyParameters privateKey;
    private Ed25519PublicKeyParameters publicKey;

    public void load() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("mcgatekeeper");
        Path privPath = dir.resolve("private.key");
        Path pubPath = dir.resolve("public.key");

        try {
            if (Files.exists(privPath) && Files.exists(pubPath)) {
                privateKey = Ed25519Util.decodePrivateKey(Files.readString(privPath).strip());
                publicKey = Ed25519Util.decodePublicKey(Files.readString(pubPath).strip());
            } else {
                Files.createDirectories(dir);
                Ed25519Util.KeyPair kp = Ed25519Util.generateKeyPair();
                privateKey = kp.privateKey();
                publicKey = kp.publicKey();
                Files.writeString(privPath, Ed25519Util.encodeKey(privateKey.getEncoded()));
                Files.writeString(pubPath, Ed25519Util.encodeKey(publicKey.getEncoded()));
            }
        } catch (IOException e) {
            throw new RuntimeException("McGatekeeper: failed to load/generate keypair", e);
        }
    }

    public Ed25519PrivateKeyParameters getPrivateKey() { return privateKey; }
    public Ed25519PublicKeyParameters getPublicKey() { return publicKey; }
}
