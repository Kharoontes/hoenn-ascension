package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/** Pantalla de ajustes (accesible desde Mod Menu y desde el engranaje del panel). */
public final class HoennConfigScreen extends Screen {
    private static final String[] ANCHORS = {"top_left", "top_right", "bottom_left", "bottom_right"};
    private static final int ROW_H = 24;

    private final Screen parent;
    private final List<ClickableWidget> entries = new ArrayList<>();
    private int scroll = 0;
    private int count = 0;
    private int colW;
    private int left;

    public HoennConfigScreen(Screen parent) {
        super(Text.translatable("hoennascension.config.title"));
        this.parent = parent;
    }

    private int top() { return 34; }
    private int bottom() { return height - 34; }

    private static Text label(String key, String value) {
        return Text.translatable(key).append(": " + value);
    }

    private static String onOff(boolean b) {
        return ScreenTexts.onOrOff(b).getString();
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

    private <T extends ClickableWidget> T add(T w) {
        w.setX(left + (count % 2) * (colW + 8));
        entries.add(w);
        addDrawableChild(w);
        count++;
        return w;
    }

    private void addButton(String key, Supplier<String> value, Runnable onClick) {
        ButtonWidget b = ButtonWidget.builder(label(key, value.get()), btn -> {
            onClick.run();
            btn.setMessage(label(key, value.get()));
        }).dimensions(0, 0, colW, 20).build();
        add(b);
    }

    private void addToggle(String key, BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        addButton(key, () -> onOff(get.getAsBoolean()), () -> set.accept(!get.getAsBoolean()));
    }

    private void addSlider(String key, double min, double max, double step, double current,
                           DoubleFunction<String> fmt, DoubleConsumer setter) {
        add(new OptSlider(colW, key, min, max, step, current, fmt, setter));
    }

    /** Interruptor por canal: ocultar sus mensajes del chat normal de Minecraft. */
    private void addChannelHide(ChannelDef ch) {
        Supplier<Text> txt = () -> Text.translatable("hoennascension.opt.hideChannel", ch.name)
                .append(": " + onOff(ch.hideFromVanilla));
        ButtonWidget b = ButtonWidget.builder(txt.get(), btn -> {
            ch.hideFromVanilla = !ch.hideFromVanilla;
            btn.setMessage(txt.get());
        }).dimensions(0, 0, colW, 20).build();
        add(b);
    }

    @Override
    protected void init() {
        HoennConfig cfg = HoennConfig.get();
        entries.clear();
        count = 0;
        colW = Math.max(100, Math.min(200, (width - 24) / 2));
        left = (width - (colW * 2 + 8)) / 2;

        DoubleFunction<String> pct = v -> Math.round(v * 100) + "%";
        DoubleFunction<String> num = v -> String.valueOf((int) Math.round(v));
        DoubleFunction<String> secs = v -> (int) Math.round(v) + " s";

        addButton("hoennascension.opt.theme", () -> Theme.byId(cfg.theme).label,
                () -> cfg.theme = Theme.byId(cfg.theme).next().id);
        addSlider("hoennascension.opt.menuOpacity", 0.15, 1.0, 0.05, cfg.menuOpacity, pct,
                v -> cfg.menuOpacity = (float) v);
        addToggle("hoennascension.opt.timestamps", () -> cfg.timestamps, v -> cfg.timestamps = v);
        addToggle("hoennascension.opt.showLogo", () -> cfg.showLogo, v -> cfg.showLogo = v);
        addToggle("hoennascension.opt.showBanner", () -> cfg.showBanner, v -> cfg.showBanner = v);
        addToggle("hoennascension.opt.sidebar", () -> cfg.sidebarCollapsed, v -> cfg.sidebarCollapsed = v);

        addToggle("hoennascension.opt.hudPinned", () -> cfg.hudPinned, v -> cfg.hudPinned = v);
        addButton("hoennascension.opt.hudAnchor", () -> anchorName(cfg.hudAnchor),
                () -> cfg.hudAnchor = nextAnchor(cfg.hudAnchor));
        addSlider("hoennascension.opt.hudLines", 1, 20, 1, cfg.hudLines, num, v -> cfg.hudLines = (int) v);
        addSlider("hoennascension.opt.hudWidth", 120, 600, 10, cfg.hudWidth, num, v -> cfg.hudWidth = (int) v);
        addSlider("hoennascension.opt.hudOpacity", 0.05, 1.0, 0.05, cfg.hudOpacity, pct,
                v -> cfg.hudOpacity = (float) v);
        addSlider("hoennascension.opt.hudFade", 2, 60, 1, cfg.hudFadeSeconds, secs,
                v -> cfg.hudFadeSeconds = (int) v);
        addToggle("hoennascension.opt.hudAlways", () -> cfg.hudAlwaysVisible, v -> cfg.hudAlwaysVisible = v);

        addToggle("hoennascension.opt.popups", () -> cfg.popups, v -> cfg.popups = v);
        addSlider("hoennascension.opt.popupSeconds", 2, 30, 1, cfg.popupSeconds, secs,
                v -> cfg.popupSeconds = (int) v);
        addSlider("hoennascension.opt.actionSeconds", 5, 60, 1, cfg.actionPopupSeconds, secs,
                v -> cfg.actionPopupSeconds = (int) v);
        addToggle("hoennascension.opt.mentions", () -> cfg.mentionHighlight, v -> cfg.mentionHighlight = v);
        addSlider("hoennascension.opt.history", 20, 2000, 20, cfg.historyPerChannel, num,
                v -> cfg.historyPerChannel = (int) v);

        // Chat normal de Minecraft
        addToggle("hoennascension.opt.hideAll", () -> cfg.hideAllFromVanilla, v -> cfg.hideAllFromVanilla = v);
        addToggle("hoennascension.opt.hideFeedback", () -> cfg.hideCommandFeedback, v -> cfg.hideCommandFeedback = v);
        addSlider("hoennascension.opt.feedbackSeconds", 1, 10, 1, cfg.commandFeedbackSeconds, secs,
                v -> cfg.commandFeedbackSeconds = (int) v);
        for (ChannelDef ch : cfg.channels) addChannelHide(ch);

        addSlider("hoennascension.opt.minWidth", 240, 500, 10, cfg.windowMinWidth, num, v -> cfg.windowMinWidth = (int) v);
        addSlider("hoennascension.opt.minHeight", 140, 400, 10, cfg.windowMinHeight, num, v -> cfg.windowMinHeight = (int) v);
        addSlider("hoennascension.opt.maxWidth", 400, 1200, 20, cfg.windowMaxWidth, num, v -> cfg.windowMaxWidth = (int) v);
        addSlider("hoennascension.opt.maxHeight", 300, 900, 20, cfg.windowMaxHeight, num, v -> cfg.windowMaxHeight = (int) v);

        addToggle("hoennascension.opt.pillAlways", () -> cfg.pillAlwaysVisible, v -> cfg.pillAlwaysVisible = v);
        addButton("hoennascension.opt.pillAnchor", () -> anchorName(cfg.minimizedAnchor), () -> {
            cfg.minimizedAnchor = nextAnchor(cfg.minimizedAnchor);
            cfg.pillXr = -1f;
            cfg.pillYr = -1f;
        });
        add(ButtonWidget.builder(Text.translatable("hoennascension.opt.resetLayout"), btn -> {
            cfg.resetLayout();
            cfg.save();
        }).dimensions(0, 0, colW, 20).build());
        add(ButtonWidget.builder(Text.translatable("hoennascension.opt.clearHistory"),
                btn -> HoennAscensionClient.STORE.clear()).dimensions(0, 0, colW, 20).build());

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, btn -> close())
                .dimensions(width / 2 - 100, height - 26, 200, 20).build());

        applyScroll();
    }

    private int maxScroll() {
        int rows = (count + 1) / 2;
        return Math.max(0, rows * ROW_H - (bottom() - top()));
    }

    private void applyScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (int i = 0; i < entries.size(); i++) {
            ClickableWidget w = entries.get(i);
            int y = top() + (i / 2) * ROW_H - scroll;
            w.setY(y);
            boolean inside = y >= top() - 2 && y + w.getHeight() <= bottom() + 2;
            w.visible = inside;
            w.active = inside;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (vertical != 0) {
            scroll -= (int) (vertical * 14);
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        c.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        HoennConfig.get().save();
    }

    /** Deslizador genérico con rango, paso y formato propios. */
    private static final class OptSlider extends SliderWidget {
        private final String key;
        private final double min, max, step;
        private final DoubleFunction<String> fmt;
        private final DoubleConsumer setter;

        OptSlider(int w, String key, double min, double max, double step, double current,
                  DoubleFunction<String> fmt, DoubleConsumer setter) {
            super(0, 0, w, 20, Text.empty(), (current - min) / (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.fmt = fmt;
            this.setter = setter;
            updateMessage();
        }

        private double current() {
            double v = min + value * (max - min);
            if (step > 0) v = Math.round(v / step) * step;
            return Math.max(min, Math.min(max, v));
        }

        @Override
        protected void updateMessage() {
            if (fmt == null) return;
            setMessage(Text.translatable(key).append(": " + fmt.apply(current())));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}