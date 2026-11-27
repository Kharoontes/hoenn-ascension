package dev.hoennascension.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Comandos y formatos de mensajes del servidor. Todo es ajustable en config/hoennascension.json.
 * Plantillas con marcadores: {amount}, {player}, {message}.
 */
public final class ServerProfile {
    /** Etiqueta que identifica a los miembros del clan en la lista de jugadores (tab) o en su equipo. */
    public String clanTag = "hoennascension";

    // ---- Donar al clan -------------------------------------------------------------------
    public String donateCommand = "/clan deposit {amount}";
    public List<Integer> donateAmounts = new ArrayList<>(List.of(10000, 25000, 40000, 50000));
    public String donateSuccessPattern = "(deposit|deposit[o\u00f3]|depositaste|donaste|donaci[o\u00f3]n|donated)";
    public String donateFailPattern = "(no tienes|insuficiente|not enough|insufficient|cannot|can't|error|invalid|inv[a\u00e1]lido|no puedes|unknown command|comando desconocido|permiso|permission)";
    public int donateConfirmSeconds = 4;

    // ---- Pagar a un jugador --------------------------------------------------------------
    public String payCommand = "/pay {player} pokedolares {amount}";
    public List<Integer> payAmounts = new ArrayList<>(List.of(100, 500, 1000, 5000));
    public String paySuccessPattern = "(pagaste|has pagado|pago realizado|pago exitoso|you paid|you have paid|payment (?:sent|successful|complete)|has enviado|enviaste)";
    public String payFailPattern = "(no tienes|insuficiente|not enough|insufficient|cannot|can't|error|invalid|inv[a\u00e1]lido|no puedes|unknown|desconocid|permiso|permission|not found|no existe|no est[a\u00e1] conectado|offline)";
    public int payConfirmSeconds = 4;

    // ---- Teleport ------------------------------------------------------------------------
    /** Comando al pulsar a un jugador de la cuadrícula del clan. */
    public String tpaCommand = "/tpa {player}";

    // ---- Wondertrade ---------------------------------------------------------------------
    public String wondertradeCommand = "/wondertrade";
    /** Minutos entre intercambios. Ajusta al tiempo real del servidor. */
    public int wondertradeCooldownMinutes = 60;
    /** Un mensaje solo cuenta para el contador si coincide con esta regex. */
    public String wondertradeMatch = "wonder ?trade";
    /** Mensaje de intercambio realizado: inicia la cuenta atrás. */
    public String wondertradeDonePattern = "(complet|success|exitos|realizad|traded|intercambi)";
    /** Mensajes del servidor que indican cuánto falta (grupos opcionales h, m, s). Si coincide, sincroniza el contador. */
    public List<String> wondertradeWaitPatterns = new ArrayList<>(List.of(
            "(?:wait|espera|esperar|cooldown|available in|disponible en|try again in|intenta de nuevo en|again in)\\D{0,25}?(?=\\d)(?:(?<h>\\d+)\\s*(?:hours?|horas?|hrs|hr|h))?\\s*(?:(?<m>\\d+)\\s*(?:minutes?|minutos?|mins|min|m))?\\s*(?:(?<s>\\d+)\\s*(?:seconds?|segundos?|secs|sec|s))?",
            "(?:wait|espera|esperar|cooldown|available in|disponible en|try again in|intenta de nuevo en|again in)\\D{0,25}?(?<m>\\d{1,3}):(?<s>\\d{2})"));

    // ---- Mercado (GTS) -------------------------------------------------------------------
    /** Tus ventas: "Bob bought your Pikachu for 1000". Grupos: item, player (el comprador), price. */
    public List<String> gtsSellPatterns = new ArrayList<>(List.of(
            "(?<player>[A-Za-z0-9_.]{2,16}) (?:bought|purchased) your (?<item>.+?)(?: for (?<price>[\\d,.]+))?(?:[.!]|$)",
            "(?<player>[A-Za-z0-9_.]{2,16}) (?:te )?(?:compr\u00f3|ha comprado|compro) (?:tu )?(?<item>.+?)(?: por (?<price>[\\d.,]+))?(?:[.!]|$)",
            "vendiste (?<item>.+?) a (?<player>[A-Za-z0-9_.]{2,16})(?: por (?<price>[\\d.,]+))?",
            "you sold (?<item>.+?) to (?<player>[A-Za-z0-9_.]{2,16})(?: for (?<price>[\\d,.]+))?"));
    /** Tus compras: "You bought Pikachu from Bob for 1000". Grupos: item, player (el vendedor), price. */
    public List<String> gtsBuyPatterns = new ArrayList<>(List.of(
            "you (?:bought|purchased) (?<item>.+?) from (?<player>[A-Za-z0-9_.]{2,16})(?: for (?<price>[\\d,.]+))?",
            "(?:compraste|has comprado) (?<item>.+?) a (?<player>[A-Za-z0-9_.]{2,16})(?: por (?<price>[\\d.,]+))?"));

    // ---- Susurros ------------------------------------------------------------------------
    /** Cómo saber con quién es un susurro (regex con UN grupo de captura = nombre del jugador). */
    public List<String> whisperFromPatterns = new ArrayList<>(List.of(
            "^(?:\\[pm\\]\\s*)?(?:from\\s+)?([A-Za-z0-9_.]{2,16})\\s*(?:whispers to you|te susurra)",
            "\\[pm\\]\\s*(?:from\\s+)?([A-Za-z0-9_.]{2,16})\\s*(?:->|:)",
            "^([A-Za-z0-9_.]{2,16})\\s*->\\s*(?:me|you|yo)\\b"));
    public List<String> whisperToPatterns = new ArrayList<>(List.of(
            "you whisper to ([A-Za-z0-9_.]{2,16})",
            "(?:le )?susurras? a ([A-Za-z0-9_.]{2,16})",
            "^(?:me|you|yo)\\s*->\\s*([A-Za-z0-9_.]{2,16})"));
    public String privateCommand = "/msg {player} {message}";

    public void sanitize() {
        if (clanTag == null) clanTag = "";
        if (donateCommand == null) donateCommand = "";
        if (privateCommand == null) privateCommand = "";
        if (payCommand == null) payCommand = "";
        if (tpaCommand == null) tpaCommand = "";
        if (wondertradeCommand == null) wondertradeCommand = "";
        if (wondertradeMatch == null) wondertradeMatch = "";
        if (wondertradeDonePattern == null) wondertradeDonePattern = "";
        if (donateSuccessPattern == null) donateSuccessPattern = "";
        if (donateFailPattern == null) donateFailPattern = "";
        if (paySuccessPattern == null) paySuccessPattern = "";
        if (payFailPattern == null) payFailPattern = "";
        if (wondertradeWaitPatterns == null) wondertradeWaitPatterns = new ArrayList<>();
        if (gtsSellPatterns == null) gtsSellPatterns = new ArrayList<>();
        if (gtsBuyPatterns == null) gtsBuyPatterns = new ArrayList<>();
        if (whisperFromPatterns == null) whisperFromPatterns = new ArrayList<>();
        if (whisperToPatterns == null) whisperToPatterns = new ArrayList<>();
        donateConfirmSeconds = Math.max(2, Math.min(15, donateConfirmSeconds));
        payConfirmSeconds = Math.max(2, Math.min(15, payConfirmSeconds));
        wondertradeCooldownMinutes = Math.max(1, Math.min(1440, wondertradeCooldownMinutes));
        if (donateAmounts == null || donateAmounts.isEmpty()) {
            donateAmounts = new ArrayList<>(List.of(10000, 25000, 40000, 50000));
        }
        if (payAmounts == null || payAmounts.isEmpty()) {
            payAmounts = new ArrayList<>(List.of(100, 500, 1000, 5000));
        }
    }

    public boolean canDonate() {
        return donateCommand != null && !donateCommand.isBlank();
    }
}