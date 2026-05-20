package com.thegameratort.mcgatekeeper.auth;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.security.SecureRandom;
import java.util.Base64;

public class Ed25519Util {

    public record KeyPair(Ed25519PrivateKeyParameters privateKey, Ed25519PublicKeyParameters publicKey) {}

    public static KeyPair generateKeyPair() {
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(new SecureRandom());
        return new KeyPair(privateKey, privateKey.generatePublicKey());
    }

    public static byte[] sign(byte[] data, Ed25519PrivateKeyParameters privateKey) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }

    public static boolean verify(byte[] data, byte[] signature, Ed25519PublicKeyParameters publicKey) {
        try {
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, publicKey);
            verifier.update(data, 0, data.length);
            return verifier.verifySignature(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static Ed25519PublicKeyParameters decodePublicKey(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return new Ed25519PublicKeyParameters(bytes, 0);
    }

    public static Ed25519PrivateKeyParameters decodePrivateKey(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return new Ed25519PrivateKeyParameters(bytes, 0);
    }

    public static String encodeKey(byte[] keyBytes) {
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /** Returns first 8 hex characters of the raw key bytes. */
    public static String fingerprint(String base64PublicKey) {
        byte[] bytes = Base64.getDecoder().decode(base64PublicKey);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, bytes.length); i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }
}
