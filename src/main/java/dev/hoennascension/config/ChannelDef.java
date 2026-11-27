package dev.hoennascension.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Definición de un canal. Todo es editable desde config/hoennascension.json */
public final class ChannelDef {
    public String id = "";
    public String name = "";
    /** Regex (find, sin distinguir mayúsculas). Vacío = canal comodín (fallback). */
    public String pattern = "";
    public String color = "#7FD0FF";
    /** Icono del canal (all, general, teleport, private, clan, legendary, mega, trade...). Vacío = el id del canal. */
    public String icon = "";
    public boolean popup = false;
    public boolean hideFromVanilla = false;
    public boolean muted = false;
    /** none | orb | levelup */
    public String sound = "none";
    /** Prefijo al escribir desde esta pestaña (ej. "/clan chat "). Vacío = chat normal. */
    public String sendPrefix = "";
    /** Si están definidos, el popup es accionable (teclas aceptar/rechazar). */
    public String acceptCommand = "";
    public String denyCommand = "";

    // ---- Filtros avanzados (todos opcionales) --------------------------------------------

    /** Solo mensajes del juego/servidor: ignora el chat escrito por jugadores. */
    public boolean gameOnly = false;
    /** Regex extra que TAMBIÉN debe coincidir (p. ej. que sea un aviso de aparición). Vacío = sin condición. */
    public String requirePattern = "";
    /** Solo mensajes que son tuyos: mencionan tu nombre o coinciden con selfPattern. */
    public boolean ownOnly = false;
    public String selfPattern = "";
    /** Solo si el mensaje trae coordenadas (coordPatterns) y están a menos de nearbyBlocks del jugador. */
    public boolean nearbyOnly = false;
    public int nearbyBlocks = 256;
    /** Si no se pueden leer coordenadas del mensaje: true = se muestra igualmente, false = se descarta. */
    public boolean nearbyShowUnknown = true;
    /** Lo descartado por lejanía también se oculta del chat normal. */
    public boolean hideRejected = false;
    /** Regex con grupos con nombre (?<x>..) (?<z>..) y opcionalmente (?<y>..). Se prueban en orden. */
    public List<String> coordPatterns = new ArrayList<>();
    /** Regex que, si coincide, EXCLUYE el mensaje del canal (p. ej. las solicitudes que envías tú). */
    public String excludePattern = "";
    /** Oculto en el sidebar (canales internos, como el de susurros). */
    public boolean hidden = false;
    /** Qué muestra el canal: log (mensajes) | teleport (cuadrícula del clan) | wondertrade | raids (amigos). */
    public String view = "log";
    /** Cómo se da formato al mensaje: formatPatterns con grupos (?<rank>) (?<name>) (?<msg>) -> "[Rango] Nombre: mensaje". */
    public List<String> formatPatterns = new ArrayList<>();
    /** Lectura especial de los mensajes: "gts" = compras y ventas del GTS. */
    public String parser = "";
    /** Regex con grupo (?<pokemon>..) para saber de qué Pokémon habla el aviso (sirve para localizarlo). */
    public List<String> pokemonPatterns = new ArrayList<>();
    /** Subfiltros del canal (botones): nombre de cada uno y la regex que clasifica los mensajes. */
    public List<String> subFilters = new ArrayList<>();
    public List<String> subPatterns = new ArrayList<>();

    private transient Pattern compiled;
    private transient boolean compileFailed;
    private static final Map<String, Pattern> CACHE = new HashMap<>();

    public ChannelDef() {}

    public ChannelDef(String id, String name, String pattern, String color) {
        this.id = id;
        this.name = name;
        this.pattern = pattern;
        this.color = color;
    }

    public ChannelDef popup(String sound) {
        this.popup = true;
        this.sound = sound;
        return this;
    }

    public ChannelDef actions(String accept, String deny) {
        this.acceptCommand = accept;
        this.denyCommand = deny;
        return this;
    }

    public String iconName() {
        return (icon != null && !icon.isBlank()) ? icon : id;
    }

    public String viewName() {
        return (view == null || view.isBlank()) ? "log" : view;
    }

    /** Canal comodín (sin patrón y de tipo log): lo que no encaja en ningún otro. */
    public boolean isFallback() {
        return (pattern == null || pattern.isBlank()) && viewName().equals("log");
    }

    public boolean excluded(String text) {
        return excludePattern != null && !excludePattern.isBlank() && find(excludePattern, text);
    }

    /** Valor de un grupo con nombre o null si el patrón no lo tiene. */
    public static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    public boolean isActionable() {
        return acceptCommand != null && !acceptCommand.isBlank();
    }

    public boolean hasSubFilters() {
        return subFilters != null && !subFilters.isEmpty();
    }

    // ---- Regex con caché ------------------------------------------------------------------

    /** Compila (y recuerda) una regex sin distinguir mayúsculas. @return null si está vacía o es inválida. */
    public static synchronized Pattern compile(String regex) {
        if (regex == null || regex.isBlank()) return null;
        if (CACHE.containsKey(regex)) return CACHE.get(regex);
        Pattern p = null;
        try {
            p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException ignored) {
        }
        CACHE.put(regex, p);
        return p;
    }

    public static boolean find(String regex, String text) {
        Pattern p = compile(regex);
        return p != null && p.matcher(text).find();
    }

    public boolean matches(String text) {
        if (isFallback()) return false;
        if (compiled == null && !compileFailed) {
            compiled = compile(pattern);
            compileFailed = compiled == null;
        }
        return compiled != null && compiled.matcher(text).find();
    }

    /** ¿Cumple la condición extra (requirePattern)? */
    public boolean requireOk(String text) {
        return requirePattern == null || requirePattern.isBlank() || find(requirePattern, text);
    }

    /** Subfiltro al que pertenece el mensaje (el primero cuya regex coincide) o null. */
    public String classify(String text) {
        if (!hasSubFilters() || subPatterns == null) return null;
        for (int i = 0; i < subFilters.size() && i < subPatterns.size(); i++) {
            if (find(subPatterns.get(i), text)) return subFilters.get(i);
        }
        return null;
    }

    public int rgb() {
        try {
            return Integer.parseInt(color.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return 0x7FD0FF;
        }
    }
}