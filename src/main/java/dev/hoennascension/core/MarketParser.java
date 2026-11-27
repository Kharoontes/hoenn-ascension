package dev.hoennascension.core;

import dev.hoennascension.config.ChannelDef;
import dev.hoennascension.config.HoennConfig;
import dev.hoennascension.config.ServerProfile;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee los mensajes del GTS y los convierte en compras y ventas con la otra persona implicada. */
public final class MarketParser {
    /** kind = "sell" (te compraron) o "buy" (compraste); player = la otra persona. */
    public record Result(String kind, String item, String player, String price) {}

    private MarketParser() {}

    public static Result parse(String plain) {
        ServerProfile sp = HoennConfig.get().server;
        Result r = tryAll("sell", sp.gtsSellPatterns, plain);
        return r != null ? r : tryAll("buy", sp.gtsBuyPatterns, plain);
    }

    private static Result tryAll(String kind, List<String> regexes, String text) {
        if (regexes == null) return null;
        for (String re : regexes) {
            Pattern p = ChannelDef.compile(re);
            if (p == null) continue;
            Matcher m = p.matcher(text);
            if (!m.find()) continue;
            String item = ChannelDef.group(m, "item");
            String player = ChannelDef.group(m, "player");
            if (item == null && player == null) continue;
            return new Result(kind, item == null ? null : item.trim(), player, ChannelDef.group(m, "price"));
        }
        return null;
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "?" : s;
    }

    /** Texto para mostrar: "Vendiste Pikachu a Bob · 1000" / "Compraste Pikachu a Bob · 1000". */
    public static String display(Result r) {
        String key = r.kind().equals("sell") ? "hoennascension.market.sell" : "hoennascension.market.buy";
        String s = Text.translatable(key, nz(r.item()), nz(r.player())).getString();
        if (r.price() != null && !r.price().isBlank()) s += " \u00b7 " + r.price();
        return s;
    }
}