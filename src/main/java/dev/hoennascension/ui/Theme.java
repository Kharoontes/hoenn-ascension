package dev.hoennascension.ui;

/**
 * Temas de color. Kyogre / Groudon / Primal salen del logo; Alba (Mega Absol) y Abismo (Mega Banette)
 * son los temas "claro" y "oscuro profundo". Al cambiar de tema, {@link #byId(String)} devuelve una
 * paleta mezclada durante unos instantes, así toda la interfaz hace una transición suave.
 */
public final class Theme {
    public static final Theme KYOGRE = new Theme(0, "kyogre", "Kyogre",
            0xFF06185A, 0xFF0F4AA8, 0xFF0C3ED8, 0xFF2F8CF0, 0xFF47B5F8,
            0xFFE8EEFF, 0xFF8A94C0, 0x55000000, false, 1.0f);
    public static final Theme GROUDON = new Theme(1, "groudon", "Groudon",
            0xFF3A0608, 0xFF8A1410, 0xFFC20207, 0xFFE8451F, 0xFFFFA24A,
            0xFFE8EEFF, 0xFF8A94C0, 0x55000000, false, 1.0f);
    public static final Theme PRIMAL = new Theme(2, "primal", "Primal",
            0xFF16086A, 0xFF3A1688, 0xFF0C3ED8, 0xFF8A55E8, 0xFFE0202A,
            0xFFE8EEFF, 0xFF8A94C0, 0x55000000, false, 1.0f);
    /** Blanco Angelical (Mega Absol): fondo claro plateado, borde azul plata -> rojo de sus ojos. */
    public static final Theme ALBA = new Theme(3, "alba", "Dawn",
            0xFFF8FBFF, 0xFFB7C8EA, 0xFF6F8FD0, 0xFF9FB0D6, 0xFFE04B66,
            0xFF16264A, 0xFF50618A, 0x40283C6E, true, 1.55f);
    /** Negro Maldito (Mega Banette): casi negro con violeta y carmesí. */
    public static final Theme ABISMO = new Theme(4, "abismo", "Abyss",
            0xFF07030D, 0xFF22093A, 0xFF5A189A, 0xFF9D4EDD, 0xFFD7263D,
            0xFFEADFF5, 0xFF9A86B3, 0x66000000, false, 1.25f);

    private static final Theme[] ALL = {KYOGRE, GROUDON, PRIMAL, ALBA, ABISMO};
    private static final long TRANSITION_MS = 450;

    public final int index;
    public final String id;
    public final String label;
    public final int bgTop, bgBottom;
    /** Borde: izquierda, centro, derecha. */
    public final int edgeA, edgeB, edgeC;
    public final int text, muted;
    /** Fondo de las barras de escritura. */
    public final int inputBg;
    /** Tema claro: texto oscuro y sin sombra de texto. */
    public final boolean light;
    /** Multiplicador de opacidad de los fondos (los temas claros necesitan más para leerse). */
    public final float opacityMul;

    private Theme(int index, String id, String label, int bgTop, int bgBottom, int a, int b, int c,
                  int text, int muted, int inputBg, boolean light, float opacityMul) {
        this.index = index;
        this.id = id;
        this.label = label;
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

    /** Color del degradado horizontal en t = 0..1 */
    public int edgeAt(float t) {
        if (t < 0.5f) return Gfx.lerp(edgeA, edgeB, t * 2f);
        return Gfx.lerp(edgeB, edgeC, (t - 0.5f) * 2f);
    }

    public Theme next() {
        return ALL[(index + 1) % ALL.length];
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
        return new Theme(b.index, b.id, b.label,
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