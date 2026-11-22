package dev.hoennascension.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Caras de jugadores: skin real si está conectado, la última conocida o una skin por defecto. */
public final class Faces {
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