package com.immersivestorage.client;

import com.immersivestorage.StorageHighlightHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Lado cliente del "Smart Highlight": recibe las posiciones del servidor,
 * dibuja un contorno pulsante y emite partículas durante 10 segundos.
 */
public final class StorageHighlightClient {
    private static final int DURATION_TICKS = 200;
    private static final double MAX_RENDER_DISTANCE_SQ = 24.0 * 24.0;

    private static List<BlockPos> targets = List.of();
    private static long endTime = 0L;

    private StorageHighlightClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(StorageHighlightHandler.RESULT_ID,
                (client, handler, buf, responseSender) -> {
                    int n = buf.readVarInt();
                    List<BlockPos> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(buf.readBlockPos());
                    }
                    client.execute(() -> setTargets(client, list));
                });

        ClientTickEvents.END_CLIENT_TICK.register(StorageHighlightClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(StorageHighlightClient::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> targets = List.of());
    }

    /** Se llama al pulsar la tecla Y. */
    public static void requestHighlight() {
        if (ClientPlayNetworking.canSend(StorageHighlightHandler.REQUEST_ID)) {
            ClientPlayNetworking.send(StorageHighlightHandler.REQUEST_ID, PacketByteBufs.create());
        }
    }

    private static void setTargets(MinecraftClient client, List<BlockPos> list) {
        targets = list;
        endTime = client.world != null ? client.world.getTime() + DURATION_TICKS : 0L;
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || targets.isEmpty()) {
            return;
        }
        long time = client.world.getTime();
        if (time > endTime) {
            targets = List.of();
            return;
        }
        if (time % 5 != 0) {
            return;
        }
        Random random = client.world.getRandom();
        for (BlockPos p : targets) {
            client.world.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    p.getX() + random.nextDouble(),
                    p.getY() + 1.05,
                    p.getZ() + random.nextDouble(),
                    0.0, 0.02, 0.0);
        }
    }

    private static void render(WorldRenderContext ctx) {
        if (targets.isEmpty() || ctx.consumers() == null || ctx.world() == null) {
            return;
        }
        VertexConsumerProvider consumers = ctx.consumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        MatrixStack matrices = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();

        float t = ctx.world().getTime() + ctx.tickDelta();
        float pulse = 0.65f + 0.35f * MathHelper.sin(t * 0.25f);

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        for (BlockPos p : targets) {
            if (p.getSquaredDistance(cam.x, cam.y, cam.z) > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }
            WorldRenderer.drawBox(matrices, lines, new Box(p).expand(0.002), 1.0f, 0.85f, 0.1f, pulse);
        }
        matrices.pop();
    }
}
