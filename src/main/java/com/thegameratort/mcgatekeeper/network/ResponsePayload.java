package com.thegameratort.mcgatekeeper.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// publicKey is included alongside the signature so the server can register it
// on first use (via /gate allow) and verify which stored key authenticated.
public record ResponsePayload(byte[] publicKey, byte[] signature) implements CustomPayload {

    public static final CustomPayload.Id<ResponsePayload> ID =
            new CustomPayload.Id<>(Identifier.of("mcgatekeeper", "response"));

    public static final PacketCodec<PacketByteBuf, ResponsePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBytes(value.publicKey());
                buf.writeBytes(value.signature());
            },
            buf -> {
                byte[] pk = new byte[32];
                buf.readBytes(pk);
                byte[] sig = new byte[64];
                buf.readBytes(sig);
                return new ResponsePayload(pk, sig);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
