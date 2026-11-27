package dev.hoennascension.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HoennConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HoennConfig instance;

    /** Versión del formato de la config; permite migrar archivos antiguos. */
    public int version = 3;
    /** Interruptor general: desactivado, el mod no captura mensajes ni dibuja nada y el chat queda 100% vanilla. */
    public boolean enabled = true;
    /** Versión del historial guardado (para re-clasificarlo una vez con las reglas nuevas). */
    public int historyVersion = 0;

    // Apariencia
    /** kyogre (azul) | groudon (rojo) | primal (violeta) | abismo (negro) | verde */
    public String theme = "primal";
    public boolean timestamps = true;
    /** Opacidad del fondo de los menús (0.15 = muy transparente, 1 = sólido). */
    public float menuOpacity = 0.55f;
    public boolean showLogo = true;
    public boolean showBanner = true;
    /** Bandera plegada (queda asomando una tira para volver a desplegarla). */
    public boolean bannerHidden = false;
    /** Sidebar compacto (solo iconos). Se compacta solo si el panel es muy estrecho. */
    public boolean sidebarCollapsed = false;

    // Límites de tamaño de la ventana (en píxeles de interfaz)
    public int windowMinWidth = 300;
    public int windowMinHeight = 170;
    public int windowMaxWidth = 760;
    public int windowMaxHeight = 520;

    // Chat normal de Minecraft
    /** Oculta del chat normal cualquier mensaje que capture el mod (el chat vive solo en el panel y el HUD). */
    public boolean hideAllFromVanilla = false;
    /** Tras ejecutar un comando desde el mod (donar, pagar...), oculta la respuesta del servidor del chat normal. */
    public boolean hideCommandFeedback = false;
    public int commandFeedbackSeconds = 2;
    /** Oculta del chat normal el aviso de aparición de un Mega (se sigue viendo en la pestaña Spawns). */
    public boolean hideMegaNotice = true;

    // Posición y tamaño de la ventana como PROPORCIÓN de la pantalla (0..1; -1 = automático)
    public float winXr = -1f, winYr = -1f, winWr = -1f, winHr = -1f;
    public float pillXr = -1f, pillYr = -1f;

    /** Comandos y formatos del servidor. */
    public ServerProfile server = new ServerProfile();

    // Ventana minimizada (pastilla flotante)
    public boolean minimized = false;
    public boolean pillAlwaysVisible = true;
    /** top_left | top_right | bottom_left | bottom_right */
    public String minimizedAnchor = "top_left";

    // HUD anclado
    public boolean hudPinned = true;
    public String hudAnchor = "bottom_left";
    public int hudLines = 6;
    public int hudWidth = 260;
    public float hudOpacity = 0.65f;
    public int hudFadeSeconds = 12;
    public boolean hudAlwaysVisible = false;
    /** id de canal o "all" (todos los canales del mod) */
    public String hudChannel = "all";

    // Notificaciones
    public boolean popups = true;
    public int popupSeconds = 6;
    /** Cuánto dura una notificación con botones (p. ej. /tpa: aceptar con Y, rechazar con N). */
    public int actionPopupSeconds = 15;
    public boolean mentionHighlight = true;
    public List<String> highlightWords = new ArrayList<>();

    public int historyPerChannel = 300;

    // Wondertrade
    /** Momento (epoch ms) en que vuelve a estar disponible /wondertrade; 0 = desconocido. */
    public long wondertradeNextAt = 0L;
    public boolean wondertradeNotify = true;

    // Clan
    /** Miembros del clan vistos con la etiqueta (para mostrarlos como "sin conexión" en Raids). */
    public List<String> clanSeen = new ArrayList<>();

    // Puente a Discord (solo clan -> Discord, mediante un webhook)
    public boolean discordRelay = false;
    public String discordWebhookUrl = "";

    // Conversaciones de susurros
    public List<String> whisperContacts = new ArrayList<>();

    public List<ChannelDef> channels = new ArrayList<>();

    public static HoennConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static HoennConfig reload() {
        instance = load();
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("hoennascension.json");
    }

    private static HoennConfig load() {
        HoennConfig cfg = null;
        Path p = path();
        if (Files.exists(p)) {
            try {
                cfg = GSON.fromJson(Files.readString(p), HoennConfig.class);
            } catch (Exception ignored) {
                cfg = null;
            }
        }
        boolean fresh = cfg == null;
        if (fresh) cfg = new HoennConfig();
        cfg.sanitize();
        if (fresh) cfg.save();
        return cfg;
    }

    private void sanitize() {
        if (channels == null || channels.isEmpty()) {
            channels = defaultChannels();
            version = 3;
        } else if (version < 3) {
            migrateToV3();
        }
        if (highlightWords == null) highlightWords = new ArrayList<>();
        if (whisperContacts == null) whisperContacts = new ArrayList<>();
        if (clanSeen == null) clanSeen = new ArrayList<>();
        if (discordWebhookUrl == null) discordWebhookUrl = "";
        if (theme == null || theme.equalsIgnoreCase("alba")) theme = "primal";
        if (hudAnchor == null) hudAnchor = "bottom_left";
        if (hudChannel == null) hudChannel = "all";
        if (minimizedAnchor == null) minimizedAnchor = "top_left";
        if (server == null) server = new ServerProfile();
        server.sanitize();
        commandFeedbackSeconds = Math.max(1, Math.min(10, commandFeedbackSeconds));
        windowMinWidth = Math.max(240, Math.min(500, windowMinWidth));
        windowMinHeight = Math.max(140, Math.min(400, windowMinHeight));
        windowMaxWidth = Math.max(windowMinWidth + 40, Math.min(1600, windowMaxWidth));
        windowMaxHeight = Math.max(windowMinHeight + 40, Math.min(1200, windowMaxHeight));
        hudLines = Math.max(1, Math.min(20, hudLines));
        hudWidth = Math.max(120, Math.min(600, hudWidth));
        hudOpacity = Math.max(0.05f, Math.min(1f, hudOpacity));
        menuOpacity = Math.max(0.15f, Math.min(1f, menuOpacity));
        historyPerChannel = Math.max(20, Math.min(2000, historyPerChannel));
        boolean hasFallback = false;
        for (ChannelDef c : channels) if (c.isFallback()) hasFallback = true;
        if (!hasFallback) channels.add(0, general());
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }

    /** Restaura posición/tamaño de la ventana, la pastilla y la bandera. */
    public void resetLayout() {
        winXr = winYr = winWr = winHr = -1f;
        pillXr = pillYr = -1f;
        bannerHidden = false;
    }

    /** Añade una conversación de susurros. @return true si es nueva (se guarda al momento). */
    public boolean addContact(String name) {
        for (int i = 0; i < whisperContacts.size(); i++) {
            if (whisperContacts.get(i).equalsIgnoreCase(name)) {
                if (i != 0) whisperContacts.add(0, whisperContacts.remove(i));
                return false;
            }
        }
        whisperContacts.add(0, name);
        while (whisperContacts.size() > 40) whisperContacts.remove(whisperContacts.size() - 1);
        save();
        return true;
    }

    public void removeContact(String name) {
        whisperContacts.removeIf(n -> n.equalsIgnoreCase(name));
        save();
    }

    public ChannelDef channel(String id) {
        for (ChannelDef c : channels) if (c.id.equals(id)) return c;
        return null;
    }

    public ChannelDef fallback() {
        for (ChannelDef c : channels) if (c.isFallback()) return c;
        return channels.get(0);
    }

    // ---- Canales por defecto -------------------------------------------------------------

    private static ChannelDef general() {
        ChannelDef g = new ChannelDef("general", "General", "", "#BFD4FF");
        g.hidden = true;
        return g;
    }

    /**
     * Configs antiguas: los canales se rehacen con la estructura actual (Clan, Spawns, Teleport, Mercado,
     * Wondertrade, Raids). Los canales propios que hayas añadido se conservan.
     */
    private void migrateToV3() {
        Set<String> known = Set.of("general", "private", "teleport", "clan", "spawns", "market",
                "wondertrade", "raids", "legendary", "mega", "trade");
        List<ChannelDef> custom = new ArrayList<>();
        for (ChannelDef c : channels) if (!known.contains(c.id)) custom.add(c);
        channels = defaultChannels();
        channels.addAll(custom);
        if ("alba".equalsIgnoreCase(theme)) theme = "primal";
        version = 3;
        save();
    }

    /**
     * Los patrones son un punto de partida: cada servidor formatea distinto sus mensajes.
     * Ajústalos en config/hoennascension.json a los mensajes reales y usa /hoennascension reload.
     */
    private static List<ChannelDef> defaultChannels() {
        List<ChannelDef> l = new ArrayList<>();
        l.add(general());

        // Susurros (interno): alimenta las conversaciones de Raids. Avisa solo de los mensajes de otros.
        ChannelDef priv = new ChannelDef("private", "Privado",
                "(\\[pm\\]|whispers to you|te susurra|->\\s*(me|you|yo)\\b)", "#A481DE").popup("orb");
        priv.hidden = true;
        priv.icon = "raids";
        l.add(priv);

        // Clan: solo lo que llega por el chat del clan (todos los rangos), como "[Rango] Nombre: mensaje".
        // Al escribir en esta pestaña se envía con /clan chat.
        ChannelDef clan = new ChannelDef("clan", "Clan",
                "\\[\\s*(?:clan|cl\\u00e1n)(?:\\s+chat)?\\s*\\]", "#47B5F8");
        clan.sendPrefix = "/clan chat ";
        clan.formatPatterns = new ArrayList<>(List.of(
                "\\[\\s*(?:clan|cl\\u00e1n)(?:\\s+chat)?\\s*\\]\\s*(?:\\[\\s*(?<rank>[^\\]]+?)\\s*\\]\\s*)?(?<name>[A-Za-z0-9_.]{2,16})\\s*[:\\u00bb>\\-]+\\s*(?<msg>.+)",
                "\\[\\s*(?:clan|cl\\u00e1n)(?:\\s+chat)?\\s*\\]\\s*(?<rank>[^\\s\\[\\]:\\u00bb>\\-]+)\\s+(?<name>[A-Za-z0-9_.]{2,16})\\s*[:\\u00bb>\\-]+\\s*(?<msg>.+)"));
        l.add(clan);

        // Spawns: legendarios y megas en un solo chat, con botones para filtrar. Solo avisos del juego y solo si
        // la aparición está cerca del jugador. Muestra las coordenadas y la distancia cuando se conocen.
        ChannelDef spawns = new ChannelDef("spawns", "Spawns",
                "(legendary|legendario|\\bmega\\b)", "#EC7D8B").popup("levelup");
        spawns.gameOnly = true;
        spawns.requirePattern = "(spawn|apareci|appear|surgi|sali\\u00f3|wild|salvaje)";
        spawns.nearbyOnly = true;
        spawns.nearbyBlocks = 256;
        spawns.nearbyShowUnknown = true;
        spawns.hideRejected = true;
        spawns.coordPatterns = new ArrayList<>(List.of(
                "\\bx\\s*[:=]?\\s*(?<x>-?\\d+(?:\\.\\d+)?)[\\s,;|/]+y\\s*[:=]?\\s*(?<y>-?\\d+(?:\\.\\d+)?)[\\s,;|/]+z\\s*[:=]?\\s*(?<z>-?\\d+(?:\\.\\d+)?)",
                "(?<x>-?\\d{1,6})\\s*,\\s*(?<y>-?\\d{1,4})\\s*,\\s*(?<z>-?\\d{1,6})"));
        spawns.pokemonPatterns = new ArrayList<>(List.of(
                "(?:legendary|legendario|mega)\\s+(?<pokemon>[\\p{L}'\\-]{3,20})",
                "(?:wild|salvaje)\\s+(?<pokemon>[\\p{L}'\\-]{3,20})"));
        spawns.subFilters = new ArrayList<>(List.of("legendary", "mega"));
        spawns.subPatterns = new ArrayList<>(List.of("legendar", "\\bmega\\b"));
        l.add(spawns);

        // Teleport: cuadrícula con los jugadores del clan conectados + solicitudes /tpa con botones Aceptar / Rechazar.
        ChannelDef tp = new ChannelDef("teleport", "Teleport",
                "(has requested to teleport|wants to teleport to you|solicitud de teletransporte|quiere teletransportarse|petici\\u00f3n de teletransporte|tpaccept|tpdeny)",
                "#5CFFE0").popup("orb").actions("/tpaccept", "/tpdeny");
        tp.view = "teleport";
        tp.gameOnly = true;
        tp.excludePattern = "(sent|enviad|has enviado|you sent|request sent)";
        l.add(tp);

        // Mercado: tus compras y ventas del GTS, con quién; filtrables por ventas / compras.
        ChannelDef market = new ChannelDef("market", "Mercado", "\\bgts\\b", "#FFC247");
        market.gameOnly = true;
        market.ownOnly = true;
        market.selfPattern = "\\b(you|your|t\\u00fa|tu|tus|has|compraste|vendiste)\\b";
        market.parser = "gts";
        market.subFilters = new ArrayList<>(List.of("sell", "buy"));
        market.subPatterns = new ArrayList<>(List.of(
                "(vendiste|vendido|sold|bought your|purchased your|te compr|compr\\u00f3 tu|ha comprado tu)",
                "(compraste|has comprado|you bought|you purchased)"));
        l.add(market);

        // Wondertrade: cuenta atrás hasta el próximo intercambio.
        ChannelDef wt = new ChannelDef("wondertrade", "Wondertrade", "wonder ?trade", "#FF8AD8");
        wt.view = "wondertrade";
        wt.gameOnly = true;
        l.add(wt);

        // Raids: lista de amigos (miembros del clan con la etiqueta) y conversaciones. No captura mensajes.
        ChannelDef raids = new ChannelDef("raids", "Raids", "", "#A481DE");
        raids.view = "raids";
        l.add(raids);
        return l;
    }
}