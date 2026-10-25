package com.immersivestorage.client;

import com.immersivestorage.block.FeudalDrawerBlock;
import com.immersivestorage.block.FeudalDrawerBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class FeudalDrawerBlockEntityRenderer implements BlockEntityRenderer<FeudalDrawerBlockEntity> {
    private static final double MAX_ITEM_DISTANCE_SQ = 24.0 * 24.0;
    private static final double MAX_TEXT_DISTANCE_SQ = 12.0 * 12.0;

    private final ItemRenderer itemRenderer;
    private final TextRenderer textRenderer;

    public FeudalDrawerBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public int getRenderDistance() {
        return 24;
    }

    @Override
    public void render(FeudalDrawerBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (entity.isEmpty() || entity.getWorld() == null) {
            return;
        }
        BlockState state = entity.getCachedState();
        if (!state.contains(FeudalDrawerBlock.FACING)) {
            return;
        }
        Direction facing = state.get(FeudalDrawerBlock.FACING);
        BlockPos pos = entity.getPos();

        // ------------------------------------------------------------ CULLING
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d cam = camera.getPos();
        double dx = pos.getX() + 0.5 - cam.x;
        double dy = pos.getY() + 0.5 - cam.y;
        double dz = pos.getZ() + 0.5 - cam.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        // 1) Distancia máxima
        if (distSq > MAX_ITEM_DISTANCE_SQ) {
            return;
        }
        // 2) Cara trasera: la cámara debe estar delante del plano de la cara frontal
        double cameraAlongNormal = -(dx * facing.getOffsetX() + dz * facing.getOffsetZ());
        if (cameraAlongNormal < 0.5) {
            return;
        }
        // 3) Bloque detrás de la cámara (fuera del frustum)
        float pitchRad = camera.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        float yawRad = -camera.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double cosP = MathHelper.cos(pitchRad);
        double lx = MathHelper.sin(yawRad) * cosP;
        double ly = -MathHelper.sin(pitchRad);
        double lz = MathHelper.cos(yawRad) * cosP;
        if (dx * lx + dy * ly + dz * lz < -1.0) {
            return;
        }

        // Un bloque opaco tiene luz 0 en su propia posición: usamos la del aire delante.
        int frontLight = WorldRenderer.getLightmapCoordinates(entity.getWorld(), pos.offset(facing));

        // --------------------------------------------------------------- ÍTEM
        ItemStack display = entity.getDisplayStack();
        matrices.push();
        matrices.translate(0.5 + facing.getOffsetX() * 0.505, 0.58, 0.5 + facing.getOffsetZ() * 0.505);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - facing.asRotation()));
        matrices.scale(0.5f, 0.5f, 0.05f);
        itemRenderer.renderItem(display, ModelTransformationMode.FIXED, frontLight,
                OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getWorld(), (int) pos.asLong());
        matrices.pop();

        // ------------------------------------------------------------ CANTIDAD
        if (distSq <= MAX_TEXT_DISTANCE_SQ) {
            String label = String.valueOf(entity.getCount());
            matrices.push();
            matrices.translate(0.5 + facing.getOffsetX() * 0.502, 0.14, 0.5 + facing.getOffsetZ() * 0.502);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.scale(0.012f, -0.012f, 0.012f);
            float width = textRenderer.getWidth(label);
            textRenderer.draw(label, -width / 2.0f, 0.0f, 0xFFFFFFFF, false,
                    matrices.peek().getPositionMatrix(), vertexConsumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, frontLight);
            matrices.pop();
        }
    }
}
