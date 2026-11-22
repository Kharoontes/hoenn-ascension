package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Notificaciones apiladas arriba a la derecha, con acciones aceptar/rechazar opcionales. */
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
        int rgb;
        long born;
        long life;

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

    /** Se llama cada tick: caducidad y teclas de acción. */
    public void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        popups.removeIf(p -> now - p.born > p.life);

        while (HoennAscensionClient.ACCEPT.wasPressed()) runAction(mc, true);
        while (HoennAscensionClient.DENY.wasPressed()) runAction(mc, false);
    }

    private void runAction(MinecraftClient mc, boolean accept) {
        Popup target = null;
        for (int i = popups.size() - 1; i >= 0; i--) {
            if (popups.get(i).actionable()) {
                target = popups.get(i);
                break;
            }
        }
        if (target == null || mc.player == null) return;
        String cmd = accept ? target.accept : target.deny;
        popups.remove(target);
        if (cmd == null || cmd.isBlank()) return;
        mc.player.networkHandler.sendChatCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
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
        if (popups.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        HoennConfig cfg = HoennConfig.get();
        Theme th = Theme.byId(cfg.theme);
        TextRenderer tr = mc.textRenderer;
        int sw = c.getScaledWindowWidth();
        int W = Math.max(100, Math.min(MAX_W, sw - 12));
        long now = System.currentTimeMillis();

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
            int h = 20 + shown * 10 + (p.actionable() ? 12 : 0);

            Gfx.panel(c, x, y, W, h, th, Math.min(1f, cfg.menuOpacity + 0.2f) * fade);
            // Barra lateral del color del canal
            c.fill(x + 1, y + 1, x + 4, y + h - 1, Gfx.rgb(p.rgb, fade));
            if (!Icons.draw(c, p.icon, x + 6, y + 3, 12, fade)) Gfx.diamond(c, x + 12, y + 8, 3, Gfx.rgb(p.rgb, fade));
            c.drawText(tr, p.title, x + 22, y + 5, Gfx.alpha(th.text, fade), !th.light);

            int ty = y + 16;
            for (int l = 0; l < shown; l++) {
                c.drawText(tr, lines.get(l), x + 10, ty, Gfx.alpha(th.text, fade), false);
                ty += 10;
            }
            if (p.actionable()) {
                Text acc = Text.literal("[").append(HoennAscensionClient.ACCEPT.getBoundKeyLocalizedText())
                        .append("] ").append(Text.translatable("hoennascension.popup.accept"));
                Text den = Text.literal("[").append(HoennAscensionClient.DENY.getBoundKeyLocalizedText())
                        .append("] ").append(Text.translatable("hoennascension.popup.deny"));
                c.drawText(tr, acc, x + 10, ty + 1, Gfx.alpha(0xFF22B25F, fade), false);
                c.drawText(tr, den, x + 20 + tr.getWidth(acc), ty + 1, Gfx.alpha(0xFFE0463F, fade), false);
            }
            y += h + 4;
        }
    }
}