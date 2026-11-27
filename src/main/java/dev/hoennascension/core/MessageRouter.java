package dev.hoennascension.core;

import dev.hoennascension.HoennAscensionClient;
import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clasifica cada mensaje entrante en un canal (aplicando los filtros del canal), le da formato y dispara popups
 * y sonidos. Lo que no pertenece a ningún canal no se toca: el chat normal sigue funcionando igual.
 */
public final class MessageRouter {
    private MessageRouter() {}

    /** Resultado de clasificar un mensaje. drop = descartado por el filtro del canal (p. ej. aparición lejana). */
    private record Route(ChannelDef channel, boolean drop, String sub, int dist, int[] pos) {}

    /**
     * @param fromPlayer true si es chat escrito por un jugador; false si es un mensaje del juego/servidor.
     * @return true si el mensaje debe seguir apareciendo en el chat vanilla.
     */
    public static boolean accept(Text text, boolean fromPlayer) {
        HoennConfig cfg = HoennConfig.get();
        if (!cfg.enabled) return true;
        String plain = text.getString().trim();
        if (plain.isEmpty()) return true;
        DonationTracker.onMessage(plain);
        Wondertrade.onMessage(plain);

        Route route = route(cfg, plain, fromPlayer);
        if (route == null) {
            notifyMention(cfg, plain);
            return true;
        }
        ChannelDef ch = route.channel();
        if (route.drop()) return !ch.hideRejected;

        ChatEntry entry = new ChatEntry(System.currentTimeMillis(), ch.id, plain, false, null, false);
        entry.sub = route.sub();
        entry.dist = route.dist();
        if (route.pos() != null) {
            entry.hasPos = true;
            entry.x = route.pos()[0];
            entry.y = route.pos()[1];
            entry.z = route.pos()[2];
        }
        String[] parts = applyFormat(ch, plain, entry);   // {rango, nombre, mensaje} del chat del clan, o null
        String sender = parts != null ? parts[1] : null;

        // Susurros: se averigua con quién es la conversación
        boolean outgoing = false;
        boolean echo = false;
        if (ch.id.equals("private")) {
            Whispers.Parsed p = Whispers.parse(plain);
            if (p != null) {
                entry.peer = p.peer();
                outgoing = p.outgoing();
                echo = outgoing && Whispers.isEchoOfSent(plain);
                if (!echo) cfg.addContact(p.peer());
            }
        }

        // Nunca se avisa de lo que escribes tú: solo de los mensajes de otros jugadores
        boolean own = outgoing || isMine(plain, sender);
        entry.mention = !own && cfg.mentionHighlight && mentions(cfg, plain);

        if (!echo) {
            HoennAscensionClient.STORE.add(entry);
            if (route.pos() == null && ch.pokemonPatterns != null && !ch.pokemonPatterns.isEmpty()) {
                SpawnLocator.watch(entry, pokemonName(ch, plain));
            }
            if (ch.id.equals("clan")) {
                if (parts != null) DiscordBridge.relay(parts[0], parts[1], parts[2]);
                else DiscordBridge.relay(null, null, plain);
            }
        }

        if (!own && !ch.muted && (ch.popup || entry.mention)) {
            if (cfg.popups) {
                HoennAscensionClient.POPUPS.push(ch.popup ? ch.name : "@", entry.shown() + entry.posSuffix(), ch);
            }
            String snd = ch.sound;
            if ((snd == null || snd.equals("none")) && entry.mention) snd = "orb";
            HoennAscensionClient.playSound(snd);
        }

        // Ocultar del chat normal: global, por canal, el aviso de Mega o la respuesta a un comando lanzado desde el mod
        boolean hideMega = cfg.hideMegaNotice && ch.id.equals("spawns") && "mega".equals(entry.sub);
        boolean hide = cfg.hideAllFromVanilla || ch.hideFromVanilla || hideMega
                || (cfg.hideCommandFeedback && Commands.recentlyRan(cfg.commandFeedbackSeconds));
        return !hide;
    }

    // ---- Clasificación -------------------------------------------------------------------

    private static Route route(HoennConfig cfg, String plain, boolean fromPlayer) {
        // Un susurro siempre va al canal Privado, aunque el patrón de ese canal en tu config no lo cubra
        if (Whispers.parse(plain) != null) {
            ChannelDef priv = cfg.channel("private");
            if (priv != null) return new Route(priv, false, null, -1, null);
        }
        for (ChannelDef c : cfg.channels) {
            if (!c.matches(plain)) continue;
            if (c.gameOnly && fromPlayer) continue;              // solo mensajes del juego
            if (!c.requireOk(plain) || c.excluded(plain)) continue;
            if (c.ownOnly && !isOwn(c, plain)) continue;          // solo lo que es tuyo (p. ej. tus compras del GTS)

            int[] pos = null;
            int dist = -1;
            if (c.coordPatterns != null && !c.coordPatterns.isEmpty()) {
                double[] xyz = locate(c, plain);
                if (xyz != null) {
                    pos = new int[]{(int) Math.round(xyz[0]), (int) Math.round(xyz[1]), (int) Math.round(xyz[2])};
                    dist = distance(xyz[0], xyz[2]);
                }
            }
            if (c.nearbyOnly) {
                if (dist >= 0 && dist > c.nearbyBlocks) return new Route(c, true, null, dist, pos);
                if (dist < 0 && !c.nearbyShowUnknown) return new Route(c, true, null, -1, null);
            }
            return new Route(c, false, c.classify(plain), dist, pos);
        }
        return null;
    }

    /**
     * Re-clasifica una entrada del historial con las reglas actuales.
     * @return false si ya no pertenece a ningún canal (se descarta).
     */
    public static boolean reclassify(ChatEntry e) {
        HoennConfig cfg = HoennConfig.get();
        if ("private".equals(e.channel) && e.peer != null) return true;
        String plain = e.text;
        if (plain == null) return false;
        Whispers.Parsed w = Whispers.parse(plain);
        if (w != null && cfg.channel("private") != null) {
            e.channel = "private";
            e.peer = w.peer();
            return true;
        }
        for (ChannelDef c : cfg.channels) {
            if (!c.matches(plain) || !c.requireOk(plain) || c.excluded(plain)) continue;
            if (c.ownOnly && !isOwn(c, plain)) continue;
            e.channel = c.id;
            e.sub = c.classify(plain);
            e.display = null;
            applyFormat(c, plain, e);
            return true;
        }
        return false;
    }

    // ---- Formato -------------------------------------------------------------------------

    /**
     * Da formato al mensaje según el canal: el chat del clan queda como "[Rango] Nombre: mensaje" y las
     * compras/ventas del GTS como "Vendiste X a Y".
     * @return {rango, nombre, mensaje} si se leyó un mensaje de chat; null si no.
     */
    private static String[] applyFormat(ChannelDef ch, String plain, ChatEntry e) {
        String[] parts = null;
        if (ch.formatPatterns != null && !ch.formatPatterns.isEmpty()) {
            parts = parseFormat(ch, plain);
            if (parts != null) {
                String rank = parts[0] != null && !parts[0].isBlank() ? "[" + parts[0].trim() + "] " : "";
                e.display = rank + parts[1] + ": " + parts[2].trim();
            }
        }
        if ("gts".equals(ch.parser)) {
            MarketParser.Result mk = MarketParser.parse(plain);
            if (mk != null) {
                e.display = MarketParser.display(mk);
                e.sub = mk.kind();
            }
        }
        return parts;
    }

    private static String[] parseFormat(ChannelDef ch, String plain) {
        for (String re : ch.formatPatterns) {
            Pattern p = ChannelDef.compile(re);
            if (p == null) continue;
            Matcher m = p.matcher(plain);
            if (!m.find()) continue;
            String name = ChannelDef.group(m, "name");
            String msg = ChannelDef.group(m, "msg");
            if (name == null || msg == null) continue;
            return new String[]{ChannelDef.group(m, "rank"), name, msg};
        }
        return null;
    }

    private static String pokemonName(ChannelDef ch, String plain) {
        for (String re : ch.pokemonPatterns) {
            Pattern p = ChannelDef.compile(re);
            if (p == null) continue;
            Matcher m = p.matcher(plain);
            if (m.find()) {
                String n = ChannelDef.group(m, "pokemon");
                if (n != null && !n.isBlank()) return n;
            }
        }
        return null;
    }

    // ---- Coordenadas ---------------------------------------------------------------------

    /** Coordenadas {x, y, z} que trae el mensaje, o null. Si no trae Y se usa la del jugador. */
    private static double[] locate(ChannelDef c, String plain) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (String regex : c.coordPatterns) {
            Pattern p = ChannelDef.compile(regex);
            if (p == null) continue;
            Matcher m = p.matcher(plain);
            if (!m.find()) continue;
            try {
                double x = Double.parseDouble(ChannelDef.group(m, "x"));
                double z = Double.parseDouble(ChannelDef.group(m, "z"));
                String ys = ChannelDef.group(m, "y");
                double y = ys != null ? Double.parseDouble(ys) : (mc.player != null ? mc.player.getY() : 0);
                return new double[]{x, y, z};
            } catch (NumberFormatException | NullPointerException e) {
                // el patrón no tiene los grupos x / z: se prueba el siguiente
            }
        }
        return null;
    }

    /** Distancia horizontal en bloques entre el jugador y un punto; -1 si no hay jugador. */
    private static int distance(double x, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        return (int) Math.round(Math.hypot(mc.player.getX() - x, mc.player.getZ() - z));
    }

    // ---- Autoría y menciones -------------------------------------------------------------

    private static String myName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return null;
        String me = mc.getSession().getUsername();
        return me == null || me.isEmpty() ? null : me;
    }

    /** ¿Lo escribí yo? Si se conoce el remitente se compara; si no, se busca mi nombre justo antes de ':' '»' o '>'. */
    private static boolean isMine(String plain, String sender) {
        String me = myName();
        if (me == null) return false;
        if (sender != null) return sender.equalsIgnoreCase(me);
        String head = plain.length() > 64 ? plain.substring(0, 64) : plain;
        Pattern p = Pattern.compile("(?:^|[\\s\\]<>])" + Pattern.quote(me) + "\\s*[>:\\u00bb\\u203a]",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return p.matcher(head).find();
    }

    /** ¿El mensaje trata de ti? Menciona tu nombre o coincide con la regex selfPattern del canal. */
    private static boolean isOwn(ChannelDef c, String plain) {
        String me = myName();
        if (me != null && plain.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT))) return true;
        return ChannelDef.find(c.selfPattern, plain);
    }

    private static boolean mentions(HoennConfig cfg, String plain) {
        String lower = plain.toLowerCase(Locale.ROOT);
        String me = myName();
        if (me != null) {
            String meLower = me.toLowerCase(Locale.ROOT);
            if (lower.contains(meLower) && !lower.startsWith("<" + meLower + ">")) return true;
        }
        for (String w : cfg.highlightWords) {
            if (w != null && !w.isBlank() && lower.contains(w.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** Mensajes ajenos a los canales del mod: solo se avisa si alguien te menciona (nunca por lo que escribes tú). */
    private static void notifyMention(HoennConfig cfg, String plain) {
        if (!cfg.mentionHighlight || !cfg.popups || isMine(plain, null) || !mentions(cfg, plain)) return;
        HoennAscensionClient.POPUPS.notice("mention", "clan", "@", plain, 0x47B5F8, cfg.popupSeconds);
        HoennAscensionClient.playSound("orb");
    }
}