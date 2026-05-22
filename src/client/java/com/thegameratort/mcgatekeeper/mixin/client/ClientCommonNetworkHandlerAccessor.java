package com.thegameratort.mcgatekeeper.mixin.client;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientCommonNetworkHandler.class)
public interface ClientCommonNetworkHandlerAccessor {
    @Accessor("serverInfo")
    @Nullable ServerInfo mcgatekeeper_getServerInfo();
}
