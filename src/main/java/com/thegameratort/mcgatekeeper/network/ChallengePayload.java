package com.thegameratort.mcgatekeeper.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ChallengePayload(byte[] nonce, int timeoutSeconds) implements CustomPayload {

    public static final CustomPayload.Id<ChallengePayload> ID =
            new CustomPayload.Id<>(Identifier.of("mcgatekeeper", "challenge"));

    public static final PacketCodec<PacketByteBuf, ChallengePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBytes(value.nonce());
                buf.writeInt(value.timeoutSeconds());
            },
            buf -> {
                byte[] nonce = new byte[32];
                buf.readBytes(nonce);
                int timeout = buf.readInt();
                return new ChallengePayload(nonce, timeout);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
