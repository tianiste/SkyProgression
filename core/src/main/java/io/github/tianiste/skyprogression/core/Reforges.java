package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reforges with mining stats (`mining-reforges.json`, generated from the NotEnoughUpdates repository, MIT). A
 * reforge missing from the table is assumed to give no mining stats. Abilities are not counted, except Fleet's
 * +50 Mining Spread towards Blocks on Block Fortune methods; the others are conditional (Heat, Cold, Fallen Stars,
 * Mining Fiesta, Mithril/Titanium) or unclear (Auspicious "+1% Mining Fortune").
 */
public final class Reforges {
    public record Reforge(String name, String stone, List<String> categories, Map<String, Double> costs, Map<String, Map<Stat, Double>> stats, String ability) {
        public boolean fits(Model.Item item) { return categories.contains(item.category); }
        /** Stats at the item's (effective) rarity for the method; null when the rarity is not listed. */
        public Map<Stat, Double> at(String tier, MiningMethod m) {
            Map<Stat, Double> s = stats.get(tier);
            if (s == null) return null;
            Map<Stat, Double> out = new EnumMap<>(Stat.class);
            out.putAll(s);
            if (name.equals("Fleet") && m.fortune == Stat.BLOCK_FORTUNE) out.merge(Stat.MINING_SPREAD, 50.0, Double::sum);
            return out;
        }
    }
    private static List<Reforge> all;
    public static synchronized List<Reforge> all() {
        if (all != null) return all;
        try (var stream = Reforges.class.getResourceAsStream("/mining-reforges.json")) {
            if (stream == null) throw new IllegalStateException("Missing reforge table");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Reforge> list = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("reforges")) {
                JsonObject o = e.getAsJsonObject();
                List<String> categories = new ArrayList<>();
                o.getAsJsonArray("categories").forEach(c -> categories.add(c.getAsString()));
                Map<String, Double> costs = new HashMap<>();
                if (o.has("costs")) o.getAsJsonObject("costs").entrySet().forEach(c -> costs.put(c.getKey(), c.getValue().getAsDouble()));
                Map<String, Map<Stat, Double>> stats = new HashMap<>();
                for (var tier : o.getAsJsonObject("stats").entrySet()) {
                    Map<Stat, Double> s = new EnumMap<>(Stat.class);
                    tier.getValue().getAsJsonObject().entrySet().forEach(v -> s.put(Stat.valueOf(v.getKey()), v.getValue().getAsDouble()));
                    stats.put(tier.getKey(), s);
                }
                list.add(new Reforge(o.get("name").getAsString(), o.has("stone") ? o.get("stone").getAsString() : null,
                        List.copyOf(categories), Map.copyOf(costs), Map.copyOf(stats), o.has("ability") ? o.get("ability").getAsString() : ""));
            }
            return all = List.copyOf(list);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    /** The reforge an item's API modifier names ("auspicious"), or null when it has no mining stats or is unknown. */
    public static Reforge byModifier(String modifier) {
        if (modifier == null || modifier.isBlank()) return null;
        String key = modifier.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        for (Reforge r : all()) if (r.name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").equals(key)) return r;
        return null;
    }
}
