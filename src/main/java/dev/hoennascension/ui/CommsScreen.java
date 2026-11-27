package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.core.ChatEntry;
import dev.hoennascension.core.ClanRoster;
import dev.hoennascension.core.Commands;
import dev.hoennascension.core.DonationTracker;
import dev.hoennascension.core.Whispers;
import dev.hoennascension.core.Wondertrade;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Panel principal con sidebar de navegación. Canales: Clan, Spawns, Teleport, Mercado, Wondertrade y Raids;
 * funciones: Donar y Ajustes. El chat normal de Minecraft no se modifica.
 * Movible, redimensionable (con límites) y proporcional al tamaño de la ventana; animaciones de apertura,
 * cierre, minimizado y plegado del sidebar. La X minimiza; Esc cierra del todo.
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
    private static final int TP_CELL_W = 96;
    private static final int TP_CELL_H = 22;
    private static final int WT_H = 58;

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

    private static final int KIND_SECTION = 0, KIND_CHANNEL = 1, KIND_FUNC = 2, KIND_SEP = 3;

    private record Line(OrderedText text, int rgb, boolean mention) {}
    private record SbItem(int kind, String id, String label, int rgb, int y, int h) {}

    private final List<Line> lines = new ArrayList<>();
    private final List<SbItem> sbItems = new ArrayList<>();
    private final RaidsPane rp = new RaidsPane();
    private final List<int[]> subRects = new ArrayList<>(); // {x, y, w, h, índice} de los botones de subfiltro
    private final List<int[]> tpRects = new ArrayList<>();  // {x, y, w, h, índice} de la cuadrícula de Teleport
    private final List<int[]> wtRects = new ArrayList<>();  // {x, y, w, h, acción} de los botones de Wondertrade

    private String activeTab = "clan";
    private String subFilter;      // subfiltro activo del canal (null = todos)
    private int scroll = 0;
    private int tpScroll = 0;
    private boolean donateOpen;
    private boolean emojiOpen;
    private boolean payMode;       // el popup de importe es para pagar a rp.peer

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

    // Cabecera
    private int closeX, pinX, swatchX;
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
    private TextFieldWidget amountField;
    private int placeholderW = -1;
    private int placeholderKey = -1;

    public CommsScreen() {
        this(false);
    }

    public CommsScreen(boolean fromPill) {
        super(Text.translatable("hoennascension.title"));
        this.fromPill = fromPill;
    }

    // ---- Canal activo y tipo de página ---------------------------------------------------

    private ChannelDef activeChannelDef() {
        return HoennConfig.get().channel(activeTab);
    }

    private String view() {
        ChannelDef c = activeChannelDef();
        return c == null ? "log" : c.viewName();
    }

    private boolean isRaids() { return view().equals("raids"); }
    private boolean isTeleport() { return view().equals("teleport"); }
    private boolean isWondertrade() { return view().equals("wondertrade"); }

    /** Canal cuyos mensajes se muestran (Raids usa el canal interno de susurros). */
    private String viewTab() { return isRaids() ? "private" : activeTab; }

    /** Solo se puede escribir en Clan (se envía con /clan chat) y en las conversaciones de Raids. */
    private boolean canWrite() {
        ChannelDef ch = activeChannelDef();
        if (ch == null) return false;
        return isRaids() || (ch.sendPrefix != null && !ch.sendPrefix.isBlank());
    }

    private boolean subRow() {
        ChannelDef ch = activeChannelDef();
        return ch != null && ch.hasSubFilters() && view().equals("log");
    }

    private List<ChannelDef> visibleChannels() {
        List<ChannelDef> out = new ArrayList<>();
        for (ChannelDef c : HoennConfig.get().channels) {
            if (!c.hidden && !c.isFallback()) out.add(c);
        }
        return out;
    }

    private String firstChannel() {
        List<ChannelDef> vis = visibleChannels();
        return vis.isEmpty() ? "clan" : vis.get(0).id;
    }

    // ---- Geometría -----------------------------------------------------------------------

    private int msgLeft() { return isRaids() ? rp.contentLeft() : cx; }
    private int msgW() { return px + pw - msgLeft(); }
    private int barLeft() { return isRaids() ? rp.contentLeft() : cx + 6; }

    private int listBottom() { return py + ph - (canWrite() ? 26 : 8); }
    private int visibleLines() { return Math.max(1, (listBottom() - listTop()) / LINE_H); }
    private int btnY() { return py + (HEADER_H - 14) / 2; }
    private int sendX() { return px + pw - 26; }
    private int emojiX() { return sendX() - 18; }
    private int inputBarY() { return py + ph - 22; }
    private int sbBottom() { return py + ph - 24; }
    private int toggleX() { return px + 1 + sbW / 2 - 7; }
    private int toggleY() { return py + ph - 20; }

    private int tpCols() { return Math.max(1, (msgW() - 8 + 4) / (TP_CELL_W + 4)); }
    private int tpMaxRows() { return ph >= 240 ? 3 : (ph >= 200 ? 2 : 1); }

    private int tpRowsNeeded() {
        int n = ClanRoster.online().size();
        return n == 0 ? 1 : (n + tpCols() - 1) / tpCols();
    }

    private int tpPanelH() { return 16 + Math.min(tpMaxRows(), tpRowsNeeded()) * (TP_CELL_H + 2); }
    private int tpMaxScroll() { return Math.max(0, tpRowsNeeded() - tpMaxRows()); }

    private boolean inTpGrid(double mx, double my) {
        return Gfx.inside(mx, my, cx + 4, py + HEADER_H + 6, msgW() - 8, tpPanelH());
    }

    /** Donde empieza la lista de mensajes: debajo de lo que muestre arriba cada tipo de canal. */
    private int listTop() {
        int top = py + HEADER_H + 6;
        if (isRaids()) return top + 22;
        if (isTeleport()) return top + tpPanelH() + 6;
        if (isWondertrade()) return top + WT_H + 6;
        if (subRow()) return top + 20;
        return top;
    }

    private boolean payVisible() {
        return isRaids() && rp.peer != null && !HoennConfig.get().server.payCommand.isBlank();
    }

    private int payBtnX() { return px + pw - 8 - 56; }
    private int payBtnY() { return py + HEADER_H + 5; }

    private boolean sidebarWantsCollapsed(HoennConfig cfg) {
        return cfg.sidebarCollapsed || pw < AUTO_COLLAPSE_BELOW;
    }

    private boolean inSidebar(double mx, double my) {
        return Gfx.inside(mx, my, px + 1, py + HEADER_H, sbW, ph - HEADER_H - 1);
    }

    private boolean inSidebarList(double mx, double my) {
        return Gfx.inside(mx, my, px + 1, py + HEADER_H, sbW, sbBottom() - (py + HEADER_H));
    }

    /** Ancho del sidebar (animado) y área de contenido resultante. */
    private void computeContent() {
        sbW = SB_COLLAPSED + Math.round((SB_EXPANDED - SB_COLLAPSED) * sbAnim);
        cx = px + sbW + 1;
        cw = pw - sbW - 2;
        rp.layout(cx, py + HEADER_H + 6, cw, py + ph - 24);
    }

    private void computeHeader() {
        HoennConfig cfg = HoennConfig.get();
        int leftEnd = cfg.showLogo
                ? px + 10 + (HEADER_H - 8) * Art.LOGO_W / Art.LOGO_H + 8
                : px + 24 + textRenderer.getWidth(title) + 8;
        int x = px + pw - 6;
        closeX = x - 14;
        x = closeX - 2;
        pinX = x - 14;
        x = pinX - 2;
        swatchX = x - 14;

        // Cabeza y nombre del jugador: solo si caben entre el logo y los botones
        int rightLimit = swatchX - 6;
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

    private int phKey() {
        return isRaids() ? (rp.peer == null ? 2 : 1) : 0;
    }

    private void updatePlaceholder() {
        String key = switch (phKey()) {
            case 2 -> "hoennascension.input.placeholder.raids";
            case 1 -> "hoennascension.input.placeholder.whisper";
            default -> "hoennascension.input.placeholder";
        };
        String full = Text.translatable(key).getString();
        placeholderW = input.getWidth();
        placeholderKey = phKey();
        input.setPlaceholder(Text.literal(textRenderer.trimToWidth(full, Math.max(20, input.getWidth() - 6))));
    }

    private void layoutWidgets() {
        HoennConfig cfg = HoennConfig.get();
        Theme th = Theme.byId(cfg.theme);
        input.setEditableColor(th.text);
        amountField.setEditableColor(th.text);

        boolean write = canWrite() && !donateOpen;
        input.visible = write;
        input.active = write;
        input.setX(barLeft() + 4);
        input.setY(py + ph - 18);
        input.setWidth(Math.max(20, emojiX() - (barLeft() + 4) - 2));
        if (input.getWidth() != placeholderW || placeholderKey != phKey()) updatePlaceholder();

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

        ChannelDef cur = activeChannelDef();
        if (cur == null || cur.hidden) activeTab = firstChannel();

        applyInitialLayout(cfg);

        input = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.empty());
        input.setMaxLength(256);
        input.setDrawsBackground(false);
        addDrawableChild(input);

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

    // ---- Mensajes ------------------------------------------------------------------------

    private void rebuildIfNeeded(Theme th) {
        String tab = viewTab();
        String key = tab + "/" + (isRaids() && rp.peer != null ? rp.peer.toLowerCase(Locale.ROOT) : "")
                + "/" + (subFilter == null ? "" : subFilter);
        int version = HoennAscensionClient.STORE.version;
        if (version == cacheVersion && msgW() == cacheWidth && key.equals(cacheTab)) return;

        cacheVersion = version;
        cacheWidth = msgW();
        cacheTab = key;
        lines.clear();

        HoennConfig cfg = HoennConfig.get();
        int wrapW = Math.max(40, msgW() - 28);
        List<ChatEntry> source;
        if (isRaids()) {
            source = rp.peer != null ? HoennAscensionClient.STORE.viewPeer(rp.peer, 400) : new ArrayList<>();
        } else {
            source = HoennAscensionClient.STORE.view(tab, "", 400);
            if (subFilter != null) {
                List<ChatEntry> filtered = new ArrayList<>();
                for (ChatEntry se : source) if (subFilter.equals(se.sub)) filtered.add(se);
                source = filtered;
            }
        }
        ChannelDef ch = activeChannelDef();
        int rgb = ch != null ? ch.rgb() : 0xFFFFFF;
        int muted = th.muted & 0xFFFFFF;
        for (ChatEntry e : source) {
            MutableText t = Text.empty();
            if (cfg.timestamps) {
                String time = LocalTime.ofInstant(Instant.ofEpochMilli(e.time), ZoneId.systemDefault()).format(TIME);
                t.append(Text.literal(time + " ").setStyle(Style.EMPTY.withColor(muted)));
            }
            t.append(Text.literal(e.shown()));
            String suffix = e.posSuffix();
            if (!suffix.isEmpty()) t.append(Text.literal(suffix).setStyle(Style.EMPTY.withColor(muted)));
            for (OrderedText ot : textRenderer.wrapLines(t, wrapW)) lines.add(new Line(ot, rgb, e.mention));
        }
    }

    private void selectChannel(String id) {
        activeTab = id;
        subFilter = null;
        scroll = 0;
        cacheVersion = -1;
        updatePlaceholder();
        layoutWidgets();
        HoennAscensionClient.STORE.markRead(viewTab());
    }

    private void cycleChannel(int dir) {
        List<ChannelDef> vis = visibleChannels();
        if (vis.isEmpty()) return;
        int i = 0;
        for (int k = 0; k < vis.size(); k++) if (vis.get(k).id.equals(activeTab)) i = k;
        selectChannel(vis.get((i + dir + vis.size()) % vis.size()).id);
    }

    private void send() {
        String s = input.getText().trim();
        if (s.isEmpty() || client == null || client.player == null) return;
        HoennConfig cfg = HoennConfig.get();

        // Raids: con un amigo abierto, lo escrito es un susurro para él
        if (isRaids() && !s.startsWith("/") && !s.startsWith("@")) {
            if (rp.peer != null && whisper(rp.peer, s)) {
                input.setText("");
                scroll = 0;
            }
            return;
        }

        // "@Jugador mensaje" = susurro
        if (s.startsWith("@")) {
            int sp = s.indexOf(' ');
            if (sp > 1 && whisper(s.substring(1, sp), s.substring(sp + 1).trim())) {
                input.setText("");
                scroll = 0;
                return;
            }
        }

        ChannelDef ch = activeChannelDef();
        String prefix = (ch != null && ch.sendPrefix != null) ? ch.sendPrefix : "";
        String full = s.startsWith("/") ? s : prefix + s;
        if (full.startsWith("/")) Commands.run(full);
        else client.player.networkHandler.sendChatMessage(full);
        input.setText("");
        scroll = 0;
    }

    /** Pide un teletransporte a un miembro del clan (comando configurable, por defecto /tpa jugador). */
    private void sendTpa(String name) {
        String tpl = HoennConfig.get().server.tpaCommand;
        if (tpl == null || tpl.isBlank()) return;
        Commands.run(Commands.fill(tpl, "{player}", name));
        HoennAscensionClient.POPUPS.notice("tpa", "teleport", name,
                Text.translatable("hoennascension.tp.sent").getString(), 0x5CFFE0, 3);
    }

    // ---- Sidebar -------------------------------------------------------------------------

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
        for (ChannelDef ch : visibleChannels()) {
            sbItems.add(new SbItem(KIND_CHANNEL, ch.id, ch.name, ch.rgb(), y, 16));
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
            sbItems.add(new SbItem(KIND_FUNC, "donate", Text.translatable("hoennascension.donate").getString(), 0x35C46A, y, 16));
            y += 17;
        }
        sbItems.add(new SbItem(KIND_FUNC, "settings", Text.translatable("hoennascension.page.settings").getString(), 0x8FD8FF, y, 16));
        y += 17;

        int contentH = (y + sbScroll) - baseTop;
        int viewH = sbBottom() - (py + HEADER_H) - 4;
        sbMaxScroll = Math.max(0, contentH - viewH);
        if (sbScroll > sbMaxScroll) sbScroll = sbMaxScroll;
    }

    private boolean itemActive(SbItem it) {
        if (it.kind() == KIND_CHANNEL) return it.id().equals(activeTab);
        return it.kind() == KIND_FUNC && it.id().equals("donate") && donateOpen;
    }

    private int unreadFor(SbItem it) {
        if (itemActive(it) || it.kind() != KIND_CHANNEL) return 0;
        ChannelDef ch = HoennConfig.get().channel(it.id());
        String key = ch != null && ch.viewName().equals("raids") ? "private" : it.id();
        return HoennAscensionClient.STORE.unread(key);
    }

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
                    String icon = it.id();
                    if (it.kind() == KIND_CHANNEL) {
                        ChannelDef chd = cfg.channel(it.id());
                        if (chd != null) icon = chd.iconName();
                    }
                    if (!Icons.draw(c, icon, sx + 6, it.y() + 2, 12, ia)) {
                        Gfx.diamond(c, sx + 12, it.y() + 8, 3, Gfx.rgb(rgb, ia));
                    }

                    int unread = unreadFor(it);
                    int badgeW = 0;
                    String badge = "";
                    if (unread > 0) {
                        badge = unread > 99 ? "99+" : String.valueOf(unread);
                        badgeW = tr.getWidth(badge) + 4;
                    }
                    if (expanded) {
                        String label = tr.trimToWidth(it.label(), Math.max(0, sbW - 21 - badgeW - 6));
                        c.drawText(tr, label, sx + 21, it.y() + 4, active ? th.text : th.muted, !th.light);
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

    private void onSidebarItem(SbItem it) {
        if (it.kind() == KIND_CHANNEL) {
            selectChannel(it.id());
        } else if (it.kind() == KIND_FUNC) {
            if (it.id().equals("donate")) openDonate();
            else if (client != null) client.setScreen(new HoennConfigScreen(this));
        }
    }

    // ---- Utilidades y disposición de la ventana --------------------------------

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // Límites de tamaño: los de la config, siempre acotados por el tamaño real de la pantalla
    private int minW() { return Math.min(HoennConfig.get().windowMinWidth, Math.max(120, width - 2 * MARGIN)); }

    private int minH() { return Math.min(HoennConfig.get().windowMinHeight, Math.max(80, height - 2 * MARGIN)); }

    private int maxW() { return Math.max(minW(), Math.min(HoennConfig.get().windowMaxWidth, width - 2 * MARGIN)); }

    private int maxH() { return Math.max(minH(), Math.min(HoennConfig.get().windowMaxHeight, height - 2 * MARGIN)); }

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

    // ---- Popups de importe y selector de emojis (geometría) --------------------

    // Geometría del popup de donación (centrado en el panel)
    private int dlgW() { return Math.max(180, Math.min(270, pw - 24)); }

    private int dlgH() { return 118; }

    private int dlgX() { return px + (pw - dlgW()) / 2; }

    private int dlgY() { return py + Math.max(HEADER_H + 4, (ph - dlgH()) / 2); }

    private int dlgFieldY() { return dlgY() + 78; }

    private int dlgConfirmX() { return dlgX() + dlgW() - 10 - 66; }

    /** Cantidades rápidas del popup: las de pago o las de donación según el modo. */
    private List<Integer> amounts() {
        return payMode ? HoennConfig.get().server.payAmounts : HoennConfig.get().server.donateAmounts;
    }

    private int dlgQuickCount() {
        int fit = Math.max(1, (dlgW() - 20 + 4) / (34 + 4));
        return Math.min(Math.min(6, fit), amounts().size());
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

    // ---- Animación de apertura / cierre / minimizado ---------------------------

    private boolean isOpenKeyDown() {
        try {
            long handle = client.getWindow().getHandle();
            int code = InputUtil.fromTranslationKey(HoennAscensionClient.OPEN.getBoundKeyTranslationKey()).getCode();
            return InputUtil.isKeyPressed(handle, code);
        } catch (Exception e) {
            return false;
        }
    }

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

    // ---- Dinero: donar, pagar y susurrar ---------------------------------------

    private boolean whisper(String player, String msg) {
        String tpl = HoennConfig.get().server.privateCommand;
        if (msg.isEmpty() || !PLAYER.matcher(player).matches() || tpl == null || tpl.isBlank()) return false;
        Commands.run(Commands.fill(tpl, "{player}", player, "{message}", msg));
        Whispers.sent(player, msg);
        return true;
    }

    private void openDonate() {
        payMode = false;
        donateOpen = true;
        emojiOpen = false;
        layoutWidgets();
    }

    private void closeDonate() {
        donateOpen = false;
        payMode = false;
        amountField.setText("");
        setFocused(null);
        layoutWidgets();
    }

    /** Ejecuta el comando de donación del servidor (por defecto: /clan deposit {cantidad}). */
    private void donate(long value) {
        if (payMode) {
            pay(value);
            return;
        }
        Commands.run(Commands.fill(HoennConfig.get().server.donateCommand, "{amount}", String.valueOf(value)));
        DonationTracker.start(value);
        closeDonate();
    }

    private void openPay() {
        if (rp.peer == null) return;
        payMode = true;
        donateOpen = true;
        emojiOpen = false;
        layoutWidgets();
    }

    /** Paga al jugador de la conversación abierta con el comando del servidor (/pay jugador pokedolares cantidad). */
    private void pay(long value) {
        String peer = rp.peer;
        String tpl = HoennConfig.get().server.payCommand;
        if (peer != null && !tpl.isBlank()) {
            Commands.run(Commands.fill(tpl, "{player}", peer, "{amount}", String.valueOf(value)));
            DonationTracker.startPay(peer, value);
        }
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

    // ---- Widgets auxiliares ----------------------------------------------------

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

        int titleX = dx + 10;
        if (payMode && rp.peer != null) {
            Faces.draw(c, rp.peer, dx + 10, dy + 7, 14);
            titleX = dx + 28;
        }
        Text dlgTitle = payMode ? Text.translatable("hoennascension.pay.title", rp.peer == null ? "" : rp.peer)
                : Text.translatable("hoennascension.donate.title");
        c.drawText(tr, tr.trimToWidth(dlgTitle.getString(), Math.max(20, dw - 36 - (titleX - dx))), titleX, dy + 10,
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
            String label = tr.trimToWidth(shortLabel(amounts().get(i)), Math.max(8, bw - 4));
            c.drawText(tr, label, bx + bw / 2 - tr.getWidth(label) / 2, dy + 44, 0xFFFFFFFF, true);
        }

        c.drawText(tr, Text.translatable("hoennascension.donate.custom"), dx + 10, dy + 64, th.muted, false);
        int fy = dlgFieldY();
        c.fill(dx + 10, fy, dlgConfirmX() - 4, fy + 16, th.inputBg);
        Gfx.hBand(c, dx + 10, fy, dlgConfirmX() - 4, fy + 1, th, 0.8f, 0f, 1f);

        boolean hoverConfirm = Gfx.inside(mx, my, dlgConfirmX(), fy, 66, 16);
        c.fillGradient(dlgConfirmX(), fy, dlgConfirmX() + 66, fy + 16,
                hoverConfirm ? 0xFF35C46A : 0xFF22984F, hoverConfirm ? 0xFF1E8F4A : 0xFF146B36);
        String conf = Text.translatable(payMode ? "hoennascension.pay" : "hoennascension.donate.confirm").getString();
        c.drawText(tr, conf, dlgConfirmX() + 33 - tr.getWidth(conf) / 2, fy + 4, 0xFFFFFFFF, true);

        String command = payMode ? cfg.server.payCommand.replace("{player}", rp.peer == null ? "?" : rp.peer)
                : cfg.server.donateCommand;
        String preview = tr.trimToWidth(command.replace("{amount}", "<n>"), Math.max(20, dw - 20));
        c.drawText(tr, preview, dx + 10, dy + 102, Gfx.alpha(th.muted, 0.8f), false);
    }

    private void renderPayButton(DrawContext c, int mx, int my) {
        boolean hover = !donateOpen && Gfx.inside(mx, my, payBtnX(), payBtnY(), 56, 16);
        c.fillGradient(payBtnX(), payBtnY(), payBtnX() + 56, payBtnY() + 16,
                hover ? 0xFF35C46A : 0xFF22984F, hover ? 0xFF1E8F4A : 0xFF146B36);
        String label = Text.translatable("hoennascension.pay").getString();
        c.drawText(textRenderer, label, payBtnX() + 28 - textRenderer.getWidth(label) / 2, payBtnY() + 4,
                0xFFFFFFFF, true);
    }

    private String subLabel(String sub) {
        if (sub == null) return Text.translatable("hoennascension.sub.all").getString();
        String key = "hoennascension.sub." + sub;
        return I18n.hasTranslation(key) ? I18n.translate(key) : sub;
    }

    /** Botones de subfiltro del canal abierto (p. ej. Todos / Legendarios / Megas). */
    private void renderSubFilters(DrawContext c, Theme th, int mx, int my) {
        ChannelDef ch = activeChannelDef();
        if (ch == null) return;
        TextRenderer tr = textRenderer;
        subRects.clear();
        List<String> options = new ArrayList<>();
        options.add(null);
        options.addAll(ch.subFilters);
        int x = msgLeft() + 4;
        int y = py + HEADER_H + 6;
        for (int i = 0; i < options.size(); i++) {
            String sub = options.get(i);
            String label = subLabel(sub);
            int w = tr.getWidth(label) + 14;
            if (x + w > px + pw - 8) break;
            subRects.add(new int[]{x, y, w, 14, i});
            boolean active = sub == null ? subFilter == null : sub.equals(subFilter);
            boolean hover = !donateOpen && Gfx.inside(mx, my, x, y, w, 14);
            c.fill(x, y, x + w, y + 14, Gfx.rgb(ch.rgb(), active ? 0.55f : (hover ? 0.32f : 0.18f)));
            if (active) c.fill(x, y + 13, x + w, y + 14, Gfx.rgb(ch.rgb(), 1f));
            c.drawText(tr, label, x + 7, y + 3, active ? th.text : th.muted, !th.light);
            x += w + 4;
        }
    }

    // ---- Dibujo --------------------------------------------------------------------------

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
        if (isRaids() && rp.peer != null) HoennAscensionClient.STORE.markPeerRead(rp.peer);
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

        // Cabeza y nombre del jugador
        if (showUser && client != null && client.player != null) {
            c.fill(userX, btnY() - 1, userX + userW, btnY() + 15, Gfx.alpha(th.text, 0.10f));
            Identifier skin = client.player.getSkinTextures().texture();
            Art.drawFace(c, skin, userX + 3, btnY() + 1, 12);
            c.drawText(tr, userName, userX + 3 + 12 + 4, btnY() + 3, th.text, !th.light);
        }

        // Tema: un solo botón con los colores del tema actual (pulsa para pasar al siguiente)
        boolean hoverSwatch = Gfx.inside(mx, my, swatchX, btnY(), 14, 14);
        Gfx.diamond(c, swatchX + 7, btnY() + 7, hoverSwatch ? 7 : 6, th.edgeAt(0.85f));
        Gfx.diamond(c, swatchX + 7, btnY() + 7, 3, th.edgeAt(0.1f));

        boolean hoverPin = Gfx.inside(mx, my, pinX, btnY(), 14, 14);
        if (cfg.hudPinned) Gfx.diamond(c, pinX + 7, btnY() + 7, 5, th.edgeAt(0.5f));
        else Gfx.diamondOutline(c, pinX + 7, btnY() + 7, 5, hoverPin ? th.text : th.muted);

        boolean hoverClose = Gfx.inside(mx, my, closeX, btnY(), 14, 14);
        c.drawText(tr, "x", closeX + 5, btnY() + 3, hoverClose ? 0xFFFF6A6A : th.muted, false);

        // Sidebar y contenido según el tipo de canal
        renderSidebar(c, th, mx, my, clip);
        if (isRaids()) renderRaidsTop(c, th, mx, my);
        else if (isTeleport()) renderTeleportGrid(c, th, mx, my);
        else if (isWondertrade()) renderWondertrade(c, th, mx, my);
        else if (subRow()) renderSubFilters(c, th, mx, my);
        renderMessages(c, th, clip);

        // Barra de entrada (solo en Clan y Raids) con botón de emojis y de enviar
        boolean hoverEmoji = false;
        if (canWrite()) {
            int ib = inputBarY();
            c.fill(barLeft(), ib, px + pw - 8, ib + 16, th.inputBg);
            Gfx.hBand(c, barLeft(), ib, px + pw - 8, ib + 1, th, 0.8f, 0f, 1f);
            hoverEmoji = !donateOpen && Gfx.inside(mx, my, emojiX(), ib, 18, 16);
            String smile = EMOJIS[0];
            c.drawText(tr, smile, emojiX() + 9 - tr.getWidth(smile) / 2, ib + 4,
                    hoverEmoji || emojiOpen ? th.text : th.muted, false);
            boolean hoverSend = !donateOpen && Gfx.inside(mx, my, sendX(), ib, 18, 16);
            Gfx.diamond(c, sendX() + 9, ib + 8, 4, hoverSend ? th.text : th.edgeAt(0.85f));
        }
        if (emojiOpen && canWrite() && !donateOpen) renderEmojiPicker(c, th, mx, my);

        // Agarre de redimensionado
        boolean hoverGrip = Gfx.inside(mx, my, px + pw - EDGE - 6, py + ph - EDGE - 6, EDGE + 8, EDGE + 8);
        int grip = hoverGrip || resizeMode != 0 ? th.text : th.muted;
        for (int r = 0; r < 3; r++)
            for (int k = 0; k < 3 - r; k++)
                c.fill(px + pw - 5 - k * 3, py + ph - 5 - r * 3, px + pw - 4 - k * 3, py + ph - 4 - r * 3, grip);

        // Popup de importe (donar / pagar): encima de todo; el campo de cantidad se dibuja después como widget
        if (donateOpen) renderDonateDialog(c, th, mx, my);

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
        if (animating || donateOpen) return;

        boolean hoverBanner = overBanner(mx, my);
        if (hoverPin) c.drawTooltip(tr, Text.translatable(cfg.hudPinned
                ? "hoennascension.tooltip.pin.on" : "hoennascension.tooltip.pin.off"), mx, my);
        else if (hoverSwatch) c.drawTooltip(tr, Text.literal(th.displayName()), mx, my);
        else if (hoverClose) c.drawTooltip(tr, Text.translatable("hoennascension.tooltip.minimize"), mx, my);
        else if (hoverEmoji && !emojiOpen) c.drawTooltip(tr, Text.translatable("hoennascension.tooltip.emoji"), mx, my);
        else if (hoverBanner) c.drawTooltip(tr, Text.translatable(cfg.bannerHidden
                ? "hoennascension.tooltip.banner.show" : "hoennascension.tooltip.banner.hide"), mx, my);
        else if (hoverTip != null) c.drawTooltip(tr, Text.literal(hoverTip), mx, my);
    }

    private void renderMessages(DrawContext c, Theme th, boolean clip) {
        TextRenderer tr = textRenderer;
        int lt = listTop();
        int lb = listBottom();
        int ml = msgLeft();
        int vis = visibleLines();
        int maxScroll = Math.max(0, lines.size() - vis);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        c.fill(ml + 4, lt - 2, px + pw - 4, lb + 2, 0x22000000);
        if (lines.isEmpty()) {
            String key = isRaids() && rp.peer == null ? "hoennascension.raids.pick" : "hoennascension.empty";
            c.drawText(tr, tr.trimToWidth(Text.translatable(key).getString(), Math.max(20, px + pw - ml - 20)),
                    ml + 12, lt + 4, th.muted, false);
        }
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

    /** Raids: lista de amigos (miembros del clan) y cabecera de la conversación abierta, con el botón Pagar. */
    private void renderRaidsTop(DrawContext c, Theme th, int mx, int my) {
        String[] tip = {null};
        rp.render(c, th, textRenderer, mx, my, donateOpen, tip);
        if (tip[0] != null) hoverTip = tip[0];
        int hx = msgLeft() + 4;
        int hw = px + pw - 8 - hx - (payVisible() ? 62 : 0);
        rp.renderHeader(c, th, textRenderer, hx, py + HEADER_H + 6, hw);
        if (payVisible()) renderPayButton(c, mx, my);
    }

    /** Teleport: cuadrícula con los jugadores del clan conectados; pulsa uno para pedirle un teletransporte. */
    private void renderTeleportGrid(DrawContext c, Theme th, int mx, int my) {
        TextRenderer tr = textRenderer;
        tpRects.clear();
        List<String> members = ClanRoster.online();
        tpScroll = Math.max(0, Math.min(tpScroll, tpMaxScroll()));
        int x0 = cx + 4;
        int y0 = py + HEADER_H + 6;
        int w = msgW() - 8;
        c.fill(x0, y0, x0 + w, y0 + tpPanelH(), 0x22000000);
        c.drawText(tr, tr.trimToWidth(Text.translatable("hoennascension.tp.title", members.size()).getString(), w - 12),
                x0 + 6, y0 + 4, th.muted, false);
        if (members.isEmpty()) {
            String none = Text.translatable("hoennascension.tp.none", HoennConfig.get().server.clanTag).getString();
            c.drawText(tr, tr.trimToWidth(none, w - 12), x0 + 6, y0 + 22, th.muted, false);
            return;
        }
        int cols = tpCols();
        int start = tpScroll * cols;
        for (int i = start; i < members.size() && i < start + cols * tpMaxRows(); i++) {
            int r = (i - start) / cols;
            int col = (i - start) % cols;
            int x = x0 + 4 + col * (TP_CELL_W + 4);
            int y = y0 + 16 + r * (TP_CELL_H + 2);
            boolean hover = !donateOpen && Gfx.inside(mx, my, x, y, TP_CELL_W, TP_CELL_H);
            c.fill(x, y, x + TP_CELL_W, y + TP_CELL_H, Gfx.rgb(0x5CFFE0, hover ? 0.30f : 0.14f));
            Faces.draw(c, members.get(i), x + 3, y + 3, 16);
            c.drawText(tr, tr.trimToWidth(members.get(i), TP_CELL_W - 26), x + 23, y + 7, th.text, !th.light);
            tpRects.add(new int[]{x, y, TP_CELL_W, TP_CELL_H, i});
        }
    }

    private static String hms(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    /** Wondertrade: cuenta atrás hasta el próximo intercambio, barra de progreso y botones. */
    private void renderWondertrade(DrawContext c, Theme th, int mx, int my) {
        TextRenderer tr = textRenderer;
        wtRects.clear();
        int x0 = cx + 4;
        int y0 = py + HEADER_H + 6;
        int w = msgW() - 8;
        c.fill(x0, y0, x0 + w, y0 + WT_H, 0x22000000);
        Gfx.hBand(c, x0, y0, x0 + w, y0 + 1, th, 0.8f, 0f, 1f);

        int btnW = Math.min(104, Math.max(70, w / 3));
        int leftW = w - btnW - 20;
        long rem = Wondertrade.remainingMs();
        String big;
        int color;
        if (rem < 0) {
            big = "--:--:--";
            color = th.muted;
        } else if (rem == 0) {
            big = Text.translatable("hoennascension.wt.available").getString();
            color = 0xFF35C46A;
        } else {
            big = hms(rem);
            color = th.text;
        }
        c.drawText(tr, tr.trimToWidth(Text.translatable("hoennascension.wt.next").getString(), leftW),
                x0 + 8, y0 + 6, th.muted, false);
        float scale = tr.getWidth(big) * 2 <= leftW ? 2f : 1f;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x0 + 8, y0 + 17, 0f);
        m.scale(scale, scale, 1f);
        c.drawText(tr, big, 0, 0, color, !th.light);
        m.pop();

        int by = y0 + 42;
        c.fill(x0 + 8, by, x0 + 8 + leftW, by + 6, 0x44000000);
        float prog = rem < 0 ? 0f : (rem == 0 ? 1f : 1f - Math.min(1f, rem / (float) Wondertrade.cooldownMs()));
        int fill = Math.round(leftW * prog);
        if (fill > 0) Gfx.hBand(c, x0 + 8, by, x0 + 8 + fill, by + 6, th, 0.9f, 0f, prog);
        if (rem < 0) {
            c.drawText(tr, tr.trimToWidth(Text.translatable("hoennascension.wt.unknown").getString(), leftW),
                    x0 + 8, by + 8, th.muted, false);
        }

        int bx = x0 + w - btnW - 8;
        boolean h1 = !donateOpen && Gfx.inside(mx, my, bx, y0 + 8, btnW, 18);
        c.fillGradient(bx, y0 + 8, bx + btnW, y0 + 26,
                h1 ? 0xFF9D4EDD : 0xFF6F35B0, h1 ? 0xFF6F35B0 : 0xFF4A2380);
        String cmd = HoennConfig.get().server.wondertradeCommand;
        String l1 = tr.trimToWidth(cmd.isBlank() ? "/wondertrade" : cmd, btnW - 6);
        c.drawText(tr, l1, bx + btnW / 2 - tr.getWidth(l1) / 2, y0 + 13, 0xFFFFFFFF, true);
        wtRects.add(new int[]{bx, y0 + 8, btnW, 18, 0});

        boolean h2 = !donateOpen && Gfx.inside(mx, my, bx, y0 + 32, btnW, 16);
        c.fill(bx, y0 + 32, bx + btnW, y0 + 48, Gfx.alpha(th.text, h2 ? 0.22f : 0.12f));
        String l2 = tr.trimToWidth(Text.translatable("hoennascension.wt.reset").getString(), btnW - 6);
        c.drawText(tr, l2, bx + btnW / 2 - tr.getWidth(l2) / 2, y0 + 36, th.text, false);
        wtRects.add(new int[]{bx, y0 + 32, btnW, 16, 1});
    }

    // ---- Entrada -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (busy()) return true;
        HoennConfig cfg = HoennConfig.get();

        // Notificaciones con botones (p. ej. /tpa): se aceptan o rechazan pulsándolas
        if (button == 0 && HoennAscensionClient.POPUPS.click(mx, my)) return true;

        // Clic derecho sobre un canal del sidebar: ocultar / mostrar sus mensajes en el chat normal
        if (button == 1 && !donateOpen) {
            computeHeader();
            computeContent();
            buildSidebar();
            if (inSidebarList(mx, my)) {
                for (SbItem it : sbItems) {
                    if (it.kind() == KIND_CHANNEL && Gfx.inside(mx, my, px + 2, it.y(), sbW - 3, it.h())) {
                        ChannelDef ch = HoennConfig.get().channel(it.id());
                        if (ch != null) {
                            ch.hideFromVanilla = !ch.hideFromVanilla;
                            HoennConfig.get().save();
                            HoennAscensionClient.POPUPS.notice("hide", ch.iconName(), ch.name,
                                    Text.translatable(ch.hideFromVanilla
                                            ? "hoennascension.hidden.on" : "hoennascension.hidden.off").getString(),
                                    ch.rgb(), 2);
                        }
                        return true;
                    }
                }
            }
        }

        if (button == 0) {
            computeHeader();
            computeContent();
            layoutDecor(cfg);
            buildSidebar();

            // Popup de importe (donar / pagar): es modal
            if (donateOpen) {
                int dx = dlgX(), dy = dlgY(), dw = dlgW(), dh = dlgH();
                if (!Gfx.inside(mx, my, dx, dy, dw, dh) || Gfx.inside(mx, my, dx + dw - 18, dy + 6, 12, 12)) {
                    closeDonate();
                    return true;
                }
                for (int i = 0; i < dlgQuickCount(); i++) {
                    if (Gfx.inside(mx, my, dlgQuickX(i), dy + 38, dlgQuickW(), 20)) {
                        donate(amounts().get(i));
                        return true;
                    }
                }
                if (Gfx.inside(mx, my, dlgConfirmX(), dlgFieldY(), 66, 16)) {
                    donateCustom();
                    return true;
                }
                return super.mouseClicked(mx, my, button); // el campo de cantidad
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
            if (Gfx.inside(mx, my, swatchX, btnY(), 14, 14)) {
                cfg.theme = Theme.byId(cfg.theme).next().id;
                cfg.save();
                return true;
            }
            if (canWrite() && Gfx.inside(mx, my, emojiX(), inputBarY(), 18, 16)) {
                emojiOpen = true;
                return true;
            }
            if (canWrite() && Gfx.inside(mx, my, sendX(), inputBarY(), 18, 16)) {
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

            // Raids: botón Pagar y lista de amigos
            if (payVisible() && Gfx.inside(mx, my, payBtnX(), payBtnY(), 56, 16)) {
                openPay();
                return true;
            }
            if (isRaids() && rp.click(mx, my)) return true;

            // Botones de subfiltro del canal (Spawns: Legendarios / Megas; Mercado: Ventas / Compras)
            if (subRow()) {
                ChannelDef sch = activeChannelDef();
                for (int[] r : subRects) {
                    if (sch != null && Gfx.inside(mx, my, r[0], r[1], r[2], r[3])) {
                        subFilter = r[4] == 0 ? null : sch.subFilters.get(r[4] - 1);
                        cacheVersion = -1;
                        scroll = 0;
                        return true;
                    }
                }
            }

            // Teleport: pulsar a un miembro del clan le pide un teletransporte
            if (isTeleport()) {
                List<String> members = ClanRoster.online();
                for (int[] r : tpRects) {
                    if (Gfx.inside(mx, my, r[0], r[1], r[2], r[3]) && r[4] < members.size()) {
                        sendTpa(members.get(r[4]));
                        return true;
                    }
                }
                if (inTpGrid(mx, my)) return true;
            }

            // Wondertrade: botones
            if (isWondertrade()) {
                for (int[] r : wtRects) {
                    if (Gfx.inside(mx, my, r[0], r[1], r[2], r[3])) {
                        if (r[4] == 0) {
                            Commands.run(cfg.server.wondertradeCommand);
                        } else {
                            Wondertrade.startCooldown();
                        }
                        return true;
                    }
                }
            }

            // Arrastrar la ventana desde la cabecera (no hay acción de doble clic: nunca recoloca la ventana)
            if (Gfx.inside(mx, my, px, py, pw, HEADER_H)) {
                dragging = true;
                dragOffX = (int) mx - px;
                dragOffY = (int) my - py;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (donateOpen) return true;
        if (vertical != 0) {
            if (isRaids() && rp.scroll(mx, my, vertical)) return true;
            if (isTeleport() && inTpGrid(mx, my)) {
                tpScroll = clamp(tpScroll - (int) Math.signum(vertical), 0, tpMaxScroll());
                return true;
            }
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
        return input.isFocused() || amountField.isFocused();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc: primero cierra popups; si no, cierra el panel
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (donateOpen) {
                closeDonate();
                return true;
            }
            if (emojiOpen) {
                emojiOpen = false;
                return true;
            }
        }

        if (!anyFieldFocused()) {
            // Y / N aceptan o rechazan la solicitud con botones más reciente (p. ej. /tpa)
            if (HoennAscensionClient.ACCEPT.matchesKey(keyCode, scanCode) && HoennAscensionClient.POPUPS.act(true)) return true;
            if (HoennAscensionClient.DENY.matchesKey(keyCode, scanCode) && HoennAscensionClient.POPUPS.act(false)) return true;

            // La misma tecla que abre el panel lo cierra, salvo que se esté escribiendo en un campo
            if (HoennAscensionClient.OPEN.matchesKey(keyCode, scanCode)) {
                if (!openKeyHeld) requestClose(false);
                return true;
            }
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

    // ---- Arrastre, cierre y guardado -------------------------------------------

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (busy()) return true;
        if (donateOpen) return super.mouseDragged(mx, my, button, dx, dy);
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
// Raids: lista de amigos = miembros del clan (con la etiqueta) y conversaciones con ellos. Va en este mismo
// archivo a propósito, para no depender de otro archivo ni de su carpeta/paquete.
// ---------------------------------------------------------------------------------------------
final class RaidsPane {
    private static final int ROW_H = 18;
    private static final int RGB = 0xA481DE;

    /** Amigo con la conversación abierta (null = ninguno). */
    String peer;

    private int scroll;
    private int lx, ly, lw, lh;
    private List<ClanRoster.Member> rows = new ArrayList<>();

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
        return Math.max(1, (lh - 6) / ROW_H);
    }

    private int rowY(int visibleIndex) {
        return ly + 4 + visibleIndex * ROW_H;
    }

    void render(DrawContext c, Theme th, TextRenderer tr, int mx, int my, boolean modal, String[] tip) {
        rows = ClanRoster.members();
        c.fill(lx, ly, lx + lw, ly + lh, 0x22000000);

        if (rows.isEmpty()) {
            if (expanded()) {
                String tag = HoennConfig.get().server.clanTag;
                int ty = ly + 6;
                for (OrderedText l : tr.wrapLines(Text.translatable("hoennascension.raids.empty", tag), lw - 10)) {
                    if (ty > ly + lh - 10) break;
                    c.drawText(tr, l, lx + 5, ty, th.muted, false);
                    ty += 10;
                }
            }
            return;
        }

        int vis = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - vis)));
        for (int r = 0; r < vis && r + scroll < rows.size(); r++) {
            ClanRoster.Member m = rows.get(r + scroll);
            int y = rowY(r);
            boolean selected = m.name().equalsIgnoreCase(peer);
            boolean hover = !modal && Gfx.inside(mx, my, lx + 1, y, lw - 2, ROW_H - 1);
            if (selected) {
                c.fillGradient(lx + 1, y, lx + lw - 1, y + ROW_H - 1, Gfx.rgb(RGB, 0.35f), Gfx.rgb(RGB, 0.08f));
                c.fill(lx + 1, y, lx + 3, y + ROW_H - 1, Gfx.rgb(RGB, 1f));
            } else if (hover) {
                c.fill(lx + 1, y, lx + lw - 1, y + ROW_H - 1, Gfx.alpha(th.text, 0.12f));
            }

            int iconX = lx + 5;
            int iconY = y + 3;
            Faces.draw(c, m.name(), iconX, iconY, 12);
            if (m.online()) c.fill(iconX + 9, iconY + 9, iconX + 13, iconY + 13, 0xFF35C46A);
            else c.fill(iconX, iconY, iconX + 12, iconY + 12, 0x88000000);

            int unread = HoennAscensionClient.STORE.unreadPeer(m.name());
            if (hover && !expanded()) tip[0] = m.name();
            if (expanded()) {
                String badge = unread > 0 ? (unread > 99 ? "99+" : String.valueOf(unread)) : "";
                int badgeW = badge.isEmpty() ? 0 : tr.getWidth(badge) + 4;
                int max = Math.max(0, lw - 5 - 12 - 5 - badgeW - 6);
                int col = m.online() ? (selected ? th.text : th.muted) : Gfx.alpha(th.muted, 0.55f);
                c.drawText(tr, tr.trimToWidth(m.name(), max), iconX + 17, y + 5, col, !th.light);
                if (unread > 0) {
                    int bx = lx + lw - badgeW - 4;
                    c.fill(bx, y + 4, bx + badgeW, y + 13, Gfx.rgb(RGB, 0.9f));
                    c.drawText(tr, badge, bx + 2, y + 5, 0xFFFFFFFF, false);
                }
            } else if (unread > 0) {
                c.fill(iconX + 9, iconY - 1, iconX + 14, iconY + 4, Gfx.rgb(RGB, 1f));
            }
        }
    }

    /** Cabecera de la conversación abierta: cara, nombre y si está conectado. */
    void renderHeader(DrawContext c, Theme th, TextRenderer tr, int x, int y, int w) {
        if (peer == null) {
            c.drawText(tr, tr.trimToWidth(Text.translatable("hoennascension.raids.pick").getString(), Math.max(10, w)),
                    x, y + 3, th.muted, false);
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

    /** Clic en la lista: abre la conversación con ese amigo. */
    boolean click(double mx, double my) {
        if (!Gfx.inside(mx, my, lx, ly, lw, lh)) return false;
        int vis = visibleRows();
        for (int r = 0; r < vis && r + scroll < rows.size(); r++) {
            if (Gfx.inside(mx, my, lx + 1, rowY(r), lw - 2, ROW_H - 1)) {
                peer = rows.get(r + scroll).name();
                HoennAscensionClient.STORE.markPeerRead(peer);
                return true;
            }
        }
        return true;
    }

    boolean scroll(double mx, double my, double vertical) {
        if (!Gfx.inside(mx, my, lx, ly, lw, lh)) return false;
        scroll -= (int) Math.signum(vertical);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
        return true;
    }
}

// ---------------------------------------------------------------------------------------------
// Caras de jugadores (también en este archivo a propósito, para no depender de otro archivo).
// ---------------------------------------------------------------------------------------------
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

    /** Primer jugador conectado cuyo nombre aparece en el texto (para mostrar su cara en una solicitud). */
    static String mentioned(String text) {
        ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
        if (nh == null || text == null) return null;
        for (PlayerListEntry e : nh.getPlayerList()) {
            String n = e.getProfile().getName();
            if (n.length() >= 3 && Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(n) + "(?![A-Za-z0-9_])",
                    Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                return n;
            }
        }
        return null;
    }
}