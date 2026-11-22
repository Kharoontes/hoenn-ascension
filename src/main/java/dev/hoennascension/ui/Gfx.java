package dev.hoennascension.ui;

import net.minecraft.client.gui.DrawContext;

/** Utilidades de dibujo propias (sin texturas externas). */
public final class Gfx {
    private Gfx() {}

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int ar = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int ag = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int ab = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Multiplica el alfa de un color ARGB. */
    public static int alpha(int argb, float mul) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, mul)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int rgb(int rgb, float alpha) {
        return alpha(0xFF000000 | (rgb & 0xFFFFFF), alpha);
    }

    /** Franja con el degradado del tema (azul → violeta → rojo) en horizontal. */
    public static void hBand(DrawContext c, int x1, int y1, int x2, int y2, Theme th, float alpha,
                             float tFrom, float tTo) {
        int w = x2 - x1;
        if (w <= 0) return;
        for (int x = x1; x < x2; x += 2) {
            float t = tFrom + (tTo - tFrom) * ((x - x1) / (float) w);
            c.fill(x, y1, Math.min(x + 2, x2), y2, alpha(th.edgeAt(t), alpha));
        }
    }

    /** Panel con fondo en degradado vertical y borde en degradado azul→rojo. */
    public static void panel(DrawContext c, int x, int y, int w, int h, Theme th, float alpha) {
        alpha = Math.min(1f, alpha * th.opacityMul);
        float edge = Math.min(1f, alpha + 0.45f);
        c.fillGradient(x, y, x + w, y + h, alpha(th.bgTop, alpha), alpha(th.bgBottom, alpha));
        hBand(c, x, y, x + w, y + 1, th, edge, 0f, 1f);
        hBand(c, x, y + h - 1, x + w, y + h, th, edge, 0f, 1f);
        c.fill(x, y, x + 1, y + h, alpha(th.edgeA, edge));
        c.fill(x + w - 1, y, x + w, y + h, alpha(th.edgeC, edge));
    }

    public static void diamond(DrawContext c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            c.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    public static void diamondOutline(DrawContext c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            c.fill(cx - w, cy + dy, cx - w + 1, cy + dy + 1, color);
            c.fill(cx + w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}