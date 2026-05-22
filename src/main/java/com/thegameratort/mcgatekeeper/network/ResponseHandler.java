package com.thegameratort.mcgatekeeper.network;

import com.mojang.authlib.GameProfile;
import com.thegameratort.mcgatekeeper.Mcgatekeeper;
import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.auth.KeyStore;
import com.thegameratort.mcgatekeeper.auth.PendingAuthManager;
import com.thegameratort.mcgatekeeper.auth.ServerIdentity;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.mixin.ServerConfigurationNetworkHandlerAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

public class ResponseHandler {

    public static void register() {
        ServerConfigurationNetworking.registerGlobalReceiver(ResponsePayload.ID, ResponseHandler::handle);
    }

    private static void handle(ResponsePayload payload, ServerConfigurationNetworking.Context context) {
        ServerConfigurationNetworkHandler handler = context.networkHandler();
        PendingAuthManager.Entry entry = PendingAuthManager.get(handler);
        if (entry == null || entry.pendingPublicKey != null) return;

        GameProfile profile = ((ServerConfigurationNetworkHandlerAccessor) handler).mcgatekeeper_getProfile();
        UUID uuid = profile.id();

        String submittedKeyB64 = Ed25519Util.encodeKey(payload.publicKey());
        byte[] message = Ed25519Util.buildSignedMessage(ServerIdentity.getPublicKey(), entry.nonce);

        boolean authenticated = false;
        List<KeyStore.KeyEntry> storedKeys = Mcgatekeeper.KEY_STORE.getKeys(uuid);
        for (KeyStore.KeyEntry stored : storedKeys) {
            if (stored.publicKey().equals(submittedKeyB64)
                && Ed25519Util.verify(message, payload.signature(), payload.publicKey())) {
                authenticated = true;
                break;
            }
        }

        if (authenticated) {
            // Lock the UUID before completing: there's a window between pending.remove
            // (in complete) and the player actually appearing in PlayerManager, and a
            // sibling unknown-key response arriving in that window must not be parked
            // on the waiting screen. Cleared on ServerPlayConnectionEvents.JOIN.
            PendingAuthManager.markInTransition(uuid);
            // Authorized takeover: kick any other pending session for this UUID
            // (including a session currently awaiting admin approval — they're already trusted)
            // and any in-play duplicate session.
            PendingAuthManager.disconnectOthers(uuid, handler,
                Text.translatable("disconnect.mcgatekeeper.new_connection"));
            context.server().getPlayerManager().disconnectDuplicateLogins(uuid);
            PendingAuthManager.complete(handler);
            Mcgatekeeper.LOGGER.info("[McGatekeeper] {} authenticated.", profile.name());
            return;
        }

        // Reject the new connection if an authenticated session is already in play
        // or transitioning to play.
        if (context.server().getPlayerManager().getPlayer(uuid) != null) {
            handler.disconnect(Text.translatable("disconnect.mcgatekeeper.session_already_active"));
            return;
        }

        // Unknown key: try to claim the awaiting-admin slot for this UUID.
        // Any other in-flight session for the same UUID — whether already awaiting
        // admin or still mid-handshake — blocks this transition. A concurrent
        // sibling might authenticate at any moment, so park no one on the waiting
        // screen needlessly.
        if (!PendingAuthManager.tryAwaitAdmin(handler, submittedKeyB64)) {
            handler.disconnect(Text.translatable("disconnect.mcgatekeeper.session_already_active"));
            Mcgatekeeper.LOGGER.warn("[McGatekeeper] Rejected concurrent unauthorized attempt for {}.", profile.name());
            return;
        }

        ServerConfigurationNetworking.send(handler, new AwaitingAdminPayload(GateConfig.INSTANCE.authTimeoutSeconds));
        Mcgatekeeper.LOGGER.info("[McGatekeeper] {} connected with an unregistered key; an admin can run /gate allow.", profile.name());
    }
}
