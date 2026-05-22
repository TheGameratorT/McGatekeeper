package com.thegameratort.mcgatekeeper.network;

import com.mojang.authlib.GameProfile;
import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.auth.PendingAuthManager;
import com.thegameratort.mcgatekeeper.auth.ServerIdentity;
import com.thegameratort.mcgatekeeper.config.GateConfig;
import com.thegameratort.mcgatekeeper.mixin.ServerConfigurationNetworkHandlerAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayerConfigurationTask;

import java.util.function.Consumer;

public class GateConfigurationTask implements ServerPlayerConfigurationTask {

    public static final Key KEY = new Key("mcgatekeeper:auth");

    private final ServerConfigurationNetworkHandler handler;

    public GateConfigurationTask(ServerConfigurationNetworkHandler handler) {
        this.handler = handler;
    }

    @Override
    public void sendPacket(Consumer<Packet<?>> sender) {
        GameProfile profile = ((ServerConfigurationNetworkHandlerAccessor) handler).mcgatekeeper_getProfile();
        byte[] nonce = PendingAuthManager.register(profile.id(), profile.name(), handler);
        byte[] message = Ed25519Util.buildSignedMessage(ServerIdentity.getPublicKey(), nonce);
        byte[] serverSignature = Ed25519Util.sign(message, ServerIdentity.getPrivateKey());
        sender.accept(ServerConfigurationNetworking.createS2CPacket(
            new ChallengePayload(nonce, GateConfig.INSTANCE.limboTimeoutSeconds, ServerIdentity.getPublicKey(), serverSignature)
        ));
    }

    @Override
    public Key getKey() {
        return KEY;
    }
}
