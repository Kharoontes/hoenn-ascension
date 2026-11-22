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