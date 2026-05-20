package com.thegameratort.mcgatekeeper.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AwaitingAdminPayload(int timeoutSeconds) implements CustomPayload {

    public static final CustomPayload.Id<AwaitingAdminPayload> ID =
        new CustomPayload.Id<>(Identifier.of("mcgatekeeper", "awaiting_admin"));

    public static final PacketCodec<PacketByteBuf, AwaitingAdminPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeInt(value.timeoutSeconds()),
        buf -> new AwaitingAdminPayload(buf.readInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
