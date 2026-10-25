package com.immersivestorage.client;

import com.immersivestorage.ImmersiveStorageMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ImmersiveStorageClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.immersivestorage";

    public static KeyBinding HIGHLIGHT_KEY;
    public static KeyBinding TOGGLE_HUD_KEY;

    @Override
    public void onInitializeClient() {
        // Renderer del ítem en la cara frontal del cajón
        BlockEntityRendererFactories.register(
                ImmersiveStorageMod.FEUDAL_DRAWER_BE, FeudalDrawerBlockEntityRenderer::new);

        // Keybindings (configurables en Opciones > Controles)
        HIGHLIGHT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.immersivestorage.highlight", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));
        TOGGLE_HUD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.immersivestorage.toggle_hud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

        HudOverlay.load();
        StorageHighlightClient.init();
        HudRenderCallback.EVENT.register(HudOverlay::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (HIGHLIGHT_KEY.wasPressed()) {
                if (client.player != null) {
                    StorageHighlightClient.requestHighlight();
                }
            }
            while (TOGGLE_HUD_KEY.wasPressed()) {
                boolean visible = HudOverlay.toggle();
                if (client.player != null) {
                    Text msg = visible
                            ? Text.translatable("message.immersivestorage.hud.enabled")
                            : Text.translatable("message.immersivestorage.hud.disabled",
                                    TOGGLE_HUD_KEY.getBoundKeyLocalizedText());
                    client.player.sendMessage(msg, true);
                }
            }
        });
    }
}
