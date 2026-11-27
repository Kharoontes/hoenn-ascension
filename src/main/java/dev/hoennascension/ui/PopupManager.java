package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.core.Commands;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Notificaciones apiladas arriba a la derecha. Las que traen acciones (p. ej. /tpa) tienen botones
 * Aceptar / Rechazar: se pulsan con el ratón (con un chat o inventario abierto) o con las teclas Y / N,
 * y desaparecen solas pasado un tiempo (se ve una barra que se consume).
 */
public final class PopupManager {
    private static final int MAX_W = 196;
    private static final int MAX_VISIBLE = 4;

    private static final class Popup {
        String tag;
        String icon;
        String title;
        String body;
        String accept;
        String deny;
        /** Jugador mencionado en una solicitud (se muestra su cara). */
        String face;
        int rgb;
        long born;
        long life;
        // Última posición dibujada (para detectar clics)
        boolean drawn;
        int rx, ry, rw, rh;
        int ax, ay, aw, ah;
        int dx, dy, dw, dh;

        boolean actionable() {
            return accept != null && !accept.isBlank();
        }
    }

    private final List<Popup> popups = new ArrayList<>();

    public void push(String title, String body, ChannelDef ch) {
        HoennConfig cfg = HoennConfig.get();
        Popup p = new Popup();
        p.title = title;
        p.body = body.length() > 140 ? body.substring(0, 137) + "..." : body;
        p.rgb = ch.rgb();
        p.icon = ch.iconName();
        p.born = System.currentTimeMillis();
        if (ch.isActionable()) {
            p.accept = ch.acceptCommand;
            p.deny = ch.denyCommand;
            p.face = Faces.mentioned(body);
            p.life = cfg.actionPopupSeconds * 1000L;
        } else {
            p.life = cfg.popupSeconds * 1000L;
        }
        popups.add(p);
        while (popups.size() > 8) popups.remove(0);
    }

    /** Aviso simple con etiqueta: uno nuevo con la misma etiqueta sustituye al anterior. */
    public void notice(String tag, String title, String body, int rgb, int seconds) {
        notice(tag, tag, title, body, rgb, seconds);
    }

    /** Aviso con etiqueta e icono propio (el nombre de un icono de Icons). */
    public void notice(String tag, String icon, String title, String body, int rgb, int seconds) {
        popups.removeIf(p -> tag.equals(p.tag));
        Popup p = new Popup();
        p.tag = tag;
        p.icon = icon;
        p.title = title;
        p.body = body.length() > 140 ? body.substring(0, 137) + "..." : body;
        p.rgb = rgb;
        p.born = System.currentTimeMillis();
        p.life = seconds * 1000L;
        popups.add(p);
    }

    public void clear() {
        popups.clear();
    }

    /** Se llama cada tick: caducidad y teclas de acción (Y / N). */
    public void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        popups.removeIf(p -> now - p.born > p.life);
        while (HoennAscensionClient.ACCEPT.wasPressed()) act(true);
        while (HoennAscensionClient.DENY.wasPressed()) act(false);
    }

    private void run(Popup p, boolean accept) {
        popups.remove(p);
        String cmd = accept ? p.accept : p.deny;
        if (cmd != null && !cmd.isBlank()) Commands.run(cmd);
    }

    /** Acepta o rechaza la solicitud con acciones más reciente. @return true si había alguna. */
    public boolean act(boolean accept) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            if (popups.get(i).actionable()) {
                run(popups.get(i), accept);
                return true;
            }
        }
        return false;
    }

    /** Clic del ratón sobre una notificación: botones Aceptar / Rechazar, o descartar un aviso simple. */
    public boolean click(double mx, double my) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.get(i);
            if (!p.drawn || !Gfx.inside(mx, my, p.rx, p.ry, p.rw, p.rh)) continue;
            if (p.actionable()) {
                if (Gfx.inside(mx, my, p.ax, p.ay, p.aw, p.ah)) run(p, true);
                else if (Gfx.inside(mx, my, p.dx, p.dy, p.dw, p.dh)) run(p, false);
            } else {
                popups.remove(p);
            }
            return true;
        }
        return false;
    }

    /** Dibujo desde el HUD: cuando el panel está abierto, lo dibuja él mismo encima de su fondo. */
    public void render(DrawContext c) {
        if (MinecraftClient.getInstance().currentScreen instanceof CommsScreen) return;
        draw(c);
    }

    public void renderOverScreen(DrawContext c) {
        draw(c);
    }

    private void draw(DrawContext c) {
        for (Popup p : popups) p.drawn = false;
        if (popups.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        HoennConfig cfg = HoennConfig.get();
        if (!cfg.enabled || mc.options.hudHidden) return;
        Theme th = Theme.byId(cfg.theme);
        TextRenderer tr = mc.textRenderer;
        int sw = c.getScaledWindowWidth();
        int W = Math.max(100, Math.min(MAX_W, sw - 12));
        long now = System.currentTimeMillis();
        double mx = mc.mouse.getX() * sw / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * c.getScaledWindowHeight() / (double) mc.getWindow().getHeight();

        int start = Math.max(0, popups.size() - MAX_VISIBLE);
        int y = 6;
        for (int i = start; i < popups.size(); i++) {
            Popup p = popups.get(i);
            long age = now - p.born;
            long left = p.life - age;

            float slide = Math.min(1f, age / 250f);
            slide = 1f - (1f - slide) * (1f - slide);
            float fade = left < 300 ? Math.max(0f, left / 300f) : 1f;
            int x = sw - W - 6 + (int) ((1f - slide) * (W + 12));

            List<OrderedText> lines = tr.wrapLines(Text.literal(p.body), W - 18);
            int shown = Math.min(2, lines.size());
            int h = 20 + shown * 10 + (p.actionable() ? 18 : 0) + 3;

            Gfx.panel(c, x, y, W, h, th, Math.min(1f, cfg.menuOpacity + 0.2f) * fade);
            c.fill(x + 1, y + 1, x + 4, y + h - 1, Gfx.rgb(p.rgb, fade));
            if (p.face != null) {
                Faces.draw(c, p.face, x + 6, y + 3, 12);
            } else if (!Icons.draw(c, p.icon, x + 6, y + 3, 12, fade)) {
                Gfx.diamond(c, x + 12, y + 8, 3, Gfx.rgb(p.rgb, fade));
            }
            c.drawText(tr, tr.trimToWidth(p.title, W - 30), x + 22, y + 5, Gfx.alpha(th.text, fade), !th.light);

            int ty = y + 16;
            for (int l = 0; l < shown; l++) {
                c.drawText(tr, lines.get(l), x + 10, ty, Gfx.alpha(th.text, fade), false);
                ty += 10;
            }

            if (p.actionable()) {
                int bw = (W - 24) / 2;
                int by = ty + 2;
                Text acc = Text.literal("[").append(HoennAscensionClient.ACCEPT.getBoundKeyLocalizedText())
                        .append("] ").append(Text.translatable("hoennascension.popup.accept"));
                Text den = Text.literal("[").append(HoennAscensionClient.DENY.getBoundKeyLocalizedText())
                        .append("] ").append(Text.translatable("hoennascension.popup.deny"));
                p.ax = x + 10;
                p.ay = by;
                p.aw = bw;
                p.ah = 12;
                p.dx = x + 14 + bw;
                p.dy = by;
                p.dw = bw;
                p.dh = 12;
                boolean hAcc = Gfx.inside(mx, my, p.ax, p.ay, p.aw, p.ah);
                boolean hDen = Gfx.inside(mx, my, p.dx, p.dy, p.dw, p.dh);
                c.fillGradient(p.ax, p.ay, p.ax + p.aw, p.ay + p.ah,
                        Gfx.alpha(hAcc ? 0xFF35C46A : 0xFF22984F, fade), Gfx.alpha(hAcc ? 0xFF1E8F4A : 0xFF146B36, fade));
                c.fillGradient(p.dx, p.dy, p.dx + p.dw, p.dy + p.dh,
                        Gfx.alpha(hDen ? 0xFFE0584F : 0xFFC0392B, fade), Gfx.alpha(hDen ? 0xFFA8362D : 0xFF8E2A20, fade));
                String sa = tr.trimToWidth(acc.getString(), bw - 4);
                String sd = tr.trimToWidth(den.getString(), bw - 4);
                c.drawText(tr, sa, p.ax + bw / 2 - tr.getWidth(sa) / 2, p.ay + 2, Gfx.alpha(0xFFFFFFFF, fade), true);
                c.drawText(tr, sd, p.dx + bw / 2 - tr.getWidth(sd) / 2, p.dy + 2, Gfx.alpha(0xFFFFFFFF, fade), true);

                // Barra de tiempo restante
                int full = W - 2;
                int cur = (int) (full * Math.max(0f, Math.min(1f, left / (float) p.life)));
                c.fill(x + 1, y + h - 3, x + 1 + full, y + h - 1, Gfx.alpha(0x55FFFFFF, fade));
                c.fill(x + 1, y + h - 3, x + 1 + cur, y + h - 1, Gfx.rgb(p.rgb, fade));
            }

            p.rx = x;
            p.ry = y;
            p.rw = W;
            p.rh = h;
            p.drawn = true;
            y += h + 4;
        }
    }
}