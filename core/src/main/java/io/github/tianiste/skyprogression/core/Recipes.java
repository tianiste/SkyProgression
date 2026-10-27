package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Buy or craft/forge: for an item that has a crafting or forge recipe (`mining-recipes.json`, generated from the NEU
 * repository), compares the current buy quote with the recipe's inputs (owned inputs first, the rest at their buy
 * quotes) and takes the cheaper known path. One recipe level only; the forge time and the unlock (HOTM tier,
 * collection) are shown, and a recipe whose unlock is not met or cannot be checked is not used.
 */
public final class Recipes {
    private Recipes() {}
    record Recipe(String type, double count, long duration, Map<String, Double> inputs, String requires, int hotm, String collection, double collectionAmount) {}
    private static final Map<String, Recipe> RECIPES = load();
    private static Map<String, Recipe> load() {
        Map<String, Recipe> out = new HashMap<>();
        try (var stream = Recipes.class.getResourceAsStream("/mining-recipes.json")) {
            if (stream == null) return out;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.getAsJsonObject("recipes").entrySet()) {
                JsonObject r = e.getValue().getAsJsonObject();
                Map<String, Double> inputs = new LinkedHashMap<>();
                r.getAsJsonObject("inputs").entrySet().forEach(i -> inputs.put(i.getKey(), i.getValue().getAsDouble()));
                out.put(e.getKey(), new Recipe(r.get("type").getAsString(), r.has("count") ? r.get("count").getAsDouble() : 1,
                        r.has("duration") ? r.get("duration").getAsLong() : 0, inputs, r.has("requires") ? r.get("requires").getAsString() : "",
                        r.has("hotm") ? r.get("hotm").getAsInt() : 0, r.has("collection") ? r.get("collection").getAsString() : null,
                        r.has("collectionAmount") ? r.get("collectionAmount").getAsDouble() : 0));
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return out;
    }
    /** The item's crafting or forge recipe, or null when it has none. */
    static Recipe recipe(String id) { return RECIPES.get(id); }
    /** coins: cheapest known way to get the items (null when neither path is known); detail explains the choice. */
    public record Path(Double coins, String detail) {}
    public static Set<String> inputIds() {
        Set<String> ids = new TreeSet<>();
        RECIPES.values().forEach(r -> ids.addAll(r.inputs().keySet()));
        return ids;
    }
    static Double quote(String id, Map<String, Price> prices) {
        Price p = prices.get(id);
        return p == null || !p.available || !(p.buy > 0) || !Double.isFinite(p.buy) ? null : p.buy;
    }
    /** Cost of `amount` copies of the item: the buy quote or the recipe, whichever is cheaper and known. */
    public static Path acquire(String id, int amount, Profile profile, Map<String, Price> prices) {
        Double quote = quote(id, prices);
        Double buy = quote == null ? null : quote * amount;
        Recipe r = RECIPES.get(id);
        if (r == null) return new Path(buy, "");
        String blocked = "";
        if (profile.hotm < r.hotm()) blocked = "needs HOTM " + r.hotm();
        else if (r.collection() != null && profile.collections == null) blocked = "needs " + r.requires() + " (Collections API off, not checked)";
        else if (r.collection() != null && profile.collections.getOrDefault(r.collection(), 0.0) < r.collectionAmount()) blocked = "needs " + r.requires();
        String how = r.type().equals("forge") ? "forge" : "craft";
        if (!blocked.isEmpty()) return new Path(buy, "Buying; can't " + how + " it yet (" + blocked + ").");
        int crafts = (int)Math.ceil(amount / Math.max(1, r.count()));
        double craft = 0;
        StringJoiner parts = new StringJoiner(", ");
        boolean known = true;
        for (var in : r.inputs().entrySet()) {
            int need = (int)Math.ceil(in.getValue() * crafts), owned = Math.min(need, Math.max(0, profile.owned.getOrDefault(in.getKey(), 0)));
            Double q = quote(in.getKey(), prices);
            if (need > owned && q == null) known = false;
            else if (need > owned) craft += q * (need - owned);
            parts.add(need + " " + in.getKey() + (owned > 0 ? " (" + owned + " owned)" : ""));
        }
        String time = r.duration() > 0 ? ", " + duration(r.duration() * crafts) + " forge time" : "";
        String recipe = how + " from " + parts + time + (r.requires().isEmpty() ? "" : "; needs " + r.requires());
        if (!known) return new Path(buy, buy == null ? "Price unknown; " + recipe + ", but an input price is unknown." : "Buying; " + recipe + ", but an input price is unknown.");
        if (buy == null) return new Path(craft, String.format(Locale.ROOT, "No buy quote; %s for %,.0f coins.", recipe, craft));
        return craft < buy
                ? new Path(craft, String.format(Locale.ROOT, "Cheaper to %s (%,.0f vs %,.0f to buy): %s.", how, craft, buy, recipe))
                : new Path(buy, String.format(Locale.ROOT, "Cheaper to buy (%,.0f vs %,.0f to %s).", buy, craft, how));
    }
    static String duration(long seconds) {
        if (seconds >= 3600) return String.format(Locale.ROOT, "%.1f h", seconds / 3600.0).replace(".0 h", " h");
        if (seconds >= 60) return seconds / 60 + " min";
        return seconds + " s";
    }
}
