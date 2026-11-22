package dev.hoennascension.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Texturas del proyecto (logo, bandera, icono) y utilidad para dibujarlas escaladas. */
public final class Art {
    private Art() {}

    public static final Identifier LOGO = Identifier.of("hoennascension", "textures/gui/logo.png");
    public static final Identifier BANNER = Identifier.of("hoennascension", "textures/gui/banner.png");
    public static final Identifier ICON = Identifier.of("hoennascension", "textures/gui/clan_icon.png");

    // Tamaños reales de los PNG (si cambias una imagen, actualiza estos valores)
    public static final int LOGO_W = 360, LOGO_H = 116;
    public static final int BANNER_W = 263, BANNER_H = 640;
    public static final int ICON_W = 16, ICON_H = 16;

    /** Cara del jugador (capa base + capa de sombrero) a partir de su skin de 64x64. */
    public static void drawFace(DrawContext c, Identifier skin, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        c.drawTexture(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        c.drawTexture(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
    }

    /** Dibuja la textura completa escalada a w x h. */
    public static void draw(DrawContext c, Identifier tex, int x, int y, int w, int h,
                            int texW, int texH, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        c.drawTexture(tex, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}