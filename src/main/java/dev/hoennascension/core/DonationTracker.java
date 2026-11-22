package dev.hoennascension.core;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.config.ServerProfile;
import net.minecraft.text.Text;

import java.text.NumberFormat;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Sigue las operaciones de dinero (donar al clan y pagar a un jugador): tras enviar el comando espera la
 * respuesta del servidor y avisa con un popup de "completado", "fallido" o "sin confirmación".
 */
public final class DonationTracker {
    private static final String ICON = "donate";
    private static final int RGB_PENDING = 0xFFC247;
    private static final int RGB_OK = 0x35C46A;
    private static final int RGB_FAIL = 0xE0463F;

    /** Una operación en curso a la espera de la respuesta del servidor. */
    private static final class Tx {
        final String tag;
        final String sentKey;
        final String doneKey;
        final String failKey;
        boolean pending;
        String detail = "";
        long deadline;

        Tx(String tag, String sentKey, String doneKey, String failKey) {
            this.tag = tag;
            this.sentKey = sentKey;
            this.doneKey = doneKey;
            this.failKey = failKey;
        }
    }

    private static final Tx DONATE = new Tx("donate",
            "hoennascension.donate.sent", "hoennascension.donate.done", "hoennascension.donate.failed");
    private static final Tx PAY = new Tx("pay",
            "hoennascension.pay.sent", "hoennascension.pay.done", "hoennascension.pay.failed");

    private DonationTracker() {}

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    private static String fmt(long v) {
        return NumberFormat.getIntegerInstance().format(v);
    }

    private static boolean matches(String regex, String text) {
        if (regex == null || regex.isBlank()) return false;
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static void begin(Tx t, String detail, int seconds) {
        t.pending = true;
        t.detail = detail;
        t.deadline = System.currentTimeMillis() + seconds * 1000L;
        HoennAscensionClient.POPUPS.notice(t.tag, ICON, tr(t.sentKey), detail, RGB_PENDING, seconds + 2);
    }

    /** Se llama justo después de enviar el comando de donación. */
    public static void start(long value) {
        begin(DONATE, fmt(value), HoennConfig.get().server.donateConfirmSeconds);
    }

    /** Se llama justo después de enviar el comando de pago a un jugador. */
    public static void startPay(String player, long value) {
        begin(PAY, player + " - " + fmt(value), HoennConfig.get().server.payConfirmSeconds);
    }

    private static void check(Tx t, String failRegex, String okRegex, String plain) {
        if (!t.pending) return;
        if (matches(failRegex, plain)) {
            t.pending = false;
            String body = plain.length() > 80 ? plain.substring(0, 77) + "..." : plain;
            HoennAscensionClient.POPUPS.notice(t.tag, ICON, tr(t.failKey), body, RGB_FAIL, 7);
        } else if (matches(okRegex, plain)) {
            t.pending = false;
            HoennAscensionClient.POPUPS.notice(t.tag, ICON, tr(t.doneKey), t.detail, RGB_OK, 6);
            HoennAscensionClient.playSound("levelup");
        }
    }

    /** Se llama con cada mensaje recibido mientras haya una operación en curso. */
    public static void onMessage(String plain) {
        ServerProfile sp = HoennConfig.get().server;
        check(DONATE, sp.donateFailPattern, sp.donateSuccessPattern, plain);
        check(PAY, sp.payFailPattern, sp.paySuccessPattern, plain);
    }

    private static void expire(Tx t) {
        if (t.pending && System.currentTimeMillis() > t.deadline) {
            t.pending = false;
            HoennAscensionClient.POPUPS.notice(t.tag, ICON, tr(t.sentKey),
                    t.detail + " - " + tr("hoennascension.donate.unconfirmed"), RGB_PENDING, 5);
        }
    }

    /** Cada tick: si el servidor no contestó a tiempo, se avisa sin afirmar que salió bien. */
    public static void tick() {
        expire(DONATE);
        expire(PAY);
    }
}