package dev.hoennascension.ui;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.core.Faces;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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