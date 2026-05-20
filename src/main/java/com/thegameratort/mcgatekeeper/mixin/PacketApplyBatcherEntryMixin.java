package com.thegameratort.mcgatekeeper.mixin;

import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks all incoming game packets for limbo players at the single dispatch
 * point where every main-thread-bound packet is applied.
 *
 * Keep-alive and pong packets are handled directly on the Netty thread
 * (no forceMainThread) so they never reach this batcher and always work.
 * CustomPayloadC2SPacket must pass through to deliver our ResponsePayload.
 */
@Mixin(targets = "net.minecraft.network.PacketApplyBatcher$Entry")
public class PacketApplyBatcherEntryMixin {

    // @Final without = null: the Mixin AP suppresses the "final field not
    // initialised" compile error. Using = null would cause Mixin to inject
    // a null-assignment into the record's canonical constructor.
    @Shadow @Final private PacketListener listener;
    @Shadow @Final private Packet<?> packet;

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void gate_blockLimboIncoming(CallbackInfo ci) {
        if (!(listener instanceof ServerPlayNetworkHandler playHandler)) return;
        if (!LimboManager.isInLimbo(playHandler.player.getUuid())) return;
        if (packet instanceof CustomPayloadC2SPacket) return;
        ci.cancel();
    }
}
