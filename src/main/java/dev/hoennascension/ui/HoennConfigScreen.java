package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Ajustes del mod: categorías en un menú lateral y las opciones de cada una a la derecha, con el estilo del
 * tema Negro (Abyss). Arriba a la derecha está el interruptor general para activar o desactivar el mod.
 */
public final class HoennConfigScreen extends Screen {
    private static final Theme T = Theme.ABISMO;
    private static final String[] ANCHORS = {"top_left", "top_right", "bottom_left", "bottom_right"};
    private static final int HEADER_H = 28;
    private static final int PAD = 8;

    // ---- Opciones ------------------------------------------------------------------------

    private abstract static class Opt {
        final String key;
        /** Zona del control en la última pasada de dibujo (la usa el arrastre de deslizadores). */
        int rx, rw;

        Opt(String key) {
            this.key = key;
        }

        String label() {
            return Text.translatable("hoennascension.set." + key).getString();
        }

        int height(TextRenderer tr, int w) {
            return 24;
        }

        abstract void render(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my);

        boolean click(double mx, double my, int x, int y, int w) {
            return false;
        }

        void drag(double mx) {}
    }

    private static int controlW(int w) {
        return Math.max(60, Math.min(150, w / 2 - 12));
    }

    private static void rowLabel(DrawContext c, TextRenderer tr, Opt o, int x, int y, int w, int reserved) {
        c.drawText(tr, tr.trimToWidth(o.label(), Math.max(20, w - reserved - 2 * PAD)), x + PAD, y + 8, T.text, true);
    }

    private static final class Toggle extends Opt {
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
            super(key);
            this.get = get;
            this.set = set;
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            rowLabel(c, tr, this, x, y, w, 40);
            boolean on = get.getAsBoolean();
            int tx = x + w - PAD - 30;
            c.fill(tx, y + 6, tx + 30, y + 18, on ? Gfx.alpha(T.edgeB, 0.95f) : 0x66FFFFFF);
            int kx = on ? tx + 19 : tx + 1;
            c.fill(kx, y + 7, kx + 10, y + 17, 0xFFFFFFFF);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            set.accept(!get.getAsBoolean());
            return true;
        }
    }

    private static final class Slider extends Opt {
        final double min, max, step;
        final DoubleSupplier get;
        final DoubleConsumer set;
        final DoubleFunction<String> fmt;

        Slider(String key, double min, double max, double step, DoubleSupplier get, DoubleConsumer set,
               DoubleFunction<String> fmt) {
            super(key);
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.fmt = fmt;
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            int sw = controlW(w);
            rowLabel(c, tr, this, x, y, w, sw + PAD);
            rx = x + w - PAD - sw;
            rw = sw;
            double v = Math.max(min, Math.min(max, get.getAsDouble()));
            float t = (float) ((v - min) / (max - min));
            c.fill(rx, y + 4, rx + sw, y + 20, T.inputBg);
            int fill = Math.round(sw * t);
            if (fill > 0) Gfx.hBand(c, rx, y + 4, rx + fill, y + 20, T, 0.75f, 0f, t);
            c.fill(rx + Math.max(0, fill - 1), y + 3, rx + Math.min(sw, fill + 2), y + 21, 0xFFFFFFFF);
            String s = fmt.apply(v);
            c.drawText(tr, s, rx + sw / 2 - tr.getWidth(s) / 2, y + 8, 0xFFFFFFFF, true);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (mx < rx || mx > rx + rw) return false;
            drag(mx);
            return true;
        }

        @Override
        void drag(double mx) {
            double t = Math.max(0, Math.min(1, (mx - rx) / Math.max(1, rw)));
            double v = min + t * (max - min);
            if (step > 0) v = Math.round(v / step) * step;
            set.accept(Math.max(min, Math.min(max, v)));
        }
    }

    private static final class Cycle extends Opt {
        final Supplier<String> value;
        final Runnable next;

        Cycle(String key, Supplier<String> value, Runnable next) {
            super(key);
            this.value = value;
            this.next = next;
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            int sw = controlW(w);
            rowLabel(c, tr, this, x, y, w, sw + PAD);
            rx = x + w - PAD - sw;
            rw = sw;
            c.fillGradient(rx, y + 4, rx + sw, y + 20, Gfx.alpha(T.edgeA, 0.55f), Gfx.alpha(T.edgeC, 0.55f));
            String s = tr.trimToWidth(value.get(), sw - 8);
            c.drawText(tr, s, rx + sw / 2 - tr.getWidth(s) / 2, y + 8, 0xFFFFFFFF, true);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            next.run();
            return true;
        }
    }

    private static final class Action extends Opt {
        final String buttonKey;
        final Runnable run;

        Action(String key, String buttonKey, Runnable run) {
            super(key);
            this.buttonKey = buttonKey;
            this.run = run;
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            int sw = controlW(w);
            rowLabel(c, tr, this, x, y, w, sw + PAD);
            rx = x + w - PAD - sw;
            rw = sw;
            boolean hover = Gfx.inside(mx, my, rx, y + 4, sw, 16);
            c.fillGradient(rx, y + 4, rx + sw, y + 20,
                    hover ? 0xFFD7263D : 0xFF9C1D2E, hover ? 0xFF9C1D2E : 0xFF6B121F);
            String s = tr.trimToWidth(Text.translatable("hoennascension.set." + buttonKey).getString(), sw - 8);
            c.drawText(tr, s, rx + sw / 2 - tr.getWidth(s) / 2, y + 8, 0xFFFFFFFF, true);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (mx < rx || mx > rx + rw) return false;
            run.run();
            return true;
        }
    }

    private static final class TextOpt extends Opt {
        final TextFieldWidget field;

        TextOpt(String key, TextFieldWidget field) {
            super(key);
            this.field = field;
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            int sw = Math.max(80, Math.min(220, w / 2));
            rowLabel(c, tr, this, x, y, w, sw + PAD);
            rx = x + w - PAD - sw;
            rw = sw;
            c.fill(rx, y + 4, rx + sw, y + 20, T.inputBg);
            Gfx.hBand(c, rx, y + 4, rx + sw, y + 5, T, 0.8f, 0f, 1f);
        }
    }

    private static final class Info extends Opt {
        Info(String key) {
            super(key);
        }

        @Override
        int height(TextRenderer tr, int w) {
            return tr.wrapLines(Text.translatable("hoennascension.set." + key), w - 2 * PAD).size() * 10 + 10;
        }

        @Override
        void render(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            int ty = y + 5;
            for (OrderedText line : tr.wrapLines(Text.translatable("hoennascension.set." + key), w - 2 * PAD)) {
                c.drawText(tr, line, x + PAD, ty, T.muted, false);
                ty += 10;
            }
        }
    }

    private record Category(String id, int rgb, List<Opt> opts) {}

    // ---- Estado --------------------------------------------------------------------------

    private final Screen parent;
    private final List<Category> cats = new ArrayList<>();
    private final List<TextFieldWidget> textFields = new ArrayList<>();
    private int active = 0;
    private int scroll = 0;
    private Opt dragging;

    private int px, py, pw, ph, sbW, listX, listW, listTop, listBottom;

    public HoennConfigScreen(Screen parent) {
        super(Text.translatable("hoennascension.config.title"));
        this.parent = parent;
    }

    // ---- Construcción de las categorías --------------------------------------------------

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String num(double v) {
        return String.valueOf((int) Math.round(v));
    }

    private static String secs(double v) {
        return (int) Math.round(v) + " s";
    }

    private static String anchorName(String a) {
        return Text.translatable("hoennascension.anchor." + a).getString();
    }

    private static String nextAnchor(String current) {
        for (int i = 0; i < ANCHORS.length; i++) {
            if (ANCHORS[i].equalsIgnoreCase(current)) return ANCHORS[(i + 1) % ANCHORS.length];
        }
        return ANCHORS[0];
    }

    /** Canal de spawns (o uno vacío si se borró de la config, para no fallar). */
    private static ChannelDef spawns() {
        ChannelDef c = HoennConfig.get().channel("spawns");
        return c != null ? c : new ChannelDef();
    }

    private Opt text(String key, Supplier<String> get, Consumer<String> set, int max) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.empty());
        f.setMaxLength(max);
        f.setDrawsBackground(false);
        f.setEditableColor(T.text);
        f.setText(get.get());
        f.setChangedListener(set::accept);
        f.visible = false;
        f.active = false;
        addDrawableChild(f);
        textFields.add(f);
        return new TextOpt(key, f);
    }

    private void buildCategories() {
        HoennConfig cfg = HoennConfig.get();
        cats.clear();
        textFields.clear();

        cats.add(new Category("general", 0x8FD8FF, List.of(
                new Toggle("enabled", () -> cfg.enabled, v -> cfg.enabled = v),
                new Info("info.general"),
                new Action("resetLayout", "btn.reset", () -> {
                    cfg.resetLayout();
                    cfg.save();
                }),
                new Action("clearHistory", "btn.clear", () -> HoennAscensionClient.STORE.clear()))));

        cats.add(new Category("appearance", 0xA481DE, List.of(
                new Cycle("theme", () -> Theme.byId(cfg.theme).displayName(),
                        () -> cfg.theme = Theme.byId(cfg.theme).next().id),
                new Slider("menuOpacity", 0.15, 1.0, 0.05, () -> cfg.menuOpacity, v -> cfg.menuOpacity = (float) v,
                        HoennConfigScreen::pct),
                new Toggle("showLogo", () -> cfg.showLogo, v -> cfg.showLogo = v),
                new Toggle("showBanner", () -> cfg.showBanner, v -> cfg.showBanner = v),
                new Toggle("sidebar", () -> cfg.sidebarCollapsed, v -> cfg.sidebarCollapsed = v),
                new Toggle("timestamps", () -> cfg.timestamps, v -> cfg.timestamps = v))));

        cats.add(new Category("chat", 0x47B5F8, List.of(
                new Info("info.chat"),
                new Toggle("mentions", () -> cfg.mentionHighlight, v -> cfg.mentionHighlight = v),
                new Toggle("hideMega", () -> cfg.hideMegaNotice, v -> cfg.hideMegaNotice = v),
                new Toggle("hideAll", () -> cfg.hideAllFromVanilla, v -> cfg.hideAllFromVanilla = v),
                new Toggle("hideFeedback", () -> cfg.hideCommandFeedback, v -> cfg.hideCommandFeedback = v),
                new Slider("feedbackSeconds", 1, 10, 1, () -> cfg.commandFeedbackSeconds,
                        v -> cfg.commandFeedbackSeconds = (int) v, HoennConfigScreen::secs),
                new Slider("history", 20, 2000, 20, () -> cfg.historyPerChannel,
                        v -> cfg.historyPerChannel = (int) v, HoennConfigScreen::num))));

        cats.add(new Category("hud", 0x5CFFE0, List.of(
                new Toggle("hudPinned", () -> cfg.hudPinned, v -> cfg.hudPinned = v),
                new Cycle("hudAnchor", () -> anchorName(cfg.hudAnchor), () -> cfg.hudAnchor = nextAnchor(cfg.hudAnchor)),
                new Slider("hudLines", 1, 20, 1, () -> cfg.hudLines, v -> cfg.hudLines = (int) v, HoennConfigScreen::num),
                new Slider("hudWidth", 120, 600, 10, () -> cfg.hudWidth, v -> cfg.hudWidth = (int) v, HoennConfigScreen::num),
                new Slider("hudOpacity", 0.05, 1.0, 0.05, () -> cfg.hudOpacity, v -> cfg.hudOpacity = (float) v,
                        HoennConfigScreen::pct),
                new Slider("hudFade", 2, 60, 1, () -> cfg.hudFadeSeconds, v -> cfg.hudFadeSeconds = (int) v,
                        HoennConfigScreen::secs),
                new Toggle("hudAlways", () -> cfg.hudAlwaysVisible, v -> cfg.hudAlwaysVisible = v))));

        cats.add(new Category("notifications", 0xFFC247, List.of(
                new Toggle("popups", () -> cfg.popups, v -> cfg.popups = v),
                new Slider("popupSeconds", 2, 30, 1, () -> cfg.popupSeconds, v -> cfg.popupSeconds = (int) v,
                        HoennConfigScreen::secs),
                new Slider("actionSeconds", 5, 60, 1, () -> cfg.actionPopupSeconds, v -> cfg.actionPopupSeconds = (int) v,
                        HoennConfigScreen::secs),
                new Toggle("wtNotify", () -> cfg.wondertradeNotify, v -> cfg.wondertradeNotify = v),
                new Info("info.keys"))));

        cats.add(new Category("window", 0xEC7D8B, List.of(
                new Slider("minW", 240, 500, 10, () -> cfg.windowMinWidth, v -> cfg.windowMinWidth = (int) v, HoennConfigScreen::num),
                new Slider("minH", 140, 400, 10, () -> cfg.windowMinHeight, v -> cfg.windowMinHeight = (int) v, HoennConfigScreen::num),
                new Slider("maxW", 400, 1200, 20, () -> cfg.windowMaxWidth, v -> cfg.windowMaxWidth = (int) v, HoennConfigScreen::num),
                new Slider("maxH", 300, 900, 20, () -> cfg.windowMaxHeight, v -> cfg.windowMaxHeight = (int) v, HoennConfigScreen::num),
                new Toggle("pillAlways", () -> cfg.pillAlwaysVisible, v -> cfg.pillAlwaysVisible = v),
                new Cycle("pillAnchor", () -> anchorName(cfg.minimizedAnchor), () -> {
                    cfg.minimizedAnchor = nextAnchor(cfg.minimizedAnchor);
                    cfg.pillXr = -1f;
                    cfg.pillYr = -1f;
                }))));

        cats.add(new Category("server", 0x35C46A, List.of(
                new Info("info.server"),
                text("clanTag", () -> cfg.server.clanTag, v -> cfg.server.clanTag = v, 32),
                text("tpaCmd", () -> cfg.server.tpaCommand, v -> cfg.server.tpaCommand = v, 64),
                new Slider("wtCooldown", 1, 240, 1, () -> cfg.server.wondertradeCooldownMinutes,
                        v -> cfg.server.wondertradeCooldownMinutes = (int) v, v -> (int) Math.round(v) + " min"),
                new Slider("nearBlocks", 32, 1024, 16, () -> spawns().nearbyBlocks,
                        v -> spawns().nearbyBlocks = (int) v, v -> (int) Math.round(v) + " m"),
                new Toggle("nearUnknown", () -> spawns().nearbyShowUnknown, v -> spawns().nearbyShowUnknown = v))));

        cats.add(new Category("discord", 0x7B8CFF, List.of(
                new Info("info.discord"),
                new Toggle("discord", () -> cfg.discordRelay, v -> cfg.discordRelay = v),
                text("webhook", () -> cfg.discordWebhookUrl, v -> cfg.discordWebhookUrl = v.trim(), 200))));
    }

    @Override
    protected void init() {
        buildCategories();
        pw = Math.max(Math.min(width - 8, 300), Math.min(width - 24, 560));
        ph = Math.max(Math.min(height - 8, 200), Math.min(height - 24, 340));
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sbW = pw >= 400 ? 112 : 30;
        listX = px + sbW + 1;
        listW = pw - sbW - 2;
        listTop = py + HEADER_H + 4;
        listBottom = py + ph - 6;
        if (active >= cats.size()) active = 0;
    }

    // ---- Posición de las filas -----------------------------------------------------------

    private List<Opt> opts() {
        return cats.get(active).opts();
    }

    private int contentHeight() {
        int h = 4;
        for (Opt o : opts()) h += o.height(textRenderer, listW);
        return h;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - (listBottom - listTop))));
    }

    // ---- Dibujo --------------------------------------------------------------------------

    private int toggleX() { return px + pw - 14 - 8 - 4 - 120; }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        // Intencionalmente vacío: el fondo se dibuja en render().
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        HoennConfig cfg = HoennConfig.get();
        TextRenderer tr = textRenderer;
        c.fill(0, 0, width, height, 0x88000000);
        Gfx.panel(c, px, py, pw, ph, T, 0.94f);

        // Cabecera: título e interruptor general
        c.drawText(tr, tr.trimToWidth(title.getString(), Math.max(40, pw - 220)), px + 12, py + 10, T.text, true);
        boolean hoverClose = Gfx.inside(mx, my, px + pw - 20, py + 7, 14, 14);
        c.drawText(tr, "x", px + pw - 16, py + 10, hoverClose ? 0xFFFF6A6A : T.muted, false);
        String state = Text.translatable(cfg.enabled ? "hoennascension.set.enabled.on" : "hoennascension.set.enabled.off").getString();
        int sw = tr.getWidth(state) + 40;
        int sx = px + pw - 26 - sw;
        c.fill(sx, py + 6, sx + sw, py + 22, Gfx.alpha(cfg.enabled ? T.edgeB : 0xFF6B121F, 0.55f));
        c.drawText(tr, state, sx + 6, py + 10, 0xFFFFFFFF, true);
        int kx = cfg.enabled ? sx + sw - 16 : sx + sw - 31;
        c.fill(kx, py + 8, kx + 12, py + 20, 0xFFFFFFFF);
        Gfx.hBand(c, px + 1, py + HEADER_H - 1, px + pw - 1, py + HEADER_H, T, 0.6f, 0f, 1f);

        // Menú lateral de categorías
        c.fill(px + 1, py + HEADER_H, px + 1 + sbW, py + ph - 1, 0x22000000);
        c.fillGradient(px + 1 + sbW, py + HEADER_H, px + 2 + sbW, py + ph - 1,
                Gfx.alpha(T.edgeA, 0.8f), Gfx.alpha(T.edgeC, 0.8f));
        String tip = null;
        for (int i = 0; i < cats.size(); i++) {
            Category cat = cats.get(i);
            int y = py + HEADER_H + 6 + i * 20;
            boolean sel = i == active;
            boolean hover = Gfx.inside(mx, my, px + 2, y, sbW - 2, 18);
            if (sel) {
                c.fillGradient(px + 2, y, px + sbW, y + 18, Gfx.rgb(cat.rgb(), 0.32f), Gfx.rgb(cat.rgb(), 0.06f));
                c.fill(px + 2, y, px + 4, y + 18, Gfx.rgb(cat.rgb(), 1f));
            } else if (hover) {
                c.fill(px + 2, y, px + sbW, y + 18, Gfx.alpha(T.text, 0.12f));
            }
            Gfx.diamond(c, px + 13, y + 9, 3, Gfx.rgb(cat.rgb(), sel ? 1f : 0.75f));
            String name = Text.translatable("hoennascension.cat." + cat.id()).getString();
            if (sbW >= 64) c.drawText(tr, tr.trimToWidth(name, sbW - 26), px + 22, y + 5, sel ? T.text : T.muted, true);
            else if (hover) tip = name;
        }

        // Opciones de la categoría activa
        clampScroll();
        for (TextFieldWidget f : textFields) {
            f.visible = false;
            f.active = false;
        }
        c.enableScissor(listX, listTop, listX + listW, listBottom);
        int y = listTop - scroll + 4;
        for (Opt o : opts()) {
            int h = o.height(tr, listW);
            if (y + h > listTop && y < listBottom) {
                boolean hover = Gfx.inside(mx, my, listX, y, listW, h) && !(o instanceof Info);
                if (hover) c.fill(listX + 2, y, listX + listW - 2, y + h, Gfx.alpha(T.text, 0.07f));
                o.render(c, tr, listX, y, listW, mx, my);
                if (o instanceof TextOpt t && y >= listTop && y + h <= listBottom) {
                    t.field.visible = true;
                    t.field.active = true;
                    t.field.setX(t.rx + 4);
                    t.field.setY(y + 8);
                    t.field.setWidth(t.rw - 8);
                }
                c.fill(listX + 6, y + h - 1, listX + listW - 6, y + h, Gfx.alpha(T.text, 0.06f));
            }
            y += h;
        }
        c.disableScissor();

        // Barra de scroll
        int total = contentHeight();
        int view = listBottom - listTop;
        if (total > view) {
            int thumbH = Math.max(10, view * view / total);
            int thumbY = listTop + (int) ((view - thumbH) * (scroll / (float) (total - view)));
            c.fill(px + pw - 6, listTop, px + pw - 4, listBottom, Gfx.alpha(T.text, 0.15f));
            c.fillGradient(px + pw - 6, thumbY, px + pw - 4, thumbY + thumbH, Gfx.alpha(T.edgeA, 1f), Gfx.alpha(T.edgeC, 1f));
        }

        super.render(c, mx, my, delta);
        if (tip != null) c.drawTooltip(tr, Text.literal(tip), mx, my);
    }

    // ---- Entrada -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            HoennConfig cfg = HoennConfig.get();
            TextRenderer tr = textRenderer;
            if (Gfx.inside(mx, my, px + pw - 20, py + 7, 14, 14)) {
                close();
                return true;
            }
            String state = Text.translatable(cfg.enabled ? "hoennascension.set.enabled.on" : "hoennascension.set.enabled.off").getString();
            int sw = tr.getWidth(state) + 40;
            int sx = px + pw - 26 - sw;
            if (Gfx.inside(mx, my, sx, py + 6, sw, 16)) {
                cfg.enabled = !cfg.enabled;
                cfg.save();
                return true;
            }
            for (int i = 0; i < cats.size(); i++) {
                if (Gfx.inside(mx, my, px + 2, py + HEADER_H + 6 + i * 20, sbW - 2, 18)) {
                    active = i;
                    scroll = 0;
                    return true;
                }
            }
            if (Gfx.inside(mx, my, listX, listTop, listW, listBottom - listTop)) {
                int y = listTop - scroll + 4;
                for (Opt o : opts()) {
                    int h = o.height(tr, listW);
                    if (my >= y && my < y + h) {
                        if (o instanceof TextOpt) break; // el campo de texto lo gestiona la pantalla
                        if (o.click(mx, my, listX, y, listW)) {
                            if (o instanceof Slider) dragging = o;
                            HoennConfig.get().save();
                            return true;
                        }
                    }
                    y += h;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null && button == 0) {
            dragging.drag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            HoennConfig.get().save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (vertical != 0) {
            scroll -= (int) (vertical * 14);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        HoennConfig.get().save();
    }
}