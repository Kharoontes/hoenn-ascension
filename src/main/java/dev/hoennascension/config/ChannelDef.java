package dev.hoennascension.config;

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
    /** Prefijo al escribir desde esta pestaña (ej. "/clan "). Vacío = chat normal. */
    public String sendPrefix = "";
    /** Si están definidos, el popup es accionable (teclas aceptar/rechazar). */
    public String acceptCommand = "";
    public String denyCommand = "";

    private transient Pattern compiled;
    private transient boolean compileFailed;

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

    public boolean isFallback() {
        return pattern == null || pattern.isBlank();
    }

    public boolean isActionable() {
        return acceptCommand != null && !acceptCommand.isBlank();
    }

    public boolean matches(String text) {
        if (isFallback()) return false;
        if (compiled == null && !compileFailed) {
            try {
                compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (PatternSyntaxException e) {
                compileFailed = true;
            }
        }
        return compiled != null && compiled.matcher(text).find();
    }

    public int rgb() {
        try {
            return Integer.parseInt(color.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return 0x7FD0FF;
        }
    }
}