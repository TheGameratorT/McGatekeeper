package com.thegameratort.mcgatekeeper.limbo;

import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LimboPacketQueue {

    private static final ConcurrentHashMap<UUID, List<Packet<?>>> queues = new ConcurrentHashMap<>();

    public static void enqueue(UUID uuid, Packet<?> packet) {
        queues.computeIfAbsent(uuid, k -> Collections.synchronizedList(new ArrayList<>())).add(packet);
    }

    public static List<Packet<?>> flush(UUID uuid) {
        List<Packet<?>> packets = queues.remove(uuid);
        return packets != null ? new ArrayList<>(packets) : List.of();
    }

    public static void discard(UUID uuid) {
        queues.remove(uuid);
    }
}
