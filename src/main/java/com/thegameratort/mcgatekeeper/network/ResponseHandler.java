package com.thegameratort.mcgatekeeper.network;

import com.thegameratort.mcgatekeeper.Mcgatekeeper;
import com.thegameratort.mcgatekeeper.auth.ChallengeStore;
import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.auth.KeyStore;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.util.List;
import java.util.UUID;

public class ResponseHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ResponsePayload.ID, ResponseHandler::handle);
    }

    private static void handle(ResponsePayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        UUID uuid = player.getUuid();

        if (!LimboManager.isInLimbo(uuid)) return;

        byte[] nonce = ChallengeStore.getChallenge(uuid);
        if (nonce == null) return;

        Ed25519PublicKeyParameters submittedKey;
        try {
            submittedKey = new Ed25519PublicKeyParameters(payload.publicKey(), 0);
        } catch (Exception e) {
            Mcgatekeeper.LOGGER.warn("[McGatekeeper] {} sent a malformed public key.", player.getName().getString());
            return;
        }

        String submittedKeyB64 = Ed25519Util.encodeKey(payload.publicKey());

        // Always record the submitted public key so an admin can /gate allow them
        ChallengeStore.setPendingPublicKey(uuid, submittedKeyB64);

        List<KeyStore.KeyEntry> storedKeys = Mcgatekeeper.KEY_STORE.getKeys(uuid);
        boolean authenticated = false;
        for (KeyStore.KeyEntry entry : storedKeys) {
            // Verify only if the submitted public key matches the stored one
            if (entry.publicKey().equals(submittedKeyB64)) {
                authenticated = Ed25519Util.verify(nonce, payload.signature(), submittedKey);
                if (authenticated) break;
            }
        }

        if (authenticated) {
            LimboManager.release(context.server(), player);
        } else {
            ServerPlayNetworking.send(player, new AwaitingAdminPayload(GateConfig.LIMBO_TIMEOUT_SECONDS));
            Mcgatekeeper.LOGGER.info("[McGatekeeper] {} connected with an unregistered key; an admin can run /gate allow.", player.getName().getString());
        }
    }
}
