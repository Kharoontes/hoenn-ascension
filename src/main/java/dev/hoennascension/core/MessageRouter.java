package dev.hoennascension.core;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Clasifica cada mensaje entrante en un canal (aplicando los filtros del canal) y dispara popups/sonidos. */
public final class MessageRouter {
    private MessageRouter() {}

    /** Resultado de clasificar un mensaje. drop = descartado por el filtro del canal (p. ej. aparición lejana). */
    private record Route(ChannelDef channel, boolean drop, String sub, int dist) {}

    /**
     * @param fromPlayer true si es chat escrito por un jugador; false si es un mensaje del juego/servidor.
     * @return true si el mensaje debe seguir apareciendo en el chat vanilla.
     */
    public static boolean accept(Text text, boolean fromPlayer) {
        String plain = text.getString().trim();
        if (plain.isEmpty()) return true;
        DonationTracker.onMessage(plain);

        HoennConfig cfg = HoennConfig.get();
        Route route = route(cfg, plain, fromPlayer);
        ChannelDef ch = route.channel();

        // Descartado por el filtro del canal: no se guarda ni avisa
        if (route.drop()) return !ch.hideRejected;

        boolean mention = cfg.mentionHighlight && mentions(cfg, plain);

        // Susurros: se averigua con quién es la conversación
        String peer = null;
        boolean outgoing = false;
        boolean echo = false;
        if (ch.id.equals("private")) {
            Whispers.Parsed p = Whispers.parse(plain);
            if (p != null) {
                peer = p.peer();
                outgoing = p.outgoing();
                echo = outgoing && Whispers.isEchoOfSent(plain);
                if (!echo) cfg.addContact(peer);
            }
        }

        if (!echo) {
            ChatEntry entry = new ChatEntry(System.currentTimeMillis(), ch.id, plain, mention, peer, false);
            entry.sub = route.sub();
            entry.dist = route.dist();
            HoennAscensionClient.STORE.add(entry);
        }

        // Sin popup ni sonido por lo que envías tú
        if (!ch.muted && (ch.popup || mention) && !outgoing) {
            if (cfg.popups) HoennAscensionClient.POPUPS.push(ch.popup ? ch.name : "@", plain, ch);
            String snd = ch.sound;
            if ((snd == null || snd.equals("none")) && mention) snd = "orb";
            HoennAscensionClient.playSound(snd);
        }

        // Ocultar del chat normal: global, por canal o la respuesta a un comando lanzado desde el mod
        boolean hide = cfg.hideAllFromVanilla || ch.hideFromVanilla
                || (cfg.hideCommandFeedback && Commands.recentlyRan(cfg.commandFeedbackSeconds));
        return !hide;
    }

    private static Route route(HoennConfig cfg, String plain, boolean fromPlayer) {
        // Un susurro siempre va al canal Privado, aunque el patrón de ese canal en tu config no lo cubra
        if (Whispers.parse(plain) != null) {
            ChannelDef priv = cfg.channel("private");
            if (priv != null) return new Route(priv, false, null, -1);
        }
        for (ChannelDef c : cfg.channels) {
            if (!c.matches(plain)) continue;
            if (c.gameOnly && fromPlayer) continue;          // solo mensajes del juego
            if (!c.requireOk(plain)) continue;               // condición extra del canal
            if (c.ownOnly && !isOwn(c, plain)) continue;     // solo lo que es tuyo (p. ej. tus compras del GTS)

            int dist = -1;
            if (c.nearbyOnly) {
                dist = distanceTo(c, plain);
                if (dist >= 0 && dist > c.nearbyBlocks) return new Route(c, true, null, dist);
                if (dist < 0 && !c.nearbyShowUnknown) return new Route(c, true, null, -1);
            }
            return new Route(c, false, c.classify(plain), dist);
        }
        return new Route(cfg.fallback(), false, null, -1);
    }

    /** ¿El mensaje trata de ti? Menciona tu nombre o coincide con la regex selfPattern del canal. */
    private static boolean isOwn(ChannelDef c, String plain) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() != null) {
            String me = mc.getSession().getUsername();
            if (me != null && !me.isEmpty()
                    && plain.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT))) return true;
        }
        return ChannelDef.find(c.selfPattern, plain);
    }

    /** Distancia horizontal en bloques entre el jugador y las coordenadas del mensaje; -1 si no se pueden leer. */
    private static int distanceTo(ChannelDef c, String plain) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || c.coordPatterns == null) return -1;
        for (String regex : c.coordPatterns) {
            Pattern p = ChannelDef.compile(regex);
            if (p == null) continue;
            Matcher m = p.matcher(plain);
            if (!m.find()) continue;
            try {
                double x = Double.parseDouble(m.group("x"));
                double z = Double.parseDouble(m.group("z"));
                return (int) Math.round(Math.hypot(mc.player.getX() - x, mc.player.getZ() - z));
            } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
                // el patrón no tiene los grupos x / z: se prueba el siguiente
            }
        }
        return -1;
    }

    private static boolean mentions(HoennConfig cfg, String plain) {
        String lower = plain.toLowerCase(Locale.ROOT);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() != null) {
            String me = mc.getSession().getUsername();
            if (me != null && !me.isEmpty()) {
                String meLower = me.toLowerCase(Locale.ROOT);
                if (lower.contains(meLower) && !lower.startsWith("<" + meLower + ">")) return true;
            }
        }
        for (String w : cfg.highlightWords) {
            if (w != null && !w.isBlank() && lower.contains(w.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}