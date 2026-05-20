package com.thegameratort.mcgatekeeper.mixin;

import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void gate_blockMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (LimboManager.isInLimbo(player.getUuid())) ci.cancel();
    }

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void gate_blockChat(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (LimboManager.isInLimbo(player.getUuid())) ci.cancel();
    }

    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    private void gate_blockCommand(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        if (LimboManager.isInLimbo(player.getUuid())) ci.cancel();
    }
}
