package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.HoennConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Pastilla flotante (icono del proyecto) que representa al panel minimizado.
 * Clic corto = maximizar. Arrastrar (con el ratón libre: chat, inventario, pausa...) = moverla.
 * Clic derecho = cerrarla del todo. Su posición es proporcional al tamaño de la pantalla.
 */
public final class MinimizedPill {
    private static final int H = 22;

    private static boolean pressing;
    /** Ocultada con clic derecho: vuelve al abrir el panel o al entrar de nuevo a un mundo. */
    private static boolean snoozed;
    private static boolean moved;
    private static int offX, offY;
    private static double startX, startY;

    private MinimizedPill() {}

    private static String badge(int unread) {
        return unread > 99 ? "99+" : String.valueOf(unread);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Rectángulo actual {x, y, ancho, alto} en coordenadas de interfaz. */
    public static int[] rect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        HoennConfig cfg = HoennConfig.get();
        int unread = HoennAscensionClient.STORE.unread("all");
        int w = 6 + Art.ICON_W + 6 + (unread > 0 ? tr.getWidth(badge(unread)) + 8 : 0);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x, y;
        if (cfg.pillXr >= 0f && cfg.pillYr >= 0f) {
            x = clamp(Math.round(cfg.pillXr * sw), 0, Math.max(0, sw - w));
            y = clamp(Math.round(cfg.pillYr * sh), 0, Math.max(0, sh - H));
        } else {
            String anchor = cfg.minimizedAnchor.toLowerCase();
            x = anchor.contains("right") ? sw - w - 6 : 6;
            y = anchor.contains("bottom") ? sh - H - 6 : 6;
        }
        return new int[]{x, y, w, H};
    }

    /** La pastilla solo existe dentro de un mundo o servidor (nunca en el menú principal). */
    private static boolean inWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.world != null;
    }

    /** ¿Debe verse la pastilla ahora? Solo dentro de un mundo; tras minimizar o, por defecto, siempre. */
    private static boolean shown() {
        HoennConfig cfg = HoennConfig.get();
        return cfg.enabled && inWorld() && !snoozed && (cfg.minimized || cfg.pillAlwaysVisible);
    }

    public static void unsnooze() {
        snoozed = false;
    }

    public static boolean hit(double mx, double my) {
        if (!shown()) return false;
        int[] r = rect();
        return Gfx.inside(mx, my, r[0], r[1], r[2], r[3]);
    }

    // ---- Arrastre -----------------------------------------------------------------------

    /** Botón pulsado sobre la pastilla. @return true si debe consumirse el clic. */
    public static boolean press(double mx, double my) {
        if (!hit(mx, my)) return false;
        int[] r = rect();
        pressing = true;
        moved = false;
        offX = (int) mx - r[0];
        offY = (int) my - r[1];
        startX = mx;
        startY = my;
        return true;
    }

    /** Se llama cada frame con la posición del ratón mientras haya otra pantalla abierta. */
    public static void dragTo(double mx, double my) {
        if (!pressing) return;
        if (!moved && Math.hypot(mx - startX, my - startY) > 3) moved = true;
        if (moved) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            int[] r = rect();
            int x = clamp((int) mx - offX, 0, Math.max(0, sw - r[2]));
            int y = clamp((int) my - offY, 0, Math.max(0, sh - H));
            HoennConfig cfg = HoennConfig.get();
            cfg.pillXr = x / (float) sw;
            cfg.pillYr = y / (float) sh;
        }
    }

    /** Botón soltado. Si no hubo arrastre es un clic: maximiza. @return true si se consume. */
    public static boolean release() {
        if (!pressing) return false;
        pressing = false;
        if (moved) {
            moved = false;
            HoennConfig.get().save();
        } else {
            restore();
        }
        return true;
    }

    public static void cancel() {
        pressing = false;
        moved = false;
    }

    /** Cierra la pastilla del todo (el panel se abre de nuevo con la tecla). */
    public static void dismiss() {
        HoennConfig cfg = HoennConfig.get();
        cfg.minimized = false;
        cfg.save();
        snoozed = true;
    }

    // ---- Dibujo -------------------------------------------------------------------------

    /** Se dibuja en el HUD cuando no hay ninguna pantalla abierta. */
    public static void renderHud(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!shown() || mc.options.hudHidden || mc.currentScreen != null) return;
        draw(c, false);
    }

    /** Se dibuja encima de cualquier otra pantalla (chat, inventario...). */
    public static void renderOnScreen(DrawContext c, int mx, int my) {
        if (!shown()) return;
        draw(c, pressing || hit(mx, my));
    }

    private static void draw(DrawContext c, boolean hover) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        HoennConfig cfg = HoennConfig.get();
        Theme th = Theme.byId(cfg.theme);
        int unread = HoennAscensionClient.STORE.unread("all");
        int[] r = rect();
        int x = r[0], y = r[1], w = r[2];

        // Aparece con un pequeño "pop" y late suavemente cuando hay mensajes sin leer
        float pulse = unread > 0 ? 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 250.0) : 1f;
        Gfx.panel(c, x, y, w, H, th, Math.min(1f, cfg.menuOpacity + 0.3f) * pulse);
        if (hover) c.fill(x + 1, y + 1, x + w - 1, y + H - 1, Gfx.alpha(th.text, 0.2f));

        Art.draw(c, Art.ICON, x + 6, y + (H - Art.ICON_H) / 2, Art.ICON_W, Art.ICON_H,
                Art.ICON_W, Art.ICON_H, 1f);

        if (unread > 0) {
            String b = badge(unread);
            int bw = tr.getWidth(b) + 6;
            int bx = x + w - bw - 3;
            c.fill(bx, y + 4, bx + bw, y + H - 4, Gfx.alpha(th.edgeC, 0.95f));
            c.drawText(tr, b, bx + 3, y + 7, 0xFFFFFFFF, false);
        }
    }

    /** Maximiza: abre el panel completo con animación desde la pastilla. */
    public static void restore() {
        snoozed = false;
        HoennConfig.get().minimized = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.setScreen(new CommsScreen(true));
    }
}