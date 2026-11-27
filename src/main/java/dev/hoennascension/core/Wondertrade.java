package dev.hoennascension.core;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.config.ServerProfile;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuenta atrás hasta el próximo /wondertrade. El mod no recibe el tiempo restante del servidor por sí solo:
 * lo calcula a partir de los mensajes del servidor (intercambio hecho o "espera X") y del tiempo de espera configurado.
 */
public final class Wondertrade {
    /** Se observó una cuenta atrás en esta sesión (para avisar solo cuando termina mientras juegas). */
    private static boolean armed;

    private Wondertrade() {}

    public static boolean known() {
        return HoennConfig.get().wondertradeNextAt > 0;
    }

    /** Milisegundos que faltan (0 = ya disponible); -1 si no se sabe. */
    public static long remainingMs() {
        long next = HoennConfig.get().wondertradeNextAt;
        if (next <= 0) return -1;
        return Math.max(0, next - System.currentTimeMillis());
    }

    public static long cooldownMs() {
        return HoennConfig.get().server.wondertradeCooldownMinutes * 60_000L;
    }

    /** Inicia la cuenta atrás completa (intercambio hecho). */
    public static void startCooldown() {
        setRemaining(cooldownMs());
    }

    public static void setRemaining(long ms) {
        HoennConfig cfg = HoennConfig.get();
        cfg.wondertradeNextAt = System.currentTimeMillis() + Math.max(0, ms);
        armed = true;
        cfg.save();
    }

    private static int num(Matcher m, String group) {
        String v = ChannelDef.group(m, group);
        if (v == null) return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Segundos indicados por un mensaje de espera (grupos h, m, s); 0 si no hay ninguno. */
    static long parseWaitSeconds(java.util.List<String> patterns, String plain) {
        if (patterns == null) return 0;
        for (String re : patterns) {
            Pattern p = ChannelDef.compile(re);
            if (p == null) continue;
            Matcher m = p.matcher(plain);
            if (!m.find()) continue;
            long total = num(m, "h") * 3600L + num(m, "m") * 60L + num(m, "s");
            if (total > 0) return total;
        }
        return 0;
    }

    /** Se llama con cada mensaje: si es del wondertrade, sincroniza o reinicia la cuenta atrás. */
    public static void onMessage(String plain) {
        ServerProfile sp = HoennConfig.get().server;
        if (!ChannelDef.find(sp.wondertradeMatch, plain)) return;
        long wait = parseWaitSeconds(sp.wondertradeWaitPatterns, plain);
        if (wait > 0) {
            setRemaining(wait * 1000L);
        } else if (ChannelDef.find(sp.wondertradeDonePattern, plain)) {
            startCooldown();
        }
    }

    /** Cada tick: avisa cuando termina la cuenta atrás (solo si la viste correr en esta sesión). */
    public static void tick() {
        HoennConfig cfg = HoennConfig.get();
        long rem = remainingMs();
        if (rem > 0) {
            armed = true;
        } else if (rem == 0 && armed) {
            armed = false;
            if (cfg.wondertradeNotify && cfg.popups) {
                HoennAscensionClient.POPUPS.notice("wondertrade", "wondertrade",
                        Text.translatable("hoennascension.wt.ready").getString(),
                        Text.translatable("hoennascension.wt.ready.body").getString(), 0xFF8AD8, 8);
                HoennAscensionClient.playSound("levelup");
            }
        }
    }
}