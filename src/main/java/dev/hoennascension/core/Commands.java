package dev.hoennascension.core;

import net.minecraft.client.MinecraftClient;

/** Utilidades para ejecutar los comandos del servidor y rellenar plantillas. */
public final class Commands {
    private static volatile long lastRun = 0L;

    private Commands() {}

    /** ¿El mod ejecutó un comando en los últimos N segundos? (sirve para ocultar la respuesta del servidor) */
    public static boolean recentlyRan(int seconds) {
        return System.currentTimeMillis() - lastRun < seconds * 1000L;
    }

    /** Ejecuta un comando (con o sin "/" inicial) como si lo escribiera el jugador. */
    public static void run(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || command == null || command.isBlank()) return;
        String c = command.trim();
        if (c.startsWith("/")) c = c.substring(1);
        if (c.isEmpty()) return;
        mc.player.networkHandler.sendChatCommand(c);
        lastRun = System.currentTimeMillis();
    }

    /** Sustituye marcadores: fill("/msg {player} {message}", "{player}", "Ash", "{message}", "hola"). */
    public static String fill(String template, String... keyValues) {
        String out = template;
        for (int i = 0; i + 1 < keyValues.length; i += 2) out = out.replace(keyValues[i], keyValues[i + 1]);
        return out;
    }
}