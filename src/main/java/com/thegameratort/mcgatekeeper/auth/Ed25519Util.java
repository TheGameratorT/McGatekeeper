package com.thegameratort.mcgatekeeper.auth;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class Ed25519Util {

    public record KeyPair(byte[] privateKey, byte[] publicKey) {}

    // Fixed ASN.1 headers for Ed25519 (RFC 8410). These never change.
    // SubjectPublicKeyInfo: SEQUENCE { SEQUENCE { OID 1.3.101.112 } BIT STRING { 0x00 <32 bytes> } }
    private static final byte[] X509_HEADER = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    // PrivateKeyInfo: SEQUENCE { INTEGER 0  SEQUENCE { OID } OCTET STRING { OCTET STRING { <32 bytes> } } }
    private static final byte[] PKCS8_HEADER = {
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
    };

    public static KeyPair generateKeyPair() {
        try {
            java.security.KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            // JCA encodes public as 44-byte X.509 DER; raw key is the last 32 bytes.
            // JCA encodes private as 48-byte PKCS#8 DER; raw seed is the last 32 bytes.
            byte[] pub = Arrays.copyOfRange(kp.getPublic().getEncoded(), X509_HEADER.length, 44);
            byte[] priv = Arrays.copyOfRange(kp.getPrivate().getEncoded(), PKCS8_HEADER.length, 48);
            return new KeyPair(priv, pub);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sign(byte[] data, byte[] rawPrivateKey) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(toJcaPrivate(rawPrivateKey));
            sig.update(data);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verify(byte[] data, byte[] signature, byte[] rawPublicKey) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(toJcaPublic(rawPublicKey));
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static String encodeKey(byte[] rawKeyBytes) {
        return Base64.getEncoder().encodeToString(rawKeyBytes);
    }

    public static byte[] decodeKey(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    /** Returns the first 8 hex characters of the raw key bytes. */
    public static String fingerprint(String base64PublicKey) {
        byte[] bytes = Base64.getDecoder().decode(base64PublicKey);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, bytes.length); i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    private static PublicKey toJcaPublic(byte[] raw) {
        if (raw.length != 32) throw new IllegalArgumentException("Ed25519 public key must be 32 bytes, got " + raw.length);
        byte[] encoded = new byte[44];
        System.arraycopy(X509_HEADER, 0, encoded, 0, X509_HEADER.length);
        System.arraycopy(raw, 0, encoded, X509_HEADER.length, 32);
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static PrivateKey toJcaPrivate(byte[] raw) {
        if (raw.length != 32) throw new IllegalArgumentException("Ed25519 private key must be 32 bytes, got " + raw.length);
        byte[] encoded = new byte[48];
        System.arraycopy(PKCS8_HEADER, 0, encoded, 0, PKCS8_HEADER.length);
        System.arraycopy(raw, 0, encoded, PKCS8_HEADER.length, 32);
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
