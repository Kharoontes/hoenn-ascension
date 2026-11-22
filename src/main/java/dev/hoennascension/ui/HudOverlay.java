package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.core.ChatEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Mini panel anclado: últimas líneas del chat, con desvanecido por antigüedad. */
public final class HudOverlay {
    private HudOverlay() {}

    private record HudLine(OrderedText text, float alpha, int rgb) {}

    public static void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoennConfig cfg = HoennConfig.get();
        if (!cfg.hudPinned || mc.options.hudHidden || mc.currentScreen != null || mc.player == null) return;

        TextRenderer tr = mc.textRenderer;
        Theme th = Theme.byId(cfg.theme);
        long now = System.currentTimeMillis();
        long fadeMs = cfg.hudFadeSeconds * 1000L;
        int sw = c.getScaledWindowWidth();
        int sh = c.getScaledWindowHeight();
        int hudW = Math.max(100, Math.min(cfg.hudWidth, sw - 12));
        int wrapW = hudW - 14;

        List<ChatEntry> entries = HoennAscensionClient.STORE.view(cfg.hudChannel, "", cfg.hudLines);
        List<HudLine> lines = new ArrayList<>();
        for (ChatEntry e : entries) {
            float a = 1f;
            if (!cfg.hudAlwaysVisible) {
                long age = now - e.time;
                if (age > fadeMs) continue;
                long left = fadeMs - age;
                if (left < 2000) a = left / 2000f;
            }
            ChannelDef ch = cfg.channel(e.channel);
            int rgb = ch != null ? ch.rgb() : 0xFFFFFF;
            Text t = Text.empty()
                    .append(Text.literal("• ").setStyle(Style.EMPTY.withColor(rgb)))
                    .append(Text.literal(e.text));
            for (OrderedText ot : tr.wrapLines(t, wrapW)) lines.add(new HudLine(ot, a, rgb));
        }
        if (lines.isEmpty()) return;
        if (lines.size() > cfg.hudLines) lines = lines.subList(lines.size() - cfg.hudLines, lines.size());

        float maxA = 0f;
        for (HudLine l : lines) maxA = Math.max(maxA, l.alpha());

        int h = lines.size() * 10 + 8;
        int w = hudW;
        String anchor = cfg.hudAnchor.toLowerCase();
        int x = anchor.contains("right") ? sw - w - 6 : 6;
        int y = anchor.contains("bottom") ? sh - h - 50 : 6;

        float bg = Math.min(1f, cfg.hudOpacity * th.opacityMul) * maxA;
        c.fillGradient(x, y, x + w, y + h, Gfx.alpha(th.bgTop, bg), Gfx.alpha(th.bgBottom, bg));
        // Barra vertical con los tres tonos del tema
        c.fillGradient(x, y, x + 2, y + h / 2, Gfx.alpha(th.edgeA, maxA), Gfx.alpha(th.edgeB, maxA));
        c.fillGradient(x, y + h / 2, x + 2, y + h, Gfx.alpha(th.edgeB, maxA), Gfx.alpha(th.edgeC, maxA));

        int ty = y + 4;
        for (HudLine l : lines) {
            int col = Gfx.alpha(th.text, l.alpha());
            if ((col >>> 24) > 3) c.drawText(tr, l.text(), x + 7, ty, col, !th.light);
            ty += 10;
        }
    }
}