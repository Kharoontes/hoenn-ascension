package dev.hoennascension.core;

public final class ChatEntry {
    public long time;
    public String channel;
    public String text;
    public boolean mention;
    /** Susurros: el otro jugador de la conversación (null si no se pudo saber). */
    public String peer;
    /** Susurros: enviado por ti desde el mod (eco local). */
    public boolean mine;
    /** Subfiltro del canal al que pertenece (p. ej. "legendary" o "mega"); null si no aplica. */
    public String sub;
    /** Distancia en bloques al jugador cuando el mensaje traía coordenadas; -1 si no se sabe. */
    public int dist = -1;

    public ChatEntry() {}

    public ChatEntry(long time, String channel, String text, boolean mention) {
        this(time, channel, text, mention, null, false);
    }

    public ChatEntry(long time, String channel, String text, boolean mention, String peer, boolean mine) {
        this.time = time;
        this.channel = channel;
        this.text = text;
        this.mention = mention;
        this.peer = peer;
        this.mine = mine;
    }
}