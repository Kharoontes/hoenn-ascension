package dev.hoennascension.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Iconos temáticos en pixel art (estilo del logo) de canales y funciones, en una sola imagen (atlas).
 * El nombre del icono es el id del canal o de la función. Si no existe, quien llama usa un respaldo.
 */
public final class Icons {
    private static final Identifier ATLAS = Identifier.of("hoennascension", "textures/gui/icons.png");
    /** Orden de los iconos en icons.png (cada uno ocupa 24x24 px). Añade aquí los nuevos al final. */
    private static final String[] NAMES = {
            "all", "general", "teleport", "private", "clan", "legendary",
            "mega", "trade", "donate", "whisper", "settings"
    };
    private static final int CELL = 24;

    private Icons() {}

    private static int indexOf(String name) {
        if (name == null) return -1;
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)) return i;
        return -1;
    }

    public static boolean has(String name) {
        return indexOf(name) >= 0;
    }

    /** Dibuja el icono escalado a size x size. @return false si no hay icono con ese nombre. */
    public static boolean draw(DrawContext c, String name, int x, int y, int size, float alpha) {
        int i = indexOf(name);
        if (i < 0) return false;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        c.drawTexture(ATLAS, x, y, size, size, (float) (i * CELL), 0f, CELL, CELL, CELL * NAMES.length, CELL);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        return true;
    }
}