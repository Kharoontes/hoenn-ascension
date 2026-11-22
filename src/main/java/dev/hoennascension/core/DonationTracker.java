package dev.hoennascension.core;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.config.ServerProfile;
import net.minecraft.text.Text;

import java.text.NumberFormat;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Sigue una donación: tras enviar el comando espera la respuesta del servidor y avisa con un popup
 * de "completada", "fallida" o "sin confirmación" (si el servidor no contesta en unos segundos).
 */
public final class DonationTracker {
    private static final String TAG = "donate";
    private static final int RGB_PENDING = 0xFFC247;
    private static final int RGB_OK = 0x35C46A;
    private static final int RGB_FAIL = 0xE0463F;

    private static boolean pending;
    private static long amount;
    private static long deadline;

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

    /** Se llama justo después de enviar el comando de donación. */
    public static void start(long value) {
        ServerProfile sp = HoennConfig.get().server;
        pending = true;
        amount = value;
        deadline = System.currentTimeMillis() + sp.donateConfirmSeconds * 1000L;
        HoennAscensionClient.POPUPS.notice(TAG, tr("hoennascension.donate.sent"), fmt(value),
                RGB_PENDING, sp.donateConfirmSeconds + 2);
    }

    /** Se llama con cada mensaje recibido mientras hay una donación en curso. */
    public static void onMessage(String plain) {
        if (!pending) return;
        ServerProfile sp = HoennConfig.get().server;
        if (matches(sp.donateFailPattern, plain)) {
            pending = false;
            String body = plain.length() > 80 ? plain.substring(0, 77) + "..." : plain;
            HoennAscensionClient.POPUPS.notice(TAG, tr("hoennascension.donate.failed"), body, RGB_FAIL, 7);
        } else if (matches(sp.donateSuccessPattern, plain)) {
            pending = false;
            HoennAscensionClient.POPUPS.notice(TAG, tr("hoennascension.donate.done"), fmt(amount), RGB_OK, 6);
            HoennAscensionClient.playSound("levelup");
        }
    }

    /** Cada tick: si el servidor no contestó a tiempo, se avisa sin afirmar que salió bien. */
    public static void tick() {
        if (pending && System.currentTimeMillis() > deadline) {
            pending = false;
            HoennAscensionClient.POPUPS.notice(TAG, tr("hoennascension.donate.sent"),
                    fmt(amount) + " - " + tr("hoennascension.donate.unconfirmed"), RGB_PENDING, 5);
        }
    }
}