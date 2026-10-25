package com.immersivestorage;

import com.immersivestorage.block.FeudalDrawerBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lado servidor del "Smart Highlight".
 * El contenido de los cofres no se sincroniza con el cliente, por eso el escaneo
 * se hace aquí y solo se envían las posiciones que coinciden.
 */
public final class StorageHighlightHandler {
    public static final Identifier REQUEST_ID = ImmersiveStorageMod.id("highlight_request");
    public static final Identifier RESULT_ID = ImmersiveStorageMod.id("highlight_result");

    public static final int RADIUS = 16;
    private static final int MAX_RESULTS = 256;
    private static final int COOLDOWN_TICKS = 10;

    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();

    private StorageHighlightHandler() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_ID,
                (server, player, handler, buf, responseSender) -> server.execute(() -> scan(player)));

        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> LAST_REQUEST.remove(handler.getPlayer().getUuid()));
    }

    private static void scan(ServerPlayerEntity player) {
        if (player.getServer() == null) {
            return;
        }
        int now = player.getServer().getTicks();
        Integer last = LAST_REQUEST.get(player.getUuid());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return;
        }
        LAST_REQUEST.put(player.getUuid(), now);

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            held = player.getOffHandStack();
        }
        if (held.isEmpty()) {
            player.sendMessage(Text.translatable("message.immersivestorage.highlight.empty_hand"), true);
            send(player, List.of());
            return;
        }

        Item item = held.getItem();
        ServerWorld world = player.getServerWorld();
        BlockPos origin = player.getBlockPos();
        int r2 = RADIUS * RADIUS;
        List<BlockPos> results = new ArrayList<>();

        int minCx = (origin.getX() - RADIUS) >> 4;
        int maxCx = (origin.getX() + RADIUS) >> 4;
        int minCz = (origin.getZ() - RADIUS) >> 4;
        int maxCz = (origin.getZ() + RADIUS) >> 4;

        outer:
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                // getWorldChunk no carga ni genera chunks
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if (p.getSquaredDistance(origin) > r2) {
                        continue;
                    }
                    if (matches(be, item)) {
                        results.add(p.toImmutable());
                        if (results.size() >= MAX_RESULTS) {
                            break outer;
                        }
                    }
                }
            }
        }

        if (results.isEmpty()) {
            player.sendMessage(Text.translatable("message.immersivestorage.highlight.none"), true);
        } else {
            player.sendMessage(Text.translatable("message.immersivestorage.highlight.found", results.size()), true);
        }
        send(player, results);
    }

    private static boolean matches(BlockEntity be, Item item) {
        if (be instanceof FeudalDrawerBlockEntity drawer) {
            return drawer.holds(item);
        }
        if (be instanceof Inventory inv) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && s.isOf(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void send(ServerPlayerEntity player, List<BlockPos> positions) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(positions.size());
        for (BlockPos p : positions) {
            buf.writeBlockPos(p);
        }
        ServerPlayNetworking.send(player, RESULT_ID, buf);
    }
}
