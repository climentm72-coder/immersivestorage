package com.immersivestorage.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Guía de controles en pantalla, pequeña y desactivable con la tecla O. */
public final class HudOverlay {
    private static final float SCALE = 0.75f;
    private static final int MARGIN = 4;
    private static final String CONFIG_FILE = "immersivestorage.properties";
    private static final String KEY_VISIBLE = "hud_visible";

    private static boolean visible = true;

    private HudOverlay() {
    }

    public static boolean isVisible() {
        return visible;
    }

    /** Alterna la visibilidad, la guarda en disco y devuelve el nuevo estado. */
    public static boolean toggle() {
        visible = !visible;
        save();
        return visible;
    }

    public static void load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            visible = Boolean.parseBoolean(props.getProperty(KEY_VISIBLE, "true"));
        } catch (IOException ignored) {
            // si falla, se mantiene el valor por defecto
        }
    }

    private static void save() {
        Properties props = new Properties();
        props.setProperty(KEY_VISIBLE, Boolean.toString(visible));
        try (OutputStream out = Files.newOutputStream(configPath())) {
            props.store(out, "Immersive Storage client settings");
        } catch (IOException ignored) {
            // no es crítico
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!visible || mc.player == null || mc.options.hudHidden || mc.options.debugEnabled) {
            return;
        }
        if (ImmersiveStorageClient.HIGHLIGHT_KEY == null || ImmersiveStorageClient.TOGGLE_HUD_KEY == null) {
            return;
        }

        TextRenderer font = mc.textRenderer;
        List<Text> lines = List.of(
                Text.translatable("hud.immersivestorage.guide.title").formatted(Formatting.GOLD),
                Text.translatable("hud.immersivestorage.guide.highlight",
                        ImmersiveStorageClient.HIGHLIGHT_KEY.getBoundKeyLocalizedText()),
                Text.translatable("hud.immersivestorage.guide.deposit"),
                Text.translatable("hud.immersivestorage.guide.take_one"),
                Text.translatable("hud.immersivestorage.guide.take_stack"),
                Text.translatable("hud.immersivestorage.guide.toggle",
                        ImmersiveStorageClient.TOGGLE_HUD_KEY.getBoundKeyLocalizedText())
                        .formatted(Formatting.GRAY)
        );

        int width = 0;
        for (Text line : lines) {
            width = Math.max(width, font.getWidth(line));
        }
        int lineHeight = font.fontHeight + 1;
        int height = lineHeight * lines.size();

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(MARGIN, MARGIN, 0);
        matrices.scale(SCALE, SCALE, 1.0f);

        context.fill(-3, -3, width + 3, height + 2, 0x66000000);
        int y = 0;
        for (Text line : lines) {
            context.drawText(font, line, 0, y, 0xFFFFFF, true);
            y += lineHeight;
        }
        matrices.pop();
    }
}
