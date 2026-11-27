package dev.hoennascension.core;

import dev.hoennascension.config.HoennConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Miembros del clan que están conectados: los jugadores de la lista (tab) cuyo nombre mostrado o cuyo equipo
 * contiene la etiqueta del clan (por defecto "hoennascension"). Se actualiza cada segundo.
 */
public final class ClanRoster {
    public record Member(String name, boolean online) {}

    private static long refreshedAt;
    private static List<String> online = new ArrayList<>();
    private static long lastSave;

    private ClanRoster() {}

    private static boolean hasTag(PlayerListEntry e, String name, String tagLower) {
        Text shown = e.getDisplayName();
        if (shown != null && shown.getString().toLowerCase(Locale.ROOT).contains(tagLower)) return true;
        Team team = e.getScoreboardTeam();
        if (team != null) {
            String decorated = Team.decorateName(team, Text.literal(name)).getString();
            if (decorated.toLowerCase(Locale.ROOT).contains(tagLower)) return true;
            if (team.getName().toLowerCase(Locale.ROOT).contains(tagLower)) return true;
        }
        return false;
    }

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < 1000) return;
        refreshedAt = now;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        List<String> out = new ArrayList<>();
        if (nh != null) {
            HoennConfig cfg = HoennConfig.get();
            String tag = cfg.server.clanTag == null ? "" : cfg.server.clanTag.trim().toLowerCase(Locale.ROOT);
            String self = mc.player != null ? mc.player.getName().getString() : "";
            for (PlayerListEntry e : nh.getPlayerList()) {
                String name = e.getProfile().getName();
                if (name.equalsIgnoreCase(self)) continue;
                if (tag.isEmpty() || hasTag(e, name, tag)) out.add(name);
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        online = out;
        remember(out, now);
    }

    /** Recuerda a los miembros vistos para poder mostrarlos como "sin conexión" en Raids. */
    private static void remember(List<String> names, long now) {
        HoennConfig cfg = HoennConfig.get();
        boolean added = false;
        for (String n : names) {
            boolean known = false;
            for (String s : cfg.clanSeen) if (s.equalsIgnoreCase(n)) { known = true; break; }
            if (!known) {
                cfg.clanSeen.add(n);
                added = true;
            }
        }
        while (cfg.clanSeen.size() > 100) cfg.clanSeen.remove(0);
        if (added && now - lastSave > 5000) {
            lastSave = now;
            cfg.save();
        }
    }

    /** Miembros conectados ahora (sin contarte a ti). */
    public static List<String> online() {
        refresh();
        return online;
    }

    /** Conectados primero y, después, los vistos antes que ahora están desconectados. */
    public static List<Member> members() {
        refresh();
        List<Member> out = new ArrayList<>();
        for (String n : online) out.add(new Member(n, true));
        for (String n : HoennConfig.get().clanSeen) {
            boolean on = false;
            for (String o : online) if (o.equalsIgnoreCase(n)) { on = true; break; }
            if (!on) out.add(new Member(n, false));
        }
        return out;
    }
}