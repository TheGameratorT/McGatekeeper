package com.thegameratort.mcgatekeeper.mixin;

import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import com.thegameratort.mcgatekeeper.limbo.LimboPacketQueue;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerCommonNetworkHandlerMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void gate_queueLimboPackets(Packet<?> packet, CallbackInfo ci) {
        if (!(((Object) this) instanceof ServerPlayNetworkHandler playHandler)) return;
        UUID uuid = playHandler.player.getUuid();
        if (!LimboManager.isInLimbo(uuid)) return;
        if (isPassthrough(packet)) return;
        LimboPacketQueue.enqueue(uuid, packet);
        ci.cancel();
    }

    private static boolean isPassthrough(Packet<?> packet) {
        return packet instanceof KeepAliveS2CPacket
            || packet instanceof DisconnectS2CPacket
            || packet instanceof CustomPayloadS2CPacket
            || packet instanceof CommonPingS2CPacket;
    }
}
