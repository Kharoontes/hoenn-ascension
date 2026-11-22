package dev.hoennascension.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class HoennConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HoennConfig instance;

    public int version = 1;

    // Apariencia
    /** kyogre | groudon | primal */
    public String theme = "primal";
    public boolean timestamps = true;
    /** Opacidad del fondo de los menús (0.15 = muy transparente, 1 = sólido). */
    public float menuOpacity = 0.55f;
    public boolean showLogo = true;
    public boolean showBanner = true;
    /** Sidebar compacto (solo iconos). Se compacta solo si el panel es muy estrecho. */
    public boolean sidebarCollapsed = false;

    // Chat normal de Minecraft
    /** Oculta del chat normal TODO lo que captura el mod (el chat vive solo en el panel y el HUD). */
    public boolean hideAllFromVanilla = false;
    /** Tras ejecutar un comando desde el mod (donar, susurrar...), oculta la respuesta del servidor del chat normal. */
    public boolean hideCommandFeedback = false;
    public int commandFeedbackSeconds = 2;

    // Límites de tamaño de la ventana (en píxeles de interfaz). Mínimos y máximos controlados
    // para que texto y botones no se descuadren.
    public int windowMinWidth = 300;
    public int windowMinHeight = 170;
    public int windowMaxWidth = 760;
    public int windowMaxHeight = 520;
    /** Últimos jugadores a los que has escrito por privado (el más reciente primero). */
    public List<String> whisperContacts = new ArrayList<>();
    /** Bandera plegada (queda asomando una tira para volver a desplegarla). */
    public boolean bannerHidden = false;

    // Posición y tamaño de la ventana como PROPORCIÓN de la pantalla (0..1; -1 = automático).
    // Al ser proporcionales, todo se readapta solo cuando cambia el tamaño de la ventana del juego.
    public float winXr = -1f, winYr = -1f, winWr = -1f, winHr = -1f;
    // Posición de la pastilla minimizada, proporcional (-1 = según minimizedAnchor)
    public float pillXr = -1f, pillYr = -1f;

    /** Comandos del servidor (donaciones, mensajes privados...). */
    public ServerProfile server = new ServerProfile();

    // Ventana minimizada (pastilla flotante)
    public boolean minimized = false;
    /** La pastilla se ve siempre que estés en un mundo con el panel cerrado (no solo tras minimizar). */
    public boolean pillAlwaysVisible = true;
    /** top_left | top_right | bottom_left | bottom_right */
    public String minimizedAnchor = "top_left";

    // HUD anclado
    public boolean hudPinned = true;
    /** top_left | top_right | bottom_left | bottom_right */
    public String hudAnchor = "bottom_left";
    public int hudLines = 6;
    public int hudWidth = 260;
    public float hudOpacity = 0.65f;
    public int hudFadeSeconds = 12;
    public boolean hudAlwaysVisible = false;
    /** id de canal o "all" */
    public String hudChannel = "all";

    // Popups
    public boolean popups = true;
    public int popupSeconds = 6;
    public int actionPopupSeconds = 15;

    // Menciones
    public boolean mentionHighlight = true;
    public List<String> highlightWords = new ArrayList<>();

    public int historyPerChannel = 300;

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
        if (channels == null || channels.isEmpty()) channels = defaultChannels();
        if (highlightWords == null) highlightWords = new ArrayList<>();
        if (theme == null) theme = "primal";
        if (hudAnchor == null) hudAnchor = "bottom_left";
        if (minimizedAnchor == null) minimizedAnchor = "top_left";
        commandFeedbackSeconds = Math.max(1, Math.min(10, commandFeedbackSeconds));
        windowMinWidth = Math.max(240, Math.min(500, windowMinWidth));
        windowMinHeight = Math.max(140, Math.min(400, windowMinHeight));
        windowMaxWidth = Math.max(windowMinWidth + 40, Math.min(1600, windowMaxWidth));
        windowMaxHeight = Math.max(windowMinHeight + 40, Math.min(1200, windowMaxHeight));
        if (server == null) server = new ServerProfile();
        if (whisperContacts == null) whisperContacts = new ArrayList<>();
        server.sanitize();
        if (hudChannel == null) hudChannel = "all";
        hudLines = Math.max(1, Math.min(20, hudLines));
        hudWidth = Math.max(120, Math.min(600, hudWidth));
        hudOpacity = Math.max(0.05f, Math.min(1f, hudOpacity));
        menuOpacity = Math.max(0.15f, Math.min(1f, menuOpacity));
        historyPerChannel = Math.max(20, Math.min(2000, historyPerChannel));
        boolean hasFallback = false;
        for (ChannelDef c : channels) if (c.isFallback()) hasFallback = true;
        if (!hasFallback) channels.add(0, new ChannelDef("general", "General", "", "#BFD4FF"));
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
                if (i != 0) whisperContacts.add(0, whisperContacts.remove(i)); // la más reciente primero
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

    /**
     * Los patrones son punto de partida: cada servidor formatea el chat distinto.
     * Ajústalos en config/hoennascension.json y usa /hoennascension reload.
     */
    private static List<ChannelDef> defaultChannels() {
        List<ChannelDef> l = new ArrayList<>();
        l.add(new ChannelDef("general", "General", "", "#BFD4FF"));
        l.add(new ChannelDef("teleport", "Teleport",
                "(has requested to teleport|wants to teleport to you|solicitud de teletransporte|quiere teletransportarse)",
                "#5CFFE0").popup("orb").actions("/tpaccept", "/tpdeny"));
        l.add(new ChannelDef("private", "Privado",
                "(\\[pm\\]|whispers to you|te susurra|->\\s*(me|you|yo)\\b)",
                "#A481DE").popup("orb"));
        l.add(new ChannelDef("clan", "Clan", "\\[(clan|clán)\\]", "#47B5F8"));
        l.add(new ChannelDef("legendary", "Legendarios", "(legendary|legendario)", "#EC7D8B").popup("levelup"));
        l.add(new ChannelDef("mega", "Mega", "\\bmega\\b", "#FF5A3C").popup("levelup"));
        l.add(new ChannelDef("trade", "Mercado", "\\b(gts|wonder ?trade|intercambio)\\b", "#FFC247"));
        return l;
    }
}