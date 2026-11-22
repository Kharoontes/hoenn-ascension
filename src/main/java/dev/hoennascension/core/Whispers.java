package dev.hoennascension.core;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.config.ServerProfile;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Reconoce con quién es cada mensaje privado y lleva el eco de los que envías desde el mod. */
public final class Whispers {
    /** peer = el otro jugador; outgoing = lo enviaste tú. */
    public record Parsed(String peer, boolean outgoing) {}

    private static String lastMsg = "";
    private static long lastTime;

    private Whispers() {}

    private static String group1(String regex, String text) {
        if (regex == null || regex.isBlank()) return null;
        try {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            if (m.find() && m.groupCount() >= 1 && m.group(1) != null) return m.group(1);
        } catch (PatternSyntaxException ignored) {
        }
        return null;
    }

    /** @return null si el mensaje no parece un susurro. */
    public static Parsed parse(String plain) {
        ServerProfile sp = HoennConfig.get().server;
        for (String re : sp.whisperToPatterns) {
            String g = group1(re, plain);
            if (g != null) return new Parsed(g, true);
        }
        for (String re : sp.whisperFromPatterns) {
            String g = group1(re, plain);
            if (g != null) return new Parsed(g, false);
        }
        return null;
    }

    /** Se llama al enviar un susurro desde el mod: crea la conversación y deja un eco local. */
    public static void sent(String peer, String msg) {
        long now = System.currentTimeMillis();
        lastMsg = msg;
        lastTime = now;
        HoennConfig.get().addContact(peer);
        String me = Text.translatable("hoennascension.whisper.me").getString();
        HoennAscensionClient.STORE.add(new ChatEntry(now, "private", me + " > " + peer + ": " + msg, false, peer, true));
    }

    /** ¿Este mensaje es el eco que el servidor devuelve de lo que acabas de enviar? (evita duplicados) */
    public static boolean isEchoOfSent(String plain) {
        return !lastMsg.isEmpty() && System.currentTimeMillis() - lastTime < 5000
                && plain.toLowerCase(Locale.ROOT).contains(lastMsg.toLowerCase(Locale.ROOT));
    }
}