package com.thegameratort.mcgatekeeper.mixin;

import com.thegameratort.mcgatekeeper.config.GateConfig;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftDedicatedServer.class)
public class MinecraftDedicatedServerMixin {

    @Shadow @Final private static Logger LOGGER;

    // The offline-mode check in setupServer is `if (!isOnlineMode()) { LOGGER.warn x4 }`.
    // Returning true from the first isOnlineMode() call makes the branch skip the whole block.
    // ordinal = 0 targets that specific call; ordinal = 1 is an unrelated check later in the method.
    @Redirect(
        method = "setupServer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/dedicated/MinecraftDedicatedServer;isOnlineMode()Z", ordinal = 0)
    )
    private boolean gate_replaceOfflineWarning(MinecraftDedicatedServer instance) {
        boolean online = instance.isOnlineMode();
        if (!online && GateConfig.INSTANCE.replaceOfflineModeWarning) {
            LOGGER.info("Protected by McGatekeeper!");
            return true;
        }
        return online;
    }
}
