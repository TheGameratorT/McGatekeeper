package com.thegameratort.mcgatekeeper.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AuthResultPayload() implements CustomPayload {

    public static final CustomPayload.Id<AuthResultPayload> ID =
            new CustomPayload.Id<>(Identifier.of("mcgatekeeper", "auth_result"));

    public static final PacketCodec<PacketByteBuf, AuthResultPayload> CODEC =
            PacketCodec.of((value, buf) -> {}, buf -> new AuthResultPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
