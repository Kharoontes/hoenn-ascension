package dev.hoennascension.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MessageStore {
    private static final Gson GSON = new Gson();
    private final List<ChatEntry> all = new ArrayList<>();
    private final Map<String, Integer> unread = new HashMap<>();
    private final Map<String, Integer> peerUnread = new HashMap<>();
    /** Sube con cada cambio; las pantallas lo usan para invalidar cachés. */
    public int version = 0;

    public void add(ChatEntry e) {
        all.add(e);
        int cap = HoennConfig.get().historyPerChannel;
        int count = 0;
        for (ChatEntry x : all) if (x.channel.equals(e.channel)) count++;
        if (count > cap) {
            Iterator<ChatEntry> it = all.iterator();
            while (it.hasNext()) {
                if (it.next().channel.equals(e.channel)) {
                    it.remove();
                    break;
                }
            }
        }
        if (!e.mine) {
            unread.merge(e.channel, 1, Integer::sum);
            if (e.peer != null) peerUnread.merge(e.peer.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        version++;
    }

    /** Últimos mensajes de una pestaña. "all" excluye canales silenciados. */
    public List<ChatEntry> view(String tab, String filterLower, int limit) {
        HoennConfig cfg = HoennConfig.get();
        List<ChatEntry> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            ChatEntry e = all.get(i);
            if (tab.equals("all")) {
                ChannelDef c = cfg.channel(e.channel);
                if (c != null && c.muted) continue;
            } else if (!e.channel.equals(tab)) {
                continue;
            }
            if (filterLower != null && !filterLower.isEmpty()
                    && !e.text.toLowerCase(Locale.ROOT).contains(filterLower)) continue;
            out.add(e);
        }
        Collections.reverse(out);
        return out;
    }

    /** Mensajes de una conversación de susurros. */
    public List<ChatEntry> viewPeer(String peer, int limit) {
        List<ChatEntry> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            ChatEntry e = all.get(i);
            if (e.peer != null && e.peer.equalsIgnoreCase(peer)) out.add(e);
        }
        Collections.reverse(out);
        return out;
    }

    /** Borra el historial de una conversación. */
    public void removePeer(String peer) {
        all.removeIf(e -> e.peer != null && e.peer.equalsIgnoreCase(peer));
        peerUnread.remove(peer.toLowerCase(Locale.ROOT));
        version++;
        save();
    }

    public int unreadPeer(String peer) {
        return peerUnread.getOrDefault(peer.toLowerCase(Locale.ROOT), 0);
    }

    public void markPeerRead(String peer) {
        peerUnread.remove(peer.toLowerCase(Locale.ROOT));
    }

    public int unread(String tab) {
        if (tab.equals("all")) {
            int sum = 0;
            for (int v : unread.values()) sum += v;
            return sum;
        }
        return unread.getOrDefault(tab, 0);
    }

    public void markRead(String tab) {
        if (tab.equals("all")) unread.clear();
        else unread.remove(tab);
    }

    public void clear() {
        all.clear();
        unread.clear();
        peerUnread.clear();
        version++;
        save();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("hoennascension").resolve("history.json");
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(all));
        } catch (Exception ignored) {
        }
    }

    public void load() {
        try {
            if (!Files.exists(file())) return;
            List<ChatEntry> loaded = GSON.fromJson(Files.readString(file()),
                    new TypeToken<List<ChatEntry>>() {}.getType());
            all.clear();
            if (loaded != null) all.addAll(loaded);
            version++;
        } catch (Exception ignored) {
        }
    }
}