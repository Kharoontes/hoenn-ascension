package dev.hoennascension.ui;

import net.minecraft.text.Text;

/**
 * Temas de color, nombrados por el color que los identifica: Azul, Rojo, Violeta, Negro y Verde.
 * Al cambiar de tema, {@link #byId(String)} devuelve una paleta mezclada durante unos instantes, así toda la
 * interfaz hace una transición suave.
 */
public final class Theme {
    public static final Theme KYOGRE = new Theme(0, "kyogre",
            0xFF06185A, 0xFF0F4AA8, 0xFF0C3ED8, 0xFF2F8CF0, 0xFF47B5F8,
            0xFFE8EEFF, 0xFF8A94C0, 0x55000000, false, 1.0f);
    public static final Theme GROUDON = new Theme(1, "groudon",
            0xFF3A0608, 0xFF8A1410, 0xFFC20207, 0xFFE8451F, 0xFFFFA24A,
            0xFFE8EEFF, 0xFF8A94C0, 0x55000000, false, 1.0f);
    public static final Theme PRIMAL = new Theme(2, "primal",
            0xFF16086A, 0xFF3A1688, 0xFF0C3ED8, 0xFF8A55E8, 0xFFE0202A,
            0xFFE8EEFF, 0xFF8A94C0, 0x55000000, false, 1.0f);
    /** Negro: casi negro con violeta y carmesí. También es el estilo de la pantalla de ajustes. */
    public static final Theme ABISMO = new Theme(3, "abismo",
            0xFF07030D, 0xFF22093A, 0xFF5A189A, 0xFF9D4EDD, 0xFFD7263D,
            0xFFEADFF5, 0xFF9A86B3, 0x66000000, false, 1.25f);
    /** Verde: tonos esmeralda. */
    public static final Theme VERDE = new Theme(4, "verde",
            0xFF03170E, 0xFF0E4A2B, 0xFF0B7A3E, 0xFF22C76E, 0xFF9BF0B5,
            0xFFE6FFF0, 0xFF86BFA0, 0x55000000, false, 1.1f);

    private static final Theme[] ALL = {KYOGRE, GROUDON, PRIMAL, ABISMO, VERDE};
    private static final long TRANSITION_MS = 450;

    public final int index;
    public final String id;
    public final int bgTop, bgBottom;
    /** Borde: izquierda, centro, derecha. */
    public final int edgeA, edgeB, edgeC;
    public final int text, muted;
    /** Fondo de las barras de escritura. */
    public final int inputBg;
    /** Tema claro: texto oscuro y sin sombra de texto. */
    public final boolean light;
    /** Multiplicador de opacidad de los fondos. */
    public final float opacityMul;

    private Theme(int index, String id, int bgTop, int bgBottom, int a, int b, int c,
                  int text, int muted, int inputBg, boolean light, float opacityMul) {
        this.index = index;
        this.id = id;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.edgeA = a;
        this.edgeB = b;
        this.edgeC = c;
        this.text = text;
        this.muted = muted;
        this.inputBg = inputBg;
        this.light = light;
        this.opacityMul = opacityMul;
    }

    /** Nombre del tema = el color que lo identifica (traducido). */
    public String displayName() {
        return Text.translatable("hoennascension.theme." + id).getString();
    }

    /** Color del degradado horizontal en t = 0..1 */
    public int edgeAt(float t) {
        if (t < 0.5f) return Gfx.lerp(edgeA, edgeB, t * 2f);
        return Gfx.lerp(edgeB, edgeC, (t - 0.5f) * 2f);
    }

    public Theme next() {
        return ALL[(index + 1) % ALL.length];
    }

    public static Theme[] all() {
        return ALL.clone();
    }

    // ---- Transición animada -------------------------------------------------------------

    private static Theme from;
    private static Theme to;
    private static long start;

    private static Theme find(String id) {
        for (Theme t : ALL) if (t.id.equalsIgnoreCase(id)) return t;
        return PRIMAL;
    }

    private static float ease(float t) {
        return t * t * (3f - 2f * t);
    }

    private static Theme blend(Theme a, Theme b, float t) {
        return new Theme(b.index, b.id,
                Gfx.lerp(a.bgTop, b.bgTop, t), Gfx.lerp(a.bgBottom, b.bgBottom, t),
                Gfx.lerp(a.edgeA, b.edgeA, t), Gfx.lerp(a.edgeB, b.edgeB, t), Gfx.lerp(a.edgeC, b.edgeC, t),
                Gfx.lerp(a.text, b.text, t), Gfx.lerp(a.muted, b.muted, t), Gfx.lerp(a.inputBg, b.inputBg, t),
                b.light, a.opacityMul + (b.opacityMul - a.opacityMul) * t);
    }

    private static Theme current(long now) {
        float t = Math.min(1f, (now - start) / (float) TRANSITION_MS);
        return t >= 1f ? to : blend(from, to, ease(t));
    }

    /** Paleta que debe usarse ahora mismo para el tema configurado (mezclada si hay transición). */
    public static Theme byId(String id) {
        Theme target = find(id);
        long now = System.currentTimeMillis();
        if (to == null) {
            to = target;
            from = target;
            start = now - TRANSITION_MS;
        }
        if (target != to) {
            from = current(now);
            to = target;
            start = now;
        }
        return current(now);
    }
}