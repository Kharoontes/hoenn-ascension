package dev.hoennascension.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Comandos del servidor que el mod ejecuta en nombre del jugador.
 * Plantillas con marcadores: {amount}, {player}, {message}.
 * Ajusta los comandos a los que use realmente el servidor (config/hoennascension.json).
 */
public final class ServerProfile {
    /** Donación al clan (función Donar). El valor elegido sustituye a {amount}. */
    public String donateCommand = "/clan deposit {amount}";
    public List<Integer> donateAmounts = new ArrayList<>(List.of(10000, 25000, 40000, 50000));

    /**
     * Cómo reconocer la respuesta del servidor tras donar (regex, sin distinguir mayúsculas).
     * Se evalúa primero el patrón de fallo. Ajusta ambos a los mensajes reales del servidor.
     */
    public String donateSuccessPattern = "(deposit|deposit[o\u00f3]|depositaste|donaste|donaci[o\u00f3]n|donated)";
    public String donateFailPattern = "(no tienes|insuficiente|not enough|insufficient|cannot|can't|error|invalid|inv[a\u00e1]lido|no puedes|unknown command|comando desconocido|permiso|permission)";
    /** Segundos que se espera la respuesta del servidor antes de avisar "sin confirmación". */
    public int donateConfirmSeconds = 4;

    /**
     * Cómo saber con quién es un susurro (regex con UN grupo de captura = nombre del jugador).
     * Entrantes: "Ash whispers to you: hola". Salientes (eco del servidor): "You whisper to Ash: hola".
     * Ajústalos al formato real de los mensajes privados de tu servidor.
     */
    public List<String> whisperFromPatterns = new ArrayList<>(List.of(
            "^(?:\\[pm\\]\\s*)?(?:from\\s+)?([A-Za-z0-9_.]{2,16})\\s*(?:whispers to you|te susurra)",
            "\\[pm\\]\\s*(?:from\\s+)?([A-Za-z0-9_.]{2,16})\\s*(?:->|:)",
            "^([A-Za-z0-9_.]{2,16})\\s*->\\s*(?:me|you|yo)\\b"));
    public List<String> whisperToPatterns = new ArrayList<>(List.of(
            "you whisper to ([A-Za-z0-9_.]{2,16})",
            "(?:le )?susurras? a ([A-Za-z0-9_.]{2,16})",
            "^(?:me|you|yo)\\s*->\\s*([A-Za-z0-9_.]{2,16})"));

    /** Mensaje privado: escribe "@Jugador mensaje" o usa la función Susurros. */
    public String privateCommand = "/msg {player} {message}";

    public void sanitize() {
        if (whisperFromPatterns == null) whisperFromPatterns = new ArrayList<>();
        if (whisperToPatterns == null) whisperToPatterns = new ArrayList<>();
        if (donateSuccessPattern == null) donateSuccessPattern = "";
        if (donateFailPattern == null) donateFailPattern = "";
        donateConfirmSeconds = Math.max(2, Math.min(15, donateConfirmSeconds));
        if (donateCommand == null) donateCommand = "";
        if (privateCommand == null) privateCommand = "";
        if (donateAmounts == null || donateAmounts.isEmpty()) {
            donateAmounts = new ArrayList<>(List.of(10000, 25000, 40000, 50000));
        }
    }

    public boolean canDonate() {
        return donateCommand != null && !donateCommand.isBlank();
    }
}