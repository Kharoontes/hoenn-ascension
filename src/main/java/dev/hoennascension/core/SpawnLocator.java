package dev.hoennascension.core;

import dev.hoennascension.HoennAscensionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Cuando un aviso de aparición no trae coordenadas, intenta localizar al Pokémon entre las entidades que el
 * cliente tiene cargadas cerca de ti (solo las cercanas llegan al cliente) y rellena sus coordenadas y distancia.
 */
public final class SpawnLocator {
    private static final class Pending {
        ChatEntry entry;
        String pokemon;
        long deadline;
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private SpawnLocator() {}

    public static void watch(ChatEntry entry, String pokemon) {
        if (pokemon == null || pokemon.isBlank()) return;
        Pending p = new Pending();
        p.entry = entry;
        p.pokemon = pokemon;
        p.deadline = System.currentTimeMillis() + 6000;
        PENDING.add(p);
        while (PENDING.size() > 8) PENDING.remove(0);
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Entity find(MinecraftClient mc, String pokemon) {
        String want = norm(pokemon);
        if (want.length() < 3) return null;
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
            if (!"cobblemon".equals(id.getNamespace())) continue;
            String name = norm(e.getName().getString());
            if (name.length() < 3 || !(name.contains(want) || want.contains(name))) continue;
            double d = e.squaredDistanceTo(mc.player);
            if (d < bestD) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    public static void tick() {
        if (PENDING.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            PENDING.clear();
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            Entity found = find(mc, p.pokemon);
            if (found != null) {
                ChatEntry e = p.entry;
                e.hasPos = true;
                e.x = (int) Math.floor(found.getX());
                e.y = (int) Math.floor(found.getY());
                e.z = (int) Math.floor(found.getZ());
                e.dist = (int) Math.round(Math.hypot(mc.player.getX() - found.getX(), mc.player.getZ() - found.getZ()));
                changed = true;
                it.remove();
            } else if (now > p.deadline) {
                it.remove();
            }
        }
        if (changed) HoennAscensionClient.STORE.touch();
    }
}