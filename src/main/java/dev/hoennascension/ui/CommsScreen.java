package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.core.ChatEntry;
import dev.hoennascension.core.Commands;
import dev.hoennascension.core.DonationTracker;
import dev.hoennascension.core.Whispers;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Uuids;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Panel principal con sidebar de navegación (canales + funciones).
 * Movible, redimensionable (con límites) y proporcional al tamaño de la ventana; animaciones de
 * apertura, cierre, minimizado y plegado del sidebar. La X minimiza; Esc cierra del todo.
 * Nada recibe el foco de escritura por sí solo: la misma tecla abre y cierra sin tocar el texto.
 */
public final class CommsScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern PLAYER = Pattern.compile("[A-Za-z0-9_.]{1,32}");
    private static final int LINE_H = 10;
    private static final int MARGIN = 4;
    private static final int HEADER_H = 36;
    private static final int EDGE = 5;
    private static final float ANIM_SECONDS = 0.22f;
    private static final int SB_EXPANDED = 98;
    private static final int SB_COLLAPSED = 28;
    private static final int AUTO_COLLAPSE_BELOW = 340;

    // Símbolos que existen en la fuente de Minecraft (se ven igual para todos los jugadores)
    private static final String[] EMOJIS = {
            "\u263A", "\u263B", "\u2665", "\u2661", "\u2605", "\u2606", "\u266A", "\u266B",
            "\u2600", "\u2601", "\u2602", "\u2744", "\u273F", "\u2714", "\u2718", "\u2726",
            "\u279C", "\u2620", "\u26A1", "\u2660", "\u2663", "\u2666", "\u262F", "\u262E",
            "\u270C", "\u260E", "\u2709", "\u2694", "\u2693", "\u2702", "\u231B", "\u2640"
    };
    private static final int EMO_COLS = 8;
    private static final int EMO_CELL = 16;
    private static final int EMO_PAD = 4;

    private enum Page { CHAT, WHISPER }

    private static final int KIND_SECTION = 0, KIND_CHANNEL = 1, KIND_FUNC = 2, KIND_SEP = 3;

    private record Line(OrderedText text, int rgb, boolean mention) {}
    private record SbItem(int kind, String id, String label, int rgb, int y, int h) {}

    private final List<Line> lines = new ArrayList<>();
    private final List<String> tabIds = new ArrayList<>();
    private final List<SbItem> sbItems = new ArrayList<>();
    private final WhisperPane wp = new WhisperPane();

    private Page page = Page.CHAT;
    private String activeTab = "all";
    private int scroll = 0;
    private boolean donateOpen;
    private boolean emojiOpen;

    private int cacheVersion = -1;
    private int cacheWidth = -1;
    private String cacheTab = "";

    // Ventana
    private int px, py, pw, ph;

    // Sidebar
    private float sbAnim = 1f;
    private int sbW = SB_EXPANDED;
    private int sbScroll = 0;
    private int sbMaxScroll = 0;
    private int cx, cw; // área de contenido
    private String hoverTip;

    // Animación de apertura / cierre / minimizado
    private final boolean fromPill;
    private boolean animStarted;
    private float anim = 1f;
    private int animDir = 0;
    private boolean toPill;
    private boolean closeNow;
    private boolean minimizing;

    // Foco y tecla de abrir/cerrar
    private boolean needClearFocus = true;
    private boolean openKeyHeld;

    // Arrastre y redimensionado
    private boolean dragging;
    private int dragOffX, dragOffY;
    private int resizeMode;
    private int resizeStartMx, resizeStartMy, resizeStartW, resizeStartH;
    private long lastHeaderClick;

    // Cabecera
    private int closeX, pinX, themeX;
    private boolean showTheme;
    private boolean showUser;
    private int userX, userW;
    private String userName = "";

    // Decoración
    private boolean showLogo;
    private int logoX, logoY, logoW, logoH;
    private int bannerX, bannerY, bannerW, bannerH;
    private float bannerAnim = 1f;
    private long lastFrame;

    private TextFieldWidget input;
    private TextFieldWidget nickField;
    private TextFieldWidget amountField;
    private int placeholderW = -1;

    public CommsScreen() {
        this(false);
    }

    public CommsScreen(boolean fromPill) {
        super(Text.translatable("hoennascension.title"));
        this.fromPill = fromPill;
    }

    // ---- Utilidades de layout -----------------------------------------------------------

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // Límites de tamaño: los de la config, siempre acotados por el tamaño real de la pantalla
    private int minW() { return Math.min(HoennConfig.get().windowMinWidth, Math.max(120, width - 2 * MARGIN)); }
    private int minH() { return Math.min(HoennConfig.get().windowMinHeight, Math.max(80, height - 2 * MARGIN)); }
    private int maxW() { return Math.max(minW(), Math.min(HoennConfig.get().windowMaxWidth, width - 2 * MARGIN)); }
    private int maxH() { return Math.max(minH(), Math.min(HoennConfig.get().windowMaxHeight, height - 2 * MARGIN)); }

    private String viewTab() { return page == Page.WHISPER ? "private" : activeTab; }

    private int listTop() { return py + HEADER_H + 6 + (page == Page.WHISPER ? 22 : 0); }
    /** En Susurros los mensajes empiezan a la derecha de la lista de conversaciones. */
    private int msgLeft() { return page == Page.WHISPER ? wp.contentLeft() : cx; }
    private int msgW() { return px + pw - msgLeft(); }
    private int barLeft() { return page == Page.WHISPER ? wp.contentLeft() : cx + 6; }
    private int listBottom() { return py + ph - 26; }
    private int visibleLines() { return Math.max(1, (listBottom() - listTop()) / LINE_H); }
    private int btnY() { return py + (HEADER_H - 14) / 2; }
    private int sendX() { return px + pw - 26; }
    private int emojiX() { return sendX() - 18; }
    private int inputBarY() { return py + ph - 22; }
    private int sbBottom() { return py + ph - 24; }

    private void storeLayout(HoennConfig cfg) {
        cfg.winXr = px / (float) width;
        cfg.winYr = py / (float) height;
        cfg.winWr = pw / (float) width;
        cfg.winHr = ph / (float) height;
    }

    /** Posición y tamaño proporcionales a la pantalla: siguen el tamaño de la ventana del juego. */
    private void applyInitialLayout(HoennConfig cfg) {
        if (cfg.winWr > 0f && cfg.winHr > 0f && cfg.winXr >= 0f && cfg.winYr >= 0f) {
            pw = clamp(Math.round(cfg.winWr * width), minW(), maxW());
            ph = clamp(Math.round(cfg.winHr * height), minH(), maxH());
            px = clamp(Math.round(cfg.winXr * width), MARGIN, Math.max(MARGIN, width - pw - MARGIN));
            py = clamp(Math.round(cfg.winYr * height), MARGIN, Math.max(MARGIN, height - ph - MARGIN));
        } else {
            pw = clamp(Math.round(width * 0.78f), minW(), Math.max(minW(), Math.min(maxW(), 540)));
            ph = clamp(Math.round(height * 0.74f), minH(), Math.max(minH(), Math.min(maxH(), 310)));
            px = (width - pw) / 2;
            py = (height - ph) / 2;
        }
    }

    private void computeHeader() {
        HoennConfig cfg = HoennConfig.get();
        int leftEnd;
        if (cfg.showLogo) {
            int lh = HEADER_H - 8;
            leftEnd = px + 10 + lh * Art.LOGO_W / Art.LOGO_H + 8;
        } else {
            leftEnd = px + 24 + textRenderer.getWidth(title) + 8;
        }
        int x = px + pw - 6;
        closeX = x - 14;
        x = closeX - 2;
        pinX = x - 14;
        x = pinX - 4;
        showTheme = x - 58 >= leftEnd;
        themeX = x - 58;

        // Cabeza y nombre del jugador: solo si caben entre el logo y los botones
        int rightLimit = showTheme ? themeX - 6 : x;
        String name = (client != null && client.player != null) ? client.player.getName().getString() : "";
        showUser = false;
        int avail = rightLimit - leftEnd;
        if (!name.isEmpty() && avail >= 3 + 12 + 4 + 24 + 6) {
            userW = Math.min(3 + 12 + 4 + textRenderer.getWidth(name) + 6, avail);
            userName = textRenderer.trimToWidth(name, userW - 3 - 12 - 4 - 6);
            userX = rightLimit - userW;
            showUser = true;
        }
    }

    /** Ancho del sidebar (animado) y área de contenido resultante. */
    private void computeContent() {
        sbW = SB_COLLAPSED + Math.round((SB_EXPANDED - SB_COLLAPSED) * sbAnim);
        cx = px + sbW + 1;
        cw = pw - sbW - 2;
        wp.layout(cx, py + HEADER_H + 6, cw, py + ph - 24);
    }

    private boolean sidebarWantsCollapsed(HoennConfig cfg) {
        return cfg.sidebarCollapsed || pw < AUTO_COLLAPSE_BELOW;
    }

    private void layoutDecor(HoennConfig cfg) {
        logoH = HEADER_H - 8;
        logoW = logoH * Art.LOGO_W / Art.LOGO_H;
        showLogo = cfg.showLogo;
        logoX = px + 10;
        logoY = py + 4;

        int overlap = 6;
        int peek = 9;
        int bw = Math.min(72, Math.max(36, ph / 4));
        bw = Math.min(bw, px + overlap);
        int bh = bw * Art.BANNER_H / Art.BANNER_W;
        bannerY = py + 4;
        int maxH = height - bannerY - 2;
        if (bh > maxH) {
            bh = maxH;
            bw = bh * Art.BANNER_W / Art.BANNER_H;
        }
        if (!cfg.showBanner || bw < 28) {
            bannerW = 0;
            bannerH = 0;
            return;
        }
        bannerW = bw;
        bannerH = bh;
        int visibleX = px + overlap - bw;
        int hiddenX = px - peek;
        bannerX = Math.round(hiddenX + (visibleX - hiddenX) * bannerAnim);
    }

    private boolean overBanner(double mx, double my) {
        return bannerW > 0 && Gfx.inside(mx, my, bannerX, bannerY, Math.min(bannerW, px - bannerX), bannerH);
    }

    // Geometría del popup de donación (centrado en el panel)
    private int dlgW() { return Math.max(180, Math.min(270, pw - 24)); }
    private int dlgH() { return 118; }
    private int dlgX() { return px + (pw - dlgW()) / 2; }
    private int dlgY() { return py + Math.max(HEADER_H + 4, (ph - dlgH()) / 2); }
    private int dlgFieldY() { return dlgY() + 78; }
    private int dlgConfirmX() { return dlgX() + dlgW() - 10 - 66; }

    private int dlgQuickCount() {
        int fit = Math.max(1, (dlgW() - 20 + 4) / (34 + 4));
        return Math.min(Math.min(6, fit), HoennConfig.get().server.donateAmounts.size());
    }

    private int dlgQuickW() {
        int n = Math.max(1, dlgQuickCount());
        return (dlgW() - 20 - (n - 1) * 4) / n;
    }

    private int dlgQuickX(int i) {
        return dlgX() + 10 + i * (dlgQuickW() + 4);
    }

    // Geometría del selector de emojis (anclado sobre el botón)
    private int emoW() { return EMO_COLS * EMO_CELL + EMO_PAD * 2; }
    private int emoH() { return ((EMOJIS.length + EMO_COLS - 1) / EMO_COLS) * EMO_CELL + EMO_PAD * 2; }
    private int emoX() { return clamp(emojiX() + 18 - emoW(), px + 2, Math.max(px + 2, px + pw - emoW() - 2)); }
    private int emoY() { return Math.max(py + HEADER_H + 2, inputBarY() - emoH() - 4); }

    private int emojiIndexAt(double mx, double my) {
        if (!Gfx.inside(mx, my, emoX(), emoY(), emoW(), emoH())) return -1;
        int col = ((int) mx - emoX() - EMO_PAD) / EMO_CELL;
        int row = ((int) my - emoY() - EMO_PAD) / EMO_CELL;
        if (col < 0 || col >= EMO_COLS || row < 0) return -1;
        int idx = row * EMO_COLS + col;
        return idx < EMOJIS.length ? idx : -1;
    }

    private void layoutWidgets() {
        HoennConfig cfg = HoennConfig.get();
        Theme th = Theme.byId(cfg.theme);
        input.setEditableColor(th.text);
        nickField.setEditableColor(th.text);
        amountField.setEditableColor(th.text);

        boolean modal = donateOpen || wp.dialogOpen;
        input.visible = !modal;
        input.active = !modal;
        input.setX(barLeft() + 4);
        input.setY(py + ph - 18);
        input.setWidth(Math.max(20, emojiX() - (barLeft() + 4) - 2));
        if (input.getWidth() != placeholderW || placeholderKey != phKey()) updatePlaceholder();

        nickField.visible = wp.dialogOpen;
        nickField.active = wp.dialogOpen;
        if (wp.dialogOpen) {
            nickField.setX(dlgX() + 14);
            nickField.setY(dlgY() + 30);
            nickField.setWidth(dlgW() - 28);
        }

        amountField.visible = donateOpen;
        amountField.active = donateOpen;
        if (donateOpen) {
            amountField.setX(dlgX() + 14);
            amountField.setY(dlgFieldY() + 4);
            amountField.setWidth(Math.max(30, dlgConfirmX() - 4 - (dlgX() + 10) - 8));
        }
    }

    @Override
    protected void init() {
        HoennConfig cfg = HoennConfig.get();
        cfg.minimized = false;
        MinimizedPill.unsnooze();

        tabIds.clear();
        tabIds.add("all");
        for (ChannelDef c : cfg.channels) tabIds.add(c.id);
        if (!tabIds.contains(activeTab)) activeTab = "all";

        applyInitialLayout(cfg);

        input = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.empty());
        input.setMaxLength(256);
        input.setDrawsBackground(false);
        addDrawableChild(input);

        nickField = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.empty());
        nickField.setMaxLength(32);
        nickField.setDrawsBackground(false);
        nickField.setTextPredicate(s -> s.matches("[A-Za-z0-9_.]*"));
        nickField.setPlaceholder(Text.translatable("hoennascension.whisper.target.hint"));
        addDrawableChild(nickField);

        amountField = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.empty());
        amountField.setMaxLength(12);
        amountField.setDrawsBackground(false);
        amountField.setTextPredicate(s -> s.chars().allMatch(Character::isDigit));
        amountField.setPlaceholder(Text.translatable("hoennascension.donate.hint"));
        addDrawableChild(amountField);

        bannerAnim = cfg.bannerHidden ? 0f : 1f;
        sbAnim = sidebarWantsCollapsed(cfg) ? 0f : 1f;
        lastFrame = System.currentTimeMillis();
        computeHeader();
        computeContent();
        updatePlaceholder();
        layoutWidgets();

        if (!animStarted) {
            animStarted = true;
            anim = 0f;
            animDir = 1;
            toPill = fromPill;
            // Si la tecla de abrir sigue pulsada, se ignora hasta que se suelte (evita cerrar por repetición)
            openKeyHeld = isOpenKeyDown();
        }

        // Ningún campo recibe el foco por sí solo: se limpia en el primer fotograma
        needClearFocus = true;
        HoennAscensionClient.STORE.markRead(viewTab());
    }

    private boolean isOpenKeyDown() {
        try {
            long handle = client.getWindow().getHandle();
            int code = InputUtil.fromTranslationKey(HoennAscensionClient.OPEN.getBoundKeyTranslationKey()).getCode();
            return InputUtil.isKeyPressed(handle, code);
        } catch (Exception e) {
            return false;
        }
    }

    private int placeholderKey = -1;

    private int phKey() {
        return page == Page.WHISPER ? (wp.peer == null ? 2 : 1) : 0;
    }

    private void updatePlaceholder() {
        String key = switch (phKey()) {
            case 2 -> "hoennascension.input.placeholder.whisper.none";
            case 1 -> "hoennascension.input.placeholder.whisper";
            default -> "hoennascension.input.placeholder";
        };
        String full = Text.translatable(key).getString();
        placeholderW = input.getWidth();
        placeholderKey = phKey();
        input.setPlaceholder(Text.literal(textRenderer.trimToWidth(full, Math.max(20, input.getWidth() - 6))));
    }

    private void setPage(Page p) {
        page = p;
        scroll = 0;
        cacheVersion = -1;
        updatePlaceholder();
        layoutWidgets();
        HoennAscensionClient.STORE.markRead(viewTab());
    }

    private void resetLayout() {
        HoennConfig cfg = HoennConfig.get();
        cfg.resetLayout();
        cfg.save();
        clearAndInit();
    }

    // ---- Animación de apertura/cierre ---------------------------------------------------

    private void requestClose(boolean minimize) {
        if (animDir < 0) return;
        minimizing = minimize;
        toPill = minimize;
        animDir = -1;
        if (anim <= 0f) closeNow = true;
    }

    private boolean busy() {
        return animDir != 0 || anim < 1f;
    }

    private void applyAnimTransform(DrawContext c, float e) {
        float t = 1f - e;
        float wcx = px + pw / 2f;
        float wcy = py + ph / 2f;
        float s, tx, ty;
        if (toPill) {
            int[] r = MinimizedPill.rect();
            float pcx = r[0] + r[2] / 2f;
            float pcy = r[1] + r[3] / 2f;
            s = 1f + (0.08f - 1f) * t;
            tx = wcx + (pcx - wcx) * t;
            ty = wcy + (pcy - wcy) * t;
        } else {
            s = 1f - 0.08f * t;
            tx = wcx;
            ty = wcy + 10f * t;
        }
        MatrixStack m = c.getMatrices();
        m.translate(tx, ty, 0f);
        m.scale(s, s, 1f);
        m.translate(-wcx, -wcy, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (closeNow && client != null) {
            closeNow = false;
            client.setScreen(null);
        }
    }

    // ---- Datos --------------------------------------------------------------------------

    private String tabName(String id) {
        if (id.equals("all")) return Text.translatable("hoennascension.tab.all").getString();
        ChannelDef c = HoennConfig.get().channel(id);
        return c == null ? id : c.name;
    }

    private void rebuildIfNeeded(Theme th) {
        String tab = viewTab();
        String key = tab + "/" + (page == Page.WHISPER && wp.peer != null ? wp.peer.toLowerCase() : "");
        int version = HoennAscensionClient.STORE.version;
        if (version == cacheVersion && msgW() == cacheWidth && key.equals(cacheTab)) return;

        cacheVersion = version;
        cacheWidth = msgW();
        cacheTab = key;
        lines.clear();

        HoennConfig cfg = HoennConfig.get();
        int wrapW = Math.max(40, msgW() - 28);
        List<ChatEntry> source = (page == Page.WHISPER && wp.peer != null)
                ? HoennAscensionClient.STORE.viewPeer(wp.peer, 400)
                : HoennAscensionClient.STORE.view(tab, "", 400);
        for (ChatEntry e : source) {
            ChannelDef ch = cfg.channel(e.channel);
            int rgb = ch != null ? ch.rgb() : 0xFFFFFF;
            MutableText t = Text.empty();
            if (cfg.timestamps) {
                String time = LocalTime.ofInstant(Instant.ofEpochMilli(e.time), ZoneId.systemDefault()).format(TIME);
                t.append(Text.literal(time + " ").setStyle(Style.EMPTY.withColor(th.muted & 0xFFFFFF)));
            }
            if (tab.equals("all") && ch != null) {
                t.append(Text.literal("[" + ch.name + "] ").setStyle(Style.EMPTY.withColor(rgb)));
            }
            t.append(Text.literal(e.text));
            for (OrderedText ot : textRenderer.wrapLines(t, wrapW)) lines.add(new Line(ot, rgb, e.mention));
        }
    }

    private void selectChannel(String id) {
        page = Page.CHAT;
        activeTab = id;
        scroll = 0;
        cacheVersion = -1;
        updatePlaceholder();
        layoutWidgets();
        HoennAscensionClient.STORE.markRead(id);
    }

    private void cycleChannel(int dir) {
        int i = page == Page.CHAT ? tabIds.indexOf(activeTab) : -1;
        i = (i + dir + tabIds.size()) % tabIds.size();
        selectChannel(tabIds.get(i));
    }

    private boolean whisper(String player, String msg) {
        String tpl = HoennConfig.get().server.privateCommand;
        if (msg.isEmpty() || !PLAYER.matcher(player).matches() || tpl == null || tpl.isBlank()) return false;
        Commands.run(Commands.fill(tpl, "{player}", player, "{message}", msg));
        Whispers.sent(player, msg);
        return true;
    }

    private void send() {
        String s = input.getText().trim();
        if (s.isEmpty() || client == null || client.player == null) return;
        HoennConfig cfg = HoennConfig.get();

        // Página Susurros: el destinatario sale del campo "Para:"
        if (page == Page.WHISPER && wp.peer != null && !s.startsWith("/")) {
            if (whisper(wp.peer, s)) {
                input.setText("");
                scroll = 0;
            }
            return;
        }

        // En cualquier canal: "@Jugador mensaje"
        if (s.startsWith("@")) {
            int sp = s.indexOf(' ');
            if (sp > 1 && whisper(s.substring(1, sp), s.substring(sp + 1).trim())) {
                input.setText("");
                scroll = 0;
                return;
            }
        }

        ChannelDef ch = cfg.channel(activeTab);
        String prefix = (ch != null && ch.sendPrefix != null) ? ch.sendPrefix : "";
        String full = s.startsWith("/") ? s : prefix + s;
        if (full.startsWith("/")) Commands.run(full);
        else client.player.networkHandler.sendChatMessage(full);
        input.setText("");
        scroll = 0;
    }

    // ---- Donación (popup de cantidad) ---------------------------------------------------

    private void openDonate() {
        donateOpen = true;
        emojiOpen = false;
        layoutWidgets();
    }

    private void closeDonate() {
        donateOpen = false;
        amountField.setText("");
        setFocused(null);
        layoutWidgets();
    }

    /** Ejecuta el comando de donación del servidor (por defecto: /clan deposit {cantidad}). */
    private void donate(long value) {
        Commands.run(Commands.fill(HoennConfig.get().server.donateCommand, "{amount}", String.valueOf(value)));
        DonationTracker.start(value);
        closeDonate();
    }

    private void donateCustom() {
        String s = amountField.getText().trim();
        if (s.isEmpty()) return;
        try {
            long v = Long.parseLong(s);
            if (v > 0) donate(v);
        } catch (NumberFormatException ignored) {
        }
    }

    private static String shortLabel(int v) {
        return (v >= 1000 && v % 1000 == 0) ? (v / 1000) + "K" : String.valueOf(v);
    }

    // ---- Sidebar ------------------------------------------------------------------------

    private static final int RGB_DONATE = 0x35C46A;
    private static final int RGB_WHISPER = 0xA481DE;
    private static final int RGB_SETTINGS = 0x8FD8FF;

    private void buildSidebar() {
        HoennConfig cfg = HoennConfig.get();
        sbItems.clear();
        boolean expanded = sbW >= 64;
        int baseTop = py + HEADER_H + 4;
        int y = baseTop - sbScroll;

        if (expanded) {
            sbItems.add(new SbItem(KIND_SECTION, "", Text.translatable("hoennascension.sb.channels").getString(), 0, y, 10));
            y += 12;
        } else {
            y += 2;
        }
        for (String id : tabIds) {
            ChannelDef ch = id.equals("all") ? null : cfg.channel(id);
            sbItems.add(new SbItem(KIND_CHANNEL, id, tabName(id), ch != null ? ch.rgb() : 0xBFD4FF, y, 16));
            y += 17;
        }
        y += 3;
        sbItems.add(new SbItem(KIND_SEP, "", "", 0, y, 1));
        y += 5;
        if (expanded) {
            sbItems.add(new SbItem(KIND_SECTION, "", Text.translatable("hoennascension.sb.functions").getString(), 0, y, 10));
            y += 12;
        }
        if (cfg.server.canDonate()) {
            sbItems.add(new SbItem(KIND_FUNC, "donate", Text.translatable("hoennascension.donate").getString(), RGB_DONATE, y, 16));
            y += 17;
        }
        sbItems.add(new SbItem(KIND_FUNC, "whisper", Text.translatable("hoennascension.page.whispers").getString(), RGB_WHISPER, y, 16));
        y += 17;
        sbItems.add(new SbItem(KIND_FUNC, "settings", Text.translatable("hoennascension.page.settings").getString(), RGB_SETTINGS, y, 16));
        y += 17;

        int contentH = (y + sbScroll) - baseTop;
        int viewH = sbBottom() - (py + HEADER_H) - 4;
        sbMaxScroll = Math.max(0, contentH - viewH);
        if (sbScroll > sbMaxScroll) sbScroll = sbMaxScroll;
    }

    private boolean inSidebar(double mx, double my) {
        return Gfx.inside(mx, my, px + 1, py + HEADER_H, sbW, ph - HEADER_H - 1);
    }

    private boolean inSidebarList(double mx, double my) {
        return Gfx.inside(mx, my, px + 1, py + HEADER_H, sbW, sbBottom() - (py + HEADER_H));
    }

    private boolean itemActive(SbItem it) {
        if (it.kind() == KIND_CHANNEL) return page == Page.CHAT && it.id().equals(activeTab);
        if (it.kind() == KIND_FUNC) {
            return (it.id().equals("donate") && donateOpen)
                    || (it.id().equals("whisper") && page == Page.WHISPER);
        }
        return false;
    }

    private int unreadFor(SbItem it) {
        if (itemActive(it)) return 0;
        if (it.kind() == KIND_CHANNEL) return HoennAscensionClient.STORE.unread(it.id());
        if (it.kind() == KIND_FUNC && it.id().equals("whisper")) return HoennAscensionClient.STORE.unread("private");
        return 0;
    }

    private int toggleX() { return px + 1 + sbW / 2 - 7; }
    private int toggleY() { return py + ph - 20; }

    private void renderSidebar(DrawContext c, Theme th, int mx, int my, boolean clip) {
        TextRenderer tr = textRenderer;
        HoennConfig cfg = HoennConfig.get();
        int sx = px + 1;
        int top = py + HEADER_H;
        boolean expanded = sbW >= 64;

        c.fill(sx, top, sx + sbW, py + ph - 1, 0x22000000);
        c.fillGradient(sx + sbW, top, sx + sbW + 1, py + ph - 1, Gfx.alpha(th.edgeA, 0.8f), Gfx.alpha(th.edgeC, 0.8f));

        if (clip) c.enableScissor(sx, top, sx + sbW, sbBottom());
        for (SbItem it : sbItems) {
            if (it.y() + it.h() < top || it.y() > sbBottom()) continue;
            switch (it.kind()) {
                case KIND_SECTION -> c.drawText(tr, it.label(), sx + 8, it.y() + 1, Gfx.alpha(th.muted, 0.85f), false);
                case KIND_SEP -> Gfx.hBand(c, sx + 6, it.y(), sx + sbW - 6, it.y() + 1, th, 0.7f, 0f, 1f);
                default -> {
                    boolean active = itemActive(it);
                    boolean hover = !donateOpen && inSidebarList(mx, my)
                            && Gfx.inside(mx, my, sx + 2, it.y(), sbW - 3, it.h());
                    int rgb = it.rgb();
                    if (active) {
                        c.fillGradient(sx + 2, it.y(), sx + sbW - 1, it.y() + it.h(), Gfx.rgb(rgb, 0.32f), Gfx.rgb(rgb, 0.06f));
                        c.fill(sx + 2, it.y(), sx + 4, it.y() + it.h(), Gfx.rgb(rgb, 1f));
                    } else if (hover) {
                        c.fill(sx + 2, it.y(), sx + sbW - 1, it.y() + it.h(), Gfx.alpha(th.text, 0.14f));
                    }
                    if (hover && !expanded) hoverTip = it.label();

                    float ia = active ? 1f : 0.8f;
                    if (it.kind() == KIND_CHANNEL) {
                        ChannelDef chd = cfg.channel(it.id());
                        boolean hid = cfg.hideAllFromVanilla || (chd != null && chd.hideFromVanilla);
                        String icon = it.id().equals("all") ? "all" : (chd != null ? chd.iconName() : it.id());
                        if (!Icons.draw(c, icon, sx + 6, it.y() + 2, 12, hid ? ia * 0.5f : ia)) {
                            int dcol = Gfx.rgb(rgb, ia);
                            if (hid) Gfx.diamondOutline(c, sx + 12, it.y() + 8, 3, dcol);
                            else Gfx.diamond(c, sx + 12, it.y() + 8, 3, dcol);
                        }
                        // Tachado = este canal NO se muestra en el chat normal de Minecraft
                        if (hid) {
                            for (int k = 0; k < 10; k++) {
                                c.fill(sx + 7 + k, it.y() + 12 - k, sx + 8 + k, it.y() + 13 - k, Gfx.alpha(th.text, 0.85f));
                            }
                        }
                    } else if (!Icons.draw(c, it.id(), sx + 6, it.y() + 2, 12, ia)) {
                        drawFuncIcon(c, it.id(), sx + 6, it.y() + 2, rgb, ia);
                    }
                    int unread = unreadFor(it);
                    int badgeW = 0;
                    String badge = "";
                    if (unread > 0) {
                        badge = unread > 99 ? "99+" : String.valueOf(unread);
                        badgeW = tr.getWidth(badge) + 4;
                    }
                    if (expanded) {
                        int labelX = sx + 21;
                        String label = tr.trimToWidth(it.label(), Math.max(0, sbW - 21 - badgeW - 6));
                        c.drawText(tr, label, labelX, it.y() + 4, active ? th.text : th.muted, !th.light);
                        if (unread > 0) {
                            int bx = sx + sbW - badgeW - 4;
                            c.fill(bx, it.y() + 3, bx + badgeW, it.y() + 12, Gfx.rgb(rgb, 0.85f));
                            c.drawText(tr, badge, bx + 2, it.y() + 4, 0xFFFFFFFF, false);
                        }
                    } else if (unread > 0) {
                        c.fill(sx + sbW - 8, it.y() + 2, sx + sbW - 3, it.y() + 7, Gfx.rgb(rgb, 1f));
                    }
                }
            }
        }
        if (clip) c.disableScissor();

        // Botón de contraer / expandir
        boolean hoverToggle = !donateOpen && Gfx.inside(mx, my, toggleX(), toggleY(), 14, 14);
        c.drawText(tr, sidebarWantsCollapsed(cfg) ? ">" : "<", toggleX() + 4, toggleY() + 3,
                hoverToggle ? th.text : th.muted, false);
        if (hoverToggle) hoverTip = Text.translatable(sidebarWantsCollapsed(cfg)
                ? "hoennascension.sidebar.expand" : "hoennascension.sidebar.collapse").getString();
    }

    private void drawFuncIcon(DrawContext c, String id, int x, int y, int rgb, float a) {
        c.fill(x, y, x + 12, y + 12, Gfx.rgb(rgb, 0.30f * a));
        int col = Gfx.rgb(rgb, a);
        switch (id) {
            case "donate" -> c.drawText(textRenderer, "$", x + 3, y + 2, col, false);
            case "whisper" -> c.drawText(textRenderer, "@", x + 3, y + 2, col, false);
            default -> {
                for (int i = 0; i < 3; i++) {
                    int ly = y + 3 + i * 3;
                    c.fill(x + 2, ly, x + 10, ly + 1, col);
                    int kx = x + 3 + ((i * 4) % 6);
                    c.fill(kx, ly - 1, kx + 2, ly + 2, col);
                }
            }
        }
    }

    private void onSidebarItem(SbItem it) {
        if (it.kind() == KIND_CHANNEL) {
            selectChannel(it.id());
        } else if (it.kind() == KIND_FUNC) {
            switch (it.id()) {
                case "donate" -> openDonate();
                case "whisper" -> setPage(Page.WHISPER);
                default -> {
                    if (client != null) client.setScreen(new HoennConfigScreen(this));
                }
            }
        }
    }

    // ---- Render -------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        // Intencionalmente vacío: sin blur vanilla; el oscurecido se dibuja en render().
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        HoennConfig cfg = HoennConfig.get();
        Theme th = Theme.byId(cfg.theme);
        TextRenderer tr = textRenderer;

        // El sistema da foco al primer campo al abrir: se deshace para que escribir sea opt-in
        if (needClearFocus) {
            setFocused(null);
            needClearFocus = false;
        }

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        if (animDir != 0) {
            anim += animDir * dt / ANIM_SECONDS;
            if (animDir > 0 && anim >= 1f) {
                anim = 1f;
                animDir = 0;
            } else if (animDir < 0 && anim <= 0f) {
                anim = 0f;
                closeNow = true;
            }
        }
        float e = anim * anim * (3f - 2f * anim);
        boolean animating = busy();

        c.fill(0, 0, width, height, ((int) (0x44 * e) << 24));
        if (closeNow) return;

        float bTarget = cfg.bannerHidden ? 0f : 1f;
        bannerAnim += (bTarget - bannerAnim) * Math.min(1f, dt * 12f);
        if (Math.abs(bTarget - bannerAnim) < 0.004f) bannerAnim = bTarget;

        float sTarget = sidebarWantsCollapsed(cfg) ? 0f : 1f;
        sbAnim += (sTarget - sbAnim) * Math.min(1f, dt * 14f);
        if (Math.abs(sTarget - sbAnim) < 0.01f) sbAnim = sTarget;

        computeHeader();
        computeContent();
        layoutWidgets();
        layoutDecor(cfg);
        buildSidebar();
        hoverTip = null;

        if (animating) {
            c.getMatrices().push();
            applyAnimTransform(c, e);
        }
        boolean clip = !animating;

        if (bannerW > 0) {
            if (clip) c.enableScissor(0, 0, px + 1, height);
            Art.draw(c, Art.BANNER, bannerX, bannerY, bannerW, bannerH, Art.BANNER_W, Art.BANNER_H,
                    0.6f + 0.4f * bannerAnim);
            if (clip) c.disableScissor();
        }

        Gfx.panel(c, px, py, pw, ph, th, cfg.menuOpacity * (0.35f + 0.65f * e));

        HoennAscensionClient.STORE.markRead(viewTab());
        if (page == Page.WHISPER && wp.peer != null) HoennAscensionClient.STORE.markPeerRead(wp.peer);
        rebuildIfNeeded(th);

        // Cabecera
        if (showLogo) {
            Art.draw(c, Art.LOGO, logoX, logoY, logoW, logoH, Art.LOGO_W, Art.LOGO_H, 1f);
        } else {
            Gfx.diamond(c, px + 13, py + HEADER_H / 2, 4, th.edgeAt(0.5f));
            Gfx.diamondOutline(c, px + 13, py + HEADER_H / 2, 6, th.edgeAt(0.15f));
            c.drawText(tr, title, px + 24, py + HEADER_H / 2 - 4, th.text, !th.light);
        }
        Gfx.hBand(c, px + 1, py + HEADER_H - 1, px + pw - 1, py + HEADER_H, th, 0.6f, 0f, 1f);

        boolean hoverTheme = showTheme && Gfx.inside(mx, my, themeX, btnY(), 58, 14);
        if (showTheme) {
            c.fillGradient(themeX, btnY(), themeX + 58, btnY() + 14,
                    Gfx.alpha(th.edgeA, hoverTheme ? 0.9f : 0.5f), Gfx.alpha(th.edgeC, hoverTheme ? 0.9f : 0.5f));
            c.drawText(tr, th.label, themeX + 29 - tr.getWidth(th.label) / 2, btnY() + 3, th.text, false);
        }

        if (showUser && client != null && client.player != null) {
            c.fill(userX, btnY() - 1, userX + userW, btnY() + 15, Gfx.alpha(th.text, 0.10f));
            Identifier skin = client.player.getSkinTextures().texture();
            Art.drawFace(c, skin, userX + 3, btnY() + 1, 12);
            c.drawText(tr, userName, userX + 3 + 12 + 4, btnY() + 3, th.text, !th.light);
        }

        boolean hoverPin = Gfx.inside(mx, my, pinX, btnY(), 14, 14);
        if (cfg.hudPinned) Gfx.diamond(c, pinX + 7, btnY() + 7, 5, th.edgeAt(0.5f));
        else Gfx.diamondOutline(c, pinX + 7, btnY() + 7, 5, hoverPin ? th.text : th.muted);

        boolean hoverClose = Gfx.inside(mx, my, closeX, btnY(), 14, 14);
        c.drawText(tr, "x", closeX + 5, btnY() + 3, hoverClose ? 0xFFFF6A6A : th.muted, false);

        // Sidebar y contenido
        renderSidebar(c, th, mx, my, clip);
        if (page == Page.WHISPER) renderWhisperTop(c, th, mx, my);
        renderMessages(c, th, clip);

        // Barra de entrada con botón de emojis y de enviar
        int ib = inputBarY();
        c.fill(barLeft(), ib, px + pw - 8, ib + 16, th.inputBg);
        Gfx.hBand(c, barLeft(), ib, px + pw - 8, ib + 1, th, 0.8f, 0f, 1f);
        boolean hoverEmoji = !donateOpen && Gfx.inside(mx, my, emojiX(), ib, 18, 16);
        String smile = EMOJIS[0];
        c.drawText(tr, smile, emojiX() + 9 - tr.getWidth(smile) / 2, ib + 4,
                hoverEmoji || emojiOpen ? th.text : th.muted, false);
        boolean hoverSend = !donateOpen && Gfx.inside(mx, my, sendX(), ib, 18, 16);
        Gfx.diamond(c, sendX() + 9, ib + 8, 4, hoverSend ? th.text : th.edgeAt(0.85f));

        // Selector de emojis
        if (emojiOpen && !donateOpen) renderEmojiPicker(c, th, mx, my);

        // Agarre de redimensionado
        boolean hoverGrip = Gfx.inside(mx, my, px + pw - EDGE - 6, py + ph - EDGE - 6, EDGE + 8, EDGE + 8);
        int grip = hoverGrip || resizeMode != 0 ? th.text : th.muted;
        for (int r = 0; r < 3; r++)
            for (int k = 0; k < 3 - r; k++)
                c.fill(px + pw - 5 - k * 3, py + ph - 5 - r * 3, px + pw - 4 - k * 3, py + ph - 4 - r * 3, grip);

        // Popup de donación (encima de todo; el campo de cantidad se dibuja después como widget)
        if (donateOpen) renderDonateDialog(c, th, mx, my);
        if (wp.dialogOpen) wp.renderDialog(c, th, tr, mx, my, px, py, pw, ph, dlgX(), dlgY(), dlgW(), dlgH(), nickField);

        super.render(c, mx, my, delta);

        // Indicador de tamaño al redimensionar (ámbar al tocar un límite)
        if (resizeMode != 0) {
            String dim = pw + " x " + ph;
            boolean atLimit = pw <= minW() || pw >= maxW() || ph <= minH() || ph >= maxH();
            int dw = tr.getWidth(dim) + 8;
            int bx0 = px + pw - dw - 10;
            int by0 = py + ph - 36;
            c.fill(bx0, by0, bx0 + dw, by0 + 12, 0xAA000000);
            c.drawText(tr, dim, bx0 + 4, by0 + 2, atLimit ? 0xFFFFC247 : th.text, false);
        }

        if (animating) c.getMatrices().pop();
        HoennAscensionClient.POPUPS.renderOverScreen(c);
        if (animating || donateOpen || wp.dialogOpen) return;

        boolean hoverBanner = overBanner(mx, my);
        if (hoverPin) c.drawTooltip(tr, Text.translatable(cfg.hudPinned
                ? "hoennascension.tooltip.pin.on" : "hoennascension.tooltip.pin.off"), mx, my);
        else if (hoverTheme) c.drawTooltip(tr, Text.translatable("hoennascension.theme." + th.id), mx, my);
        else if (hoverClose) c.drawTooltip(tr, Text.translatable("hoennascension.tooltip.minimize"), mx, my);
        else if (hoverEmoji && !emojiOpen) c.drawTooltip(tr, Text.translatable("hoennascension.tooltip.emoji"), mx, my);
        else if (hoverBanner) c.drawTooltip(tr, Text.translatable(cfg.bannerHidden
                ? "hoennascension.tooltip.banner.show" : "hoennascension.tooltip.banner.hide"), mx, my);
        else if (hoverTip != null) c.drawTooltip(tr, Text.literal(hoverTip), mx, my);
    }

    private void renderMessages(DrawContext c, Theme th, boolean clip) {
        TextRenderer tr = textRenderer;
        int lt = listTop();
        int ml = msgLeft();
        int lb = listBottom();
        int vis = visibleLines();
        int maxScroll = Math.max(0, lines.size() - vis);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        c.fill(ml + 4, lt - 2, px + pw - 4, lb + 2, 0x22000000);
        if (clip) c.enableScissor(ml + 4, lt - 2, px + pw - 4, lb + 2);
        int y = lb - LINE_H;
        for (int idx = lines.size() - 1 - scroll; idx >= 0 && y > lt - LINE_H; idx--, y -= LINE_H) {
            Line l = lines.get(idx);
            if (l.mention()) c.fill(ml + 6, y - 1, px + pw - 10, y + 9, Gfx.alpha(th.edgeB, 0.30f));
            c.fill(ml + 6, y - 1, ml + 8, y + 9, Gfx.rgb(l.rgb(), 0.9f));
            c.drawText(tr, l.text(), ml + 12, y, th.text, !th.light);
        }
        if (clip) c.disableScissor();

        if (lines.size() > vis) {
            int trackH = lb - lt;
            int thumbH = Math.max(8, trackH * vis / lines.size());
            float frac = maxScroll == 0 ? 0f : scroll / (float) maxScroll;
            int thumbY = lb - thumbH - (int) ((trackH - thumbH) * frac);
            c.fill(px + pw - 8, lt, px + pw - 6, lb, Gfx.alpha(th.text, 0.2f));
            c.fillGradient(px + pw - 8, thumbY, px + pw - 6, thumbY + thumbH,
                    Gfx.alpha(th.edgeA, 1f), Gfx.alpha(th.edgeC, 1f));
        }
    }

    /** Lista de conversaciones (con caras) y cabecera de la conversación abierta. */
    private void renderWhisperTop(DrawContext c, Theme th, int mx, int my) {
        String[] tip = {null};
        wp.render(c, th, textRenderer, mx, my, donateOpen || wp.dialogOpen, tip);
        if (tip[0] != null) hoverTip = tip[0];
        wp.renderHeader(c, th, textRenderer, msgLeft() + 4, py + HEADER_H + 6, px + pw - 8 - (msgLeft() + 4));
    }

    private void renderEmojiPicker(DrawContext c, Theme th, int mx, int my) {
        TextRenderer tr = textRenderer;
        int x0 = emoX();
        int y0 = emoY();
        Gfx.panel(c, x0, y0, emoW(), emoH(), th, 0.95f);
        int hover = emojiIndexAt(mx, my);
        for (int i = 0; i < EMOJIS.length; i++) {
            int cxx = x0 + EMO_PAD + (i % EMO_COLS) * EMO_CELL;
            int cyy = y0 + EMO_PAD + (i / EMO_COLS) * EMO_CELL;
            if (i == hover) c.fill(cxx, cyy, cxx + EMO_CELL, cyy + EMO_CELL, Gfx.alpha(th.text, 0.18f));
            String g = EMOJIS[i];
            c.drawText(tr, g, cxx + (EMO_CELL - tr.getWidth(g)) / 2, cyy + 4, th.text, false);
        }
    }

    private void renderDonateDialog(DrawContext c, Theme th, int mx, int my) {
        TextRenderer tr = textRenderer;
        HoennConfig cfg = HoennConfig.get();
        c.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xAA000000);

        int dx = dlgX(), dy = dlgY(), dw = dlgW(), dh = dlgH();
        Gfx.panel(c, dx, dy, dw, dh, th, 0.95f);

        c.drawText(tr, Text.translatable("hoennascension.donate.title"), dx + 10, dy + 10,
                th.light ? 0xFF1B8A4A : 0xFF7DFFB0, !th.light);
        boolean hoverX = Gfx.inside(mx, my, dx + dw - 18, dy + 6, 12, 12);
        c.drawText(tr, "x", dx + dw - 15, dy + 9, hoverX ? 0xFFFF6A6A : th.muted, false);

        c.drawText(tr, Text.translatable("hoennascension.donate.quick"), dx + 10, dy + 26, th.muted, false);
        int n = dlgQuickCount();
        int bw = dlgQuickW();
        for (int i = 0; i < n; i++) {
            int bx = dlgQuickX(i);
            boolean hover = Gfx.inside(mx, my, bx, dy + 38, bw, 20);
            c.fillGradient(bx, dy + 38, bx + bw, dy + 58,
                    hover ? 0xFF35C46A : 0xFF22984F, hover ? 0xFF1E8F4A : 0xFF146B36);
            String label = tr.trimToWidth(shortLabel(cfg.server.donateAmounts.get(i)), Math.max(8, bw - 4));
            c.drawText(tr, label, bx + bw / 2 - tr.getWidth(label) / 2, dy + 44, 0xFFFFFFFF, true);
        }

        c.drawText(tr, Text.translatable("hoennascension.donate.custom"), dx + 10, dy + 64, th.muted, false);
        int fy = dlgFieldY();
        c.fill(dx + 10, fy, dlgConfirmX() - 4, fy + 16, th.inputBg);
        Gfx.hBand(c, dx + 10, fy, dlgConfirmX() - 4, fy + 1, th, 0.8f, 0f, 1f);

        boolean hoverConfirm = Gfx.inside(mx, my, dlgConfirmX(), fy, 66, 16);
        c.fillGradient(dlgConfirmX(), fy, dlgConfirmX() + 66, fy + 16,
                hoverConfirm ? 0xFF35C46A : 0xFF22984F, hoverConfirm ? 0xFF1E8F4A : 0xFF146B36);
        String conf = Text.translatable("hoennascension.donate.confirm").getString();
        c.drawText(tr, conf, dlgConfirmX() + 33 - tr.getWidth(conf) / 2, fy + 4, 0xFFFFFFFF, true);

        String preview = tr.trimToWidth(cfg.server.donateCommand.replace("{amount}", "<n>"), Math.max(20, dw - 20));
        c.drawText(tr, preview, dx + 10, dy + 102, Gfx.alpha(th.muted, 0.8f), false);
    }

    // ---- Entrada ------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (busy()) return true;

        // Clic derecho sobre un canal del sidebar: ocultar / mostrar sus mensajes en el chat normal
        if (button == 1 && !donateOpen) {
            computeHeader();
            computeContent();
            buildSidebar();
            if (inSidebarList(mx, my)) {
                for (SbItem it : sbItems) {
                    if (it.kind() == KIND_CHANNEL && !it.id().equals("all")
                            && Gfx.inside(mx, my, px + 2, it.y(), sbW - 3, it.h())) {
                        ChannelDef ch = HoennConfig.get().channel(it.id());
                        if (ch != null) {
                            ch.hideFromVanilla = !ch.hideFromVanilla;
                            HoennConfig.get().save();
                        }
                        return true;
                    }
                }
            }
        }

        // Clic derecho en la lista de susurros: borrar conversación (pide un segundo clic para confirmar)
        if (button == 1 && page == Page.WHISPER && !donateOpen && !wp.dialogOpen) {
            computeContent();
            if (wp.click(mx, my, 1)) return true;
        }

        if (button == 0) {
            HoennConfig cfg = HoennConfig.get();
            computeHeader();
            computeContent();
            layoutDecor(cfg);
            buildSidebar();

            // Popup de donación: es modal
            if (donateOpen) {
                int dx = dlgX(), dy = dlgY(), dw = dlgW(), dh = dlgH();
                if (!Gfx.inside(mx, my, dx, dy, dw, dh) || Gfx.inside(mx, my, dx + dw - 18, dy + 6, 12, 12)) {
                    closeDonate();
                    return true;
                }
                for (int i = 0; i < dlgQuickCount(); i++) {
                    if (Gfx.inside(mx, my, dlgQuickX(i), dy + 38, dlgQuickW(), 20)) {
                        donate(cfg.server.donateAmounts.get(i));
                        return true;
                    }
                }
                if (Gfx.inside(mx, my, dlgConfirmX(), dlgFieldY(), 66, 16)) {
                    donateCustom();
                    return true;
                }
                return super.mouseClicked(mx, my, button); // el campo de cantidad
            }

            // Popup de nuevo susurro: también es modal
            if (wp.dialogOpen) {
                if (wp.clickDialog(mx, my, dlgX(), dlgY(), dlgW(), dlgH(), nickField)) {
                    layoutWidgets();
                    return true;
                }
                return super.mouseClicked(mx, my, button); // el campo del nombre
            }

            // Selector de emojis abierto
            if (emojiOpen) {
                int idx = emojiIndexAt(mx, my);
                if (idx >= 0) {
                    input.write(EMOJIS[idx]);
                    setFocused(input);
                    return true;
                }
                boolean onButton = Gfx.inside(mx, my, emojiX(), inputBarY(), 18, 16);
                boolean inPicker = Gfx.inside(mx, my, emoX(), emoY(), emoW(), emoH());
                emojiOpen = false;
                if (onButton || inPicker) return true;
            }

            if (Gfx.inside(mx, my, closeX, btnY(), 14, 14)) {
                requestClose(true);
                return true;
            }
            if (Gfx.inside(mx, my, pinX, btnY(), 14, 14)) {
                cfg.hudPinned = !cfg.hudPinned;
                cfg.save();
                return true;
            }
            if (showTheme && Gfx.inside(mx, my, themeX, btnY(), 58, 14)) {
                cfg.theme = Theme.byId(cfg.theme).next().id;
                cfg.save();
                return true;
            }
            if (Gfx.inside(mx, my, emojiX(), inputBarY(), 18, 16)) {
                emojiOpen = true;
                return true;
            }
            if (Gfx.inside(mx, my, sendX(), inputBarY(), 18, 16)) {
                send();
                return true;
            }
            if (overBanner(mx, my)) {
                cfg.bannerHidden = !cfg.bannerHidden;
                cfg.save();
                return true;
            }

            // Bordes de redimensionado
            boolean onRight = mx >= px + pw - EDGE && mx <= px + pw + 2 && my >= py && my <= py + ph + 2;
            boolean onBottom = my >= py + ph - EDGE && my <= py + ph + 2 && mx >= px && mx <= px + pw + 2;
            if (onRight || onBottom) {
                resizeMode = (onRight ? 1 : 0) | (onBottom ? 2 : 0);
                resizeStartMx = (int) mx;
                resizeStartMy = (int) my;
                resizeStartW = pw;
                resizeStartH = ph;
                return true;
            }

            // Sidebar: botón de contraer y entradas
            if (Gfx.inside(mx, my, toggleX(), toggleY(), 14, 14)) {
                cfg.sidebarCollapsed = !sidebarWantsCollapsed(cfg);
                cfg.save();
                return true;
            }
            if (inSidebarList(mx, my)) {
                for (SbItem it : sbItems) {
                    if ((it.kind() == KIND_CHANNEL || it.kind() == KIND_FUNC)
                            && Gfx.inside(mx, my, px + 2, it.y(), sbW - 3, it.h())) {
                        onSidebarItem(it);
                        return true;
                    }
                }
                return true;
            }

            // Página Susurros: lista de conversaciones
            if (page == Page.WHISPER && wp.click(mx, my, 0)) return true;

            // Arrastrar desde la cabecera; doble clic = restaurar posición y tamaño
            if (Gfx.inside(mx, my, px, py, pw, HEADER_H)) {
                long now = System.currentTimeMillis();
                if (now - lastHeaderClick < 300) {
                    lastHeaderClick = 0;
                    resetLayout();
                    return true;
                }
                lastHeaderClick = now;
                dragging = true;
                dragOffX = (int) mx - px;
                dragOffY = (int) my - py;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (busy()) return true;
        if (donateOpen || wp.dialogOpen) return super.mouseDragged(mx, my, button, dx, dy);
        if (button == 0) {
            HoennConfig cfg = HoennConfig.get();
            if (dragging) {
                px = clamp((int) mx - dragOffX, MARGIN, Math.max(MARGIN, width - pw - MARGIN));
                py = clamp((int) my - dragOffY, MARGIN, Math.max(MARGIN, height - ph - MARGIN));
                storeLayout(cfg);
                return true;
            }
            if (resizeMode != 0) {
                if ((resizeMode & 1) != 0)
                    pw = clamp(resizeStartW + (int) mx - resizeStartMx, minW(),
                            Math.max(minW(), Math.min(maxW(), width - px - MARGIN)));
                if ((resizeMode & 2) != 0)
                    ph = clamp(resizeStartH + (int) my - resizeStartMy, minH(),
                            Math.max(minH(), Math.min(maxH(), height - py - MARGIN)));
                // Si al llegar al mínimo se sale de la pantalla, se recoloca dentro
                px = clamp(px, MARGIN, Math.max(MARGIN, width - pw - MARGIN));
                py = clamp(py, MARGIN, Math.max(MARGIN, height - ph - MARGIN));
                storeLayout(cfg);
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && (dragging || resizeMode != 0)) {
            dragging = false;
            resizeMode = 0;
            HoennConfig.get().save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (donateOpen || wp.dialogOpen) return true;
        if (vertical != 0) {
            if (page == Page.WHISPER && wp.scroll(mx, my, vertical)) return true;
            if (inSidebar(mx, my)) {
                sbScroll = clamp(sbScroll - (int) Math.signum(vertical) * 12, 0, sbMaxScroll);
            } else {
                scroll += (int) Math.signum(vertical) * 3;
                if (scroll < 0) scroll = 0;
            }
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private boolean anyFieldFocused() {
        return input.isFocused() || nickField.isFocused() || amountField.isFocused();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc: primero cierra popups; si no, cierra el panel
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (donateOpen) {
                closeDonate();
                return true;
            }
            if (wp.dialogOpen) {
                wp.closeDialog(nickField);
                setFocused(null);
                layoutWidgets();
                return true;
            }
            if (emojiOpen) {
                emojiOpen = false;
                return true;
            }
        }

        // La misma tecla que abre el panel lo cierra, salvo que se esté escribiendo en un campo
        if (!anyFieldFocused() && HoennAscensionClient.OPEN.matchesKey(keyCode, scanCode)) {
            if (!openKeyHeld) requestClose(false);
            return true;
        }

        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && input.isFocused()) {
            send();
            return true;
        }
        if (enter && amountField.isFocused()) {
            donateCustom();
            return true;
        }
        if (enter && nickField.isFocused()) {
            wp.submitNick(nickField);
            setFocused(null);
            layoutWidgets();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP && !donateOpen) {
            scroll += visibleLines();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN && !donateOpen) {
            scroll = Math.max(0, scroll - visibleLines());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && !anyFieldFocused() && !donateOpen) {
            cycleChannel(hasShiftDown() ? -1 : 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (HoennAscensionClient.OPEN.matchesKey(keyCode, scanCode)) openKeyHeld = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Cierre normal: animación y luego se cierra del todo. */
    @Override
    public void close() {
        requestClose(false);
    }

    @Override
    public void removed() {
        HoennConfig.get().minimized = minimizing;
        HoennConfig.get().save();
        HoennAscensionClient.STORE.save();
    }
}

// ---------------------------------------------------------------------------------------------
// Panel de la página Susurros. Va en este mismo archivo a propósito: así no depende de otro archivo
// ni de su carpeta/paquete.
// ---------------------------------------------------------------------------------------------
/**
 * Página Susurros: lista de conversaciones (con la cara y el nombre de cada jugador), crear una nueva
 * con buscador de jugadores conectados, y borrar conversaciones.
 */
final class WhisperPane {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.]{1,32}");
    private static final int ROW_H = 18;
    private static final int RGB = 0xA481DE;

    /** Conversación abierta (null = todos los susurros). */
    String peer;
    /** Popup "Nuevo susurro" abierto. */
    boolean dialogOpen;

    private int scroll;
    private String armed;
    private long armedAt;
    private int lx, ly, lw, lh;

    // ---- Geometría ----------------------------------------------------------------------

    void layout(int contentLeft, int top, int contentW, int bottom) {
        lx = contentLeft + 4;
        ly = top;
        lw = contentW >= 300 ? Math.max(100, Math.min(130, contentW / 3)) : 32;
        lh = Math.max(40, bottom - top);
    }

    /** Donde empieza el área de mensajes (a la derecha de la lista). */
    int contentLeft() {
        return lx + lw + 6;
    }

    private boolean expanded() {
        return lw >= 64;
    }

    private int visibleRows() {
        return Math.max(1, (lh - 22) / ROW_H);
    }

    private int totalRows() {
        return 1 + HoennConfig.get().whisperContacts.size();
    }

    private String nameAt(int row) {
        return row == 0 ? null : HoennConfig.get().whisperContacts.get(row - 1);
    }

    private int rowY(int visibleIndex) {
        return ly + 22 + visibleIndex * ROW_H;
    }

    // ---- Lista de conversaciones --------------------------------------------------------

    void render(DrawContext c, Theme th, TextRenderer tr, int mx, int my, boolean modal, String[] tip) {
        c.fill(lx, ly, lx + lw, ly + lh, 0x22000000);

        // Botón Nuevo
        boolean hoverNew = !modal && Gfx.inside(mx, my, lx + 2, ly + 2, lw - 4, 16);
        c.fillGradient(lx + 2, ly + 2, lx + lw - 2, ly + 18,
                Gfx.rgb(RGB, hoverNew ? 0.65f : 0.4f), Gfx.rgb(RGB, hoverNew ? 0.35f : 0.18f));
        String newLabel = Text.translatable("hoennascension.whisper.new").getString();
        String nl = expanded() ? "+ " + newLabel : "+";
        c.drawText(tr, nl, lx + lw / 2 - tr.getWidth(nl) / 2, ly + 6, th.text, !th.light);
        if (hoverNew && !expanded()) tip[0] = newLabel;

        int total = totalRows();
        int vis = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - vis)));
        long now = System.currentTimeMillis();

        for (int r = 0; r < vis && r + scroll < total; r++) {
            int row = r + scroll;
            String name = nameAt(row);
            int y = rowY(r);
            boolean selected = name == null ? peer == null : name.equalsIgnoreCase(peer);
            boolean hover = !modal && Gfx.inside(mx, my, lx + 1, y, lw - 2, ROW_H - 1);
            boolean isArmed = name != null && name.equalsIgnoreCase(armed) && now - armedAt < 2000;

            if (isArmed) {
                c.fill(lx + 1, y, lx + lw - 1, y + ROW_H - 1, 0x66E0463F);
            } else if (selected) {
                c.fillGradient(lx + 1, y, lx + lw - 1, y + ROW_H - 1, Gfx.rgb(RGB, 0.35f), Gfx.rgb(RGB, 0.08f));
                c.fill(lx + 1, y, lx + 3, y + ROW_H - 1, Gfx.rgb(RGB, 1f));
            } else if (hover) {
                c.fill(lx + 1, y, lx + lw - 1, y + ROW_H - 1, Gfx.alpha(th.text, 0.12f));
            }

            int iconX = lx + 5;
            int iconY = y + 3;
            String label;
            if (name == null) {
                Icons.draw(c, "whisper", iconX, iconY, 12, 1f);
                label = Text.translatable("hoennascension.whisper.all.short").getString();
            } else {
                Faces.draw(c, name, iconX, iconY, 12);
                if (Faces.isOnline(name)) c.fill(iconX + 9, iconY + 9, iconX + 13, iconY + 13, 0xFF35C46A);
                label = name;
            }

            int unread = name == null ? 0 : HoennAscensionClient.STORE.unreadPeer(name);
            if (hover && !expanded()) tip[0] = label;

            if (expanded()) {
                int xW = name != null ? 14 : 2;
                String badge = unread > 0 ? (unread > 99 ? "99+" : String.valueOf(unread)) : "";
                int badgeW = badge.isEmpty() ? 0 : tr.getWidth(badge) + 4;
                int max = Math.max(0, lw - 5 - 12 - 5 - xW - badgeW - 3);
                c.drawText(tr, tr.trimToWidth(label, max), iconX + 17, y + 5, selected ? th.text : th.muted, !th.light);
                if (unread > 0) {
                    int bx = lx + lw - xW - badgeW - 2;
                    c.fill(bx, y + 4, bx + badgeW, y + 13, Gfx.rgb(RGB, 0.9f));
                    c.drawText(tr, badge, bx + 2, y + 5, 0xFFFFFFFF, false);
                }
                if (name != null && (hover || isArmed)) {
                    boolean onX = Gfx.inside(mx, my, lx + lw - 14, y + 3, 12, 12);
                    c.drawText(tr, "x", lx + lw - 11, y + 5, isArmed || onX ? 0xFFFF6A6A : th.muted, false);
                }
            } else if (unread > 0) {
                c.fill(iconX + 9, iconY - 1, iconX + 14, iconY + 4, Gfx.rgb(RGB, 1f));
            }
        }
    }

    /** Cabecera de la conversación abierta: cara, nombre y si está conectado. */
    void renderHeader(DrawContext c, Theme th, TextRenderer tr, int x, int y, int w) {
        if (peer == null) {
            Icons.draw(c, "whisper", x, y, 14, 1f);
            c.drawText(tr, tr.trimToWidth(Text.translatable("hoennascension.whisper.all").getString(), Math.max(10, w - 20)),
                    x + 19, y + 3, th.text, !th.light);
        } else {
            Faces.draw(c, peer, x, y, 14);
            boolean on = Faces.isOnline(peer);
            String status = Text.translatable(on ? "hoennascension.whisper.online" : "hoennascension.whisper.offline").getString();
            int statusW = tr.getWidth(status) + 8;
            String n = tr.trimToWidth(peer, Math.max(10, w - 19 - statusW));
            c.drawText(tr, n, x + 19, y + 3, th.text, !th.light);
            c.fill(x + 19 + tr.getWidth(n) + 6, y + 5, x + 19 + tr.getWidth(n) + 10, y + 9, on ? 0xFF35C46A : 0xFF8A94C0);
            c.drawText(tr, status, x + 19 + tr.getWidth(n) + 13, y + 3, th.muted, false);
        }
        c.fill(x, y + 16, x + w, y + 17, Gfx.alpha(th.text, 0.15f));
    }

    /** Clic en la lista. button 0 = seleccionar / nuevo / x; button 1 = borrar. */
    boolean click(double mx, double my, int button) {
        if (!Gfx.inside(mx, my, lx, ly, lw, lh)) return false;
        if (button == 0 && Gfx.inside(mx, my, lx + 2, ly + 2, lw - 4, 16)) {
            dialogOpen = true;
            return true;
        }
        int vis = visibleRows();
        int total = totalRows();
        for (int r = 0; r < vis && r + scroll < total; r++) {
            int row = r + scroll;
            String name = nameAt(row);
            int y = rowY(r);
            if (!Gfx.inside(mx, my, lx + 1, y, lw - 2, ROW_H - 1)) continue;
            if (name == null) {
                if (button == 0) peer = null;
                return true;
            }
            boolean onX = expanded() && Gfx.inside(mx, my, lx + lw - 14, y + 3, 12, 12);
            if (button == 1 || onX) {
                requestDelete(name);
            } else {
                peer = name;
                HoennAscensionClient.STORE.markPeerRead(name);
            }
            return true;
        }
        return true;
    }

    boolean scroll(double mx, double my, double vertical) {
        if (!Gfx.inside(mx, my, lx, ly, lw, lh)) return false;
        scroll -= (int) Math.signum(vertical);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, totalRows() - visibleRows())));
        return true;
    }

    // ---- Borrar -------------------------------------------------------------------------

    /** Primer clic: marca la conversación en rojo; segundo clic (en 2 s): la borra con su historial. */
    private void requestDelete(String name) {
        long now = System.currentTimeMillis();
        if (name.equalsIgnoreCase(armed) && now - armedAt < 2000) {
            HoennConfig.get().removeContact(name);
            HoennAscensionClient.STORE.removePeer(name);
            if (name.equalsIgnoreCase(peer)) peer = null;
            armed = null;
        } else {
            armed = name;
            armedAt = now;
        }
    }

    // ---- Popup "Nuevo susurro" con buscador ---------------------------------------------

    /** Opciones: el nombre escrito (si es válido y no está conectado) y los jugadores conectados que coinciden. */
    private List<String[]> rows(String q) {
        List<String[]> out = new ArrayList<>();
        List<String> online = Faces.onlineMatching(q, 4);
        boolean exact = false;
        for (String n : online) if (n.equalsIgnoreCase(q)) exact = true;
        if (!q.isEmpty() && NAME.matcher(q).matches() && !exact) out.add(new String[]{q, "typed"});
        for (String n : online) {
            if (out.size() >= 4) break;
            out.add(new String[]{n, "online"});
        }
        return out;
    }

    void renderDialog(DrawContext c, Theme th, TextRenderer tr, int mx, int my,
                      int px, int py, int pw, int ph, int dx, int dy, int dw, int dh, TextFieldWidget field) {
        c.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xAA000000);
        Gfx.panel(c, dx, dy, dw, dh, th, 0.95f);

        c.drawText(tr, Text.translatable("hoennascension.whisper.new.title"), dx + 10, dy + 10, th.text, !th.light);
        boolean hoverX = Gfx.inside(mx, my, dx + dw - 18, dy + 6, 12, 12);
        c.drawText(tr, "x", dx + dw - 15, dy + 9, hoverX ? 0xFFFF6A6A : th.muted, false);

        c.fill(dx + 10, dy + 26, dx + dw - 10, dy + 42, th.inputBg);
        Gfx.hBand(c, dx + 10, dy + 26, dx + dw - 10, dy + 27, th, 0.8f, 0f, 1f);

        List<String[]> rows = rows(field.getText().trim());
        if (rows.isEmpty()) {
            c.drawText(tr, tr.trimToWidth(Text.translatable("hoennascension.whisper.none").getString(), dw - 20),
                    dx + 10, dy + 52, th.muted, false);
        }
        for (int i = 0; i < rows.size(); i++) {
            String name = rows.get(i)[0];
            boolean typed = rows.get(i)[1].equals("typed");
            int y = dy + 48 + i * 16;
            if (Gfx.inside(mx, my, dx + 8, y, dw - 16, 16)) c.fill(dx + 8, y, dx + dw - 8, y + 16, Gfx.alpha(th.text, 0.14f));
            Faces.draw(c, name, dx + 12, y + 2, 12);
            String right = Text.translatable(typed ? "hoennascension.whisper.start" : "hoennascension.whisper.online").getString();
            int rw = tr.getWidth(right);
            c.drawText(tr, tr.trimToWidth(name, Math.max(10, dw - 40 - rw)), dx + 28, y + 4, th.text, !th.light);
            c.drawText(tr, right, dx + dw - 12 - rw, y + 4, typed ? th.muted : 0xFF35C46A, false);
        }
    }

    /** @return true si el clic se consumió (el campo de texto lo gestiona el que llama si devuelve false). */
    boolean clickDialog(double mx, double my, int dx, int dy, int dw, int dh, TextFieldWidget field) {
        if (!Gfx.inside(mx, my, dx, dy, dw, dh) || Gfx.inside(mx, my, dx + dw - 18, dy + 6, 12, 12)) {
            closeDialog(field);
            return true;
        }
        List<String[]> rows = rows(field.getText().trim());
        for (int i = 0; i < rows.size(); i++) {
            if (Gfx.inside(mx, my, dx + 8, dy + 48 + i * 16, dw - 16, 16)) {
                create(rows.get(i)[0], field);
                return true;
            }
        }
        return false;
    }

    /** Intro en el campo: abre el nombre escrito o, si está vacío, el primer jugador sugerido. */
    void submitNick(TextFieldWidget field) {
        String q = field.getText().trim();
        if (NAME.matcher(q).matches()) {
            create(q, field);
            return;
        }
        List<String[]> rows = rows(q);
        if (!rows.isEmpty()) create(rows.get(0)[0], field);
    }

    private void create(String name, TextFieldWidget field) {
        String real = Faces.canonicalName(name);
        HoennConfig.get().addContact(real);
        HoennConfig.get().save();
        peer = real;
        HoennAscensionClient.STORE.markPeerRead(real);
        closeDialog(field);
    }

    void closeDialog(TextFieldWidget field) {
        dialogOpen = false;
        field.setText("");
    }
}

// ---------------------------------------------------------------------------------------------
// Caras de jugadores (también en este archivo a propósito, para no depender de otro archivo).
// ---------------------------------------------------------------------------------------------
/** Caras de jugadores: skin real si está conectado, la última conocida o una skin por defecto. */
final class Faces {
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private Faces() {}

    private static PlayerListEntry online(String name) {
        ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
        if (nh == null || name == null) return null;
        for (PlayerListEntry e : nh.getPlayerList()) {
            if (e.getProfile().getName().equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    public static boolean isOnline(String name) {
        return online(name) != null;
    }

    /** Nombre con las mayúsculas reales si el jugador está conectado. */
    public static String canonicalName(String name) {
        PlayerListEntry e = online(name);
        return e != null ? e.getProfile().getName() : name;
    }

    public static Identifier skinFor(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        PlayerListEntry e = online(name);
        if (e != null) {
            Identifier t = e.getSkinTextures().texture();
            CACHE.put(key, t);
            return t;
        }
        Identifier cached = CACHE.get(key);
        if (cached != null) return cached;
        return DefaultSkinHelper.getSkinTextures(Uuids.getOfflinePlayerUuid(name)).texture();
    }

    public static void draw(DrawContext c, String name, int x, int y, int size) {
        Art.drawFace(c, skinFor(name), x, y, size);
    }

    /** Jugadores conectados cuyo nombre contiene la búsqueda (sin contar al propio jugador). */
    public static List<String> onlineMatching(String query, int limit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        List<String> out = new ArrayList<>();
        if (nh == null) return out;
        String self = mc.player != null ? mc.player.getName().getString() : "";
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (PlayerListEntry e : nh.getPlayerList()) {
            String n = e.getProfile().getName();
            if (n.equalsIgnoreCase(self)) continue;
            String l = n.toLowerCase(Locale.ROOT);
            if (q.isEmpty() || l.startsWith(q)) starts.add(n);
            else if (l.contains(q)) contains.add(n);
        }
        starts.sort(String.CASE_INSENSITIVE_ORDER);
        contains.sort(String.CASE_INSENSITIVE_ORDER);
        out.addAll(starts);
        out.addAll(contains);
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}