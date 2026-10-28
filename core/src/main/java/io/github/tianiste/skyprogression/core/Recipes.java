package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Buy or craft/forge: for an item that has a crafting or forge recipe (`mining-recipes.json`, generated from the NEU
 * repository), compares the current buy quote with the recipe's inputs (owned inputs first; each missing input at the
 * cheaper of its buy quote and its own recipe, a few levels deep) and takes the cheaper known path. The forge
 * time and the unlock (HOTM tier, collection) are shown; a recipe whose unlock is not met or cannot be checked is used
 * only when the item has no buy quote, and the path names the missing unlock.
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
    /**
     * coins: cheapest known way to get the items (null when neither path is known); detail explains the choice; locked:
     * the unlock a recipe still needs when it is the only way to get the item ("" otherwise); used: owned items the
     * recipes use up (so a plan can take them out of what later steps may use); forge: forge seconds over the recipes used.
     */
    public record Path(Double coins, String detail, String locked, Map<String, Integer> used, long forge) {
        public Path(Double coins, String detail) { this(coins, detail, "", Map.of(), 0); }
    }
    public static Set<String> inputIds() {
        Set<String> ids = new TreeSet<>();
        RECIPES.values().forEach(r -> ids.addAll(r.inputs().keySet()));
        return ids;
    }
    static Double quote(String id, Map<String, Price> prices) {
        Price p = prices.get(id);
        return p == null || !p.available || !(p.buy > 0) || !Double.isFinite(p.buy) ? null : p.buy;
    }
    /** Recipe levels followed below the requested item (Relic of Power -> Artifact -> Ring -> Talisman needs three). */
    static final int MAX_DEPTH = 5;
    /**
     * One resolved way to get `amount` of an item: coins (null = unknown), the path taken ("buy", "craft", "forge", or ""
     * when unknown), steps (one line per recipe used, the requested item first), forge seconds summed over the recipes
     * used, the unlocks still missing, why the price is unknown, and the owned items it used up.
     */
    private record Node(Double coins, String how, List<String> steps, long forge, Set<String> locked, String unknown, Map<String, Integer> used) {}
    /**
     * Cost of `amount` copies of the item: the buy quote or the recipe, whichever is cheaper and known. Recipe inputs are
     * taken from owned items first (each owned item counted once over the whole chain); a missing input costs the cheaper
     * known of its quote and its own recipe in turn, up to MAX_DEPTH recipe levels (a recipe that would
     * repeat an item already being made is not followed). A recipe whose unlock is not met is used only when the item has no buy quote; the
     * path then names the missing unlock.
     */
    public static Path acquire(String id, int amount, Profile profile, Map<String, Price> prices) { return acquire(id, amount, profile, prices, profile.owned); }
    /** As above, with recipe inputs taken from `owned` (what earlier materials or plan steps left) instead of the profile's items. */
    public static Path acquire(String id, int amount, Profile profile, Map<String, Price> prices, Map<String, Integer> owned) {
        Node n = resolve(id, amount, profile, prices, owned, new HashSet<>(), 0);
        if (n.coins() == null) return new Path(null, RECIPES.containsKey(id) ? "Price unknown: " + n.unknown() + "." : "");
        if (n.how().equals("buy")) return new Path(n.coins(), n.steps().isEmpty() ? "" : n.steps().getFirst());
        String locked = String.join(", ", n.locked());
        Double buy = quote(id, prices);
        String head = buy == null
                ? String.format(Locale.ROOT, "No buy quote; %s for %,.0f coins", n.how(), n.coins())
                : String.format(Locale.ROOT, "Cheaper to %s (%,.0f vs %,.0f to buy)", n.how(), n.coins(), buy * amount);
        return new Path(n.coins(), head + ": " + String.join("; ", n.steps()) + (n.forge() > 0 ? "; " + duration(n.forge()) + " forge time in total" : "")
                + (locked.isEmpty() ? "" : "; needs " + locked + " first") + ".", locked, n.used(), n.forge());
    }
    private static Node resolve(String id, int amount, Profile profile, Map<String, Price> prices, Map<String, Integer> owned, Set<String> making, int depth) {
        Double quote = quote(id, prices);
        Double buy = quote == null ? null : quote * amount;
        Node bought = new Node(buy, "buy", List.of(), 0, Set.of(), "", Map.of());
        Recipe r = RECIPES.get(id);
        if (r == null) return buy != null ? bought : unknown("no buy quote and no recipe for " + id);
        if (depth > MAX_DEPTH || making.contains(id))
            return buy != null ? bought : unknown("no buy quote for " + id + " and its recipe is more than " + MAX_DEPTH + " levels deep");
        String blocked = "";
        if (profile.hotm < r.hotm()) blocked = "HOTM " + r.hotm();
        else if (r.collection() != null && profile.collections == null) blocked = r.requires() + " (Collections API off, not checked)";
        else if (r.collection() != null && profile.collections.getOrDefault(r.collection(), 0.0) < r.collectionAmount()) blocked = r.requires();
        String how = r.type().equals("forge") ? "forge" : "craft";
        if (!blocked.isEmpty() && buy != null)
            return depth > 0 ? bought : new Node(buy, "buy", List.of("Buying; can't " + how + " it yet (needs " + blocked + ")."), 0, Set.of(), "", Map.of());
        int crafts = (int)Math.ceil(amount / Math.max(1, r.count()));
        Map<String, Integer> left = new Left(owned), used = new HashMap<>();
        Set<String> locked = new LinkedHashSet<>();
        if (!blocked.isEmpty()) locked.add(blocked);
        making.add(id);
        double craft = 0;
        long forge = r.duration() * crafts;
        List<String> sub = new ArrayList<>();
        StringJoiner parts = new StringJoiner(", ");
        String missing = "";
        for (var in : r.inputs().entrySet()) {
            int need = (int)Math.ceil(in.getValue() * crafts), have = Math.min(need, Math.max(0, left.getOrDefault(in.getKey(), 0)));
            if (have > 0) { left.merge(in.getKey(), -have, Integer::sum); used.merge(in.getKey(), have, Integer::sum); }
            parts.add(need + " " + in.getKey() + (have > 0 ? " (" + have + " owned)" : ""));
            if (need == have) continue;
            Node n = resolve(in.getKey(), need - have, profile, prices, left, making, depth + 1);
            if (n.coins() == null) { if (missing.isEmpty()) missing = n.unknown(); continue; }
            craft += n.coins();
            forge += n.forge();
            locked.addAll(n.locked());
            sub.addAll(n.steps());
            n.used().forEach((k, v) -> { left.merge(k, -v, Integer::sum); used.merge(k, v, Integer::sum); });
        }
        making.remove(id);
        String step = (depth == 0 ? how : id + ": " + how) + " from " + parts + (r.duration() > 0 ? " (" + duration(r.duration() * crafts) + ")" : "")
                + (r.requires().isEmpty() ? "" : ", needs " + r.requires());
        if (!missing.isEmpty()) {
            if (buy != null) return depth > 0 ? bought : new Node(buy, "buy", List.of("Buying; " + step + ", but " + missing + "."), 0, Set.of(), "", Map.of());
            return unknown("can't " + how + " " + id + ": " + missing);
        }
        if (buy != null && craft >= buy)
            return new Node(buy, "buy", depth == 0 ? List.of(String.format(Locale.ROOT, "Cheaper to buy (%,.0f vs %,.0f to %s).", buy, craft, how)) : List.of(), 0, Set.of(), "", Map.of());
        List<String> steps = new ArrayList<>();
        steps.add(step);
        steps.addAll(sub);
        return new Node(craft, how, steps, forge, locked, "", used);
    }
    /**
     * Owned counts still left: the changes over a base map that is neither copied nor modified (a plan prices hundreds of
     * upgrades against the full inventory). Reads see the change when there is one, else the base.
     */
    static final class Left extends AbstractMap<String, Integer> {
        private final Map<String, Integer> base, changed = new HashMap<>();
        Left(Map<String, Integer> base) { this.base = base; }
        @Override public Integer get(Object key) { return changed.containsKey(key) ? changed.get(key) : base.get(key); }
        @Override public boolean containsKey(Object key) { return changed.containsKey(key) ? changed.get(key) != null : base.containsKey(key); }
        @Override public Integer getOrDefault(Object key, Integer fallback) { Integer v = get(key); return v != null ? v : fallback; }
        @Override public Integer put(String key, Integer value) { Integer old = get(key); changed.put(key, value); return old; }
        @Override public Integer remove(Object key) { Integer old = get(key); if (key instanceof String k) changed.put(k, null); return old; }
        @Override public Integer merge(String key, Integer value, java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> f) {
            Integer old = get(key), now = old == null ? value : f.apply(old, value);
            changed.put(key, now);
            return now;
        }
        @Override public Set<Entry<String, Integer>> entrySet() {
            Map<String, Integer> all = new HashMap<>(base);
            changed.forEach((k, v) -> { if (v == null) all.remove(k); else all.put(k, v); });
            return all.entrySet();
        }
    }
    private static Node unknown(String why) { return new Node(null, "", List.of(), 0, Set.of(), why, Map.of()); }
    static String duration(long seconds) {
        if (seconds >= 3600) return String.format(Locale.ROOT, "%.1f h", seconds / 3600.0).replace(".0 h", " h");
        if (seconds >= 60) return seconds / 60 + " min";
        return seconds + " s";
    }
}
