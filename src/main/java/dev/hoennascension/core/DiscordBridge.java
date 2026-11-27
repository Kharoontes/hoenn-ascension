package dev.hoennascension.core;

import dev.hoennascension.config.HoennConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Envía el chat del clan a un canal de Discord mediante un webhook (un solo sentido: clan -> Discord).
 * Desactivado por defecto. Solo acepta direcciones de webhooks oficiales de Discord.
 */
public final class DiscordBridge {
    private static final Pattern WEBHOOK = Pattern.compile("https://(?:discord|discordapp)\\.com/api/webhooks/\\d+/[\\w-]+");

    private static final ThreadPoolExecutor EXEC = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(20), r -> {
        Thread t = new Thread(r, "hoennascension-discord");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.DiscardOldestPolicy());

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private DiscordBridge() {}

    public static boolean urlOk(String url) {
        return url != null && url.length() < 300 && WEBHOOK.matcher(url.trim()).matches();
    }

    private static String clean(String s, int max) {
        if (s == null) return "";
        String t = s.replace("@", "@\u200b").replaceAll("[\\p{Cntrl}]", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Reenvía un mensaje del chat del clan. rank y name pueden ser null si no se pudieron leer. */
    public static void relay(String rank, String name, String message) {
        HoennConfig cfg = HoennConfig.get();
        if (!cfg.enabled || !cfg.discordRelay || !urlOk(cfg.discordWebhookUrl)) return;
        String url = cfg.discordWebhookUrl.trim();
        String user = clean(name == null || name.isBlank() ? "Clan" : name, 32);
        String body = (rank != null && !rank.isBlank() ? "[" + clean(rank, 20) + "] " : "") + clean(message, 1800);
        if (body.isBlank()) return;
        String payload = "{\"username\":" + json(user.isBlank() ? "Clan" : user)
                + ",\"content\":" + json(body)
                + ",\"allowed_mentions\":{\"parse\":[]}}";
        EXEC.execute(() -> post(url, payload));
    }

    private static void post(String url, String payload) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HTTP.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
            Thread.sleep(600); // respeta el límite de Discord (5 mensajes cada 2 s por webhook)
        } catch (Exception ignored) {
        }
    }
}