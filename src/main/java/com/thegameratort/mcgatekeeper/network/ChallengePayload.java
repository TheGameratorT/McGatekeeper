package com.thegameratort.mcgatekeeper.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ChallengePayload(
    byte[] nonce,
    int timeoutSeconds,
    byte[] serverPublicKey,
    byte[] serverSignature
) implements CustomPayload {

    public static final CustomPayload.Id<ChallengePayload> ID =
            new CustomPayload.Id<>(Identifier.of("mcgatekeeper", "challenge"));

    public static final PacketCodec<PacketByteBuf, ChallengePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBytes(value.nonce());
                buf.writeInt(value.timeoutSeconds());
                buf.writeBytes(value.serverPublicKey());
                buf.writeBytes(value.serverSignature());
            },
            buf -> {
                byte[] nonce = new byte[32];
                buf.readBytes(nonce);
                int timeout = buf.readInt();
                byte[] serverPublicKey = new byte[32];
                buf.readBytes(serverPublicKey);
                byte[] serverSignature = new byte[64];
                buf.readBytes(serverSignature);
                return new ChallengePayload(nonce, timeout, serverPublicKey, serverSignature);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
