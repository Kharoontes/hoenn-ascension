package dev.hoennascension.core;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;

/** Clasifica cada mensaje entrante en un canal y dispara popups/sonidos. */
public final class MessageRouter {
    private MessageRouter() {}

    /** @return true si el mensaje debe seguir apareciendo en el chat vanilla. */
    public static boolean accept(Text text) {
        String plain = text.getString().trim();
        if (plain.isEmpty()) return true;
        DonationTracker.onMessage(plain);

        HoennConfig cfg = HoennConfig.get();
        ChannelDef ch = route(cfg, plain);
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
            HoennAscensionClient.STORE.add(new ChatEntry(System.currentTimeMillis(), ch.id, plain, mention, peer, false));
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

    private static ChannelDef route(HoennConfig cfg, String plain) {
        // Un susurro siempre va al canal Privado, aunque el patrón de ese canal en tu config no lo cubra
        if (Whispers.parse(plain) != null) {
            ChannelDef priv = cfg.channel("private");
            if (priv != null) return priv;
        }
        for (ChannelDef c : cfg.channels) {
            if (c.matches(plain)) return c;
        }
        return cfg.fallback();
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