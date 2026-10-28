package io.github.tianiste.skyprogression.service;

import com.google.gson.*;
import com.google.gson.stream.*;
import io.github.tianiste.skyprogression.core.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * The website's player pages: one JSON answer per page (overview, gear, upgrades, methods, start, hotm), computed with the
 * same core classes and defaults the mod uses (best owned gear, the method's usual zone and default efficiency, no Type
 * Stats). Every view also carries the profile list, API settings, data age and the worn armor for the 3D model.
 */
final class WebViews {
    private WebViews() {}
    static final Set<String> VIEWS = Set.of("overview", "gear", "upgrades", "methods", "start", "hotm");
    /** Plan steps on the Upgrades page, as in the mod. */
    private static final int PLAN_STEPS = 12;
    /** Compact output; NaN and infinities (unknown rates) become null, which JSON can carry. */
    static final Gson GSON = new GsonBuilder().serializeNulls().registerTypeAdapter(Double.class, new Finite()).registerTypeAdapter(double.class, new Finite()).create();
    private static final class Finite extends TypeAdapter<Double> {
        @Override public void write(JsonWriter out, Double v) throws IOException { if (v == null || !Double.isFinite(v)) out.nullValue(); else out.value(v); }
        @Override public Double read(JsonReader in) throws IOException { return in.nextDouble(); }
    }

    /** What the page asked for: profile id ("" = selected), method, price mode, God Potion, best owned gear or what is worn. */
    /** cost: how purchases are priced (instant-buy by default). */
    record Query(String view, String profileId, MiningMethod method, MethodComparison.PriceMode mode, boolean god, boolean bestGear, CostMode cost) {
        Query(String view, String profileId, MiningMethod method, MethodComparison.PriceMode mode, boolean god, boolean bestGear) {
            this(view, profileId, method, mode, god, bestGear, CostMode.INSTANT_BUY);
        }
    }
    /** Skin texture hash and model ("slim" or "classic"); null when Mojang could not be asked. */
    record Skin(String hash, boolean slim) {}

    static Map<String, Object> build(Query q, PlayerData d, ProgressionEngine engine, Map<String, Price> prices, List<StatBreakdown.MayorPerk> mayor,
                                     String pricesAt, Skin skin) {
        PlayerData.Member member = d.member(q.profileId());
        Profile p = d.report.profiles.stream().filter(x -> x.id.equals(member.id())).findFirst().orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("view", q.view());
        out.put("player", d.player);
        out.put("uuid", d.uuid);
        out.put("fetchedAt", d.fetchedAt);
        out.put("pricesAt", pricesAt);
        out.put("profile", profileInfo(p));
        out.put("profiles", d.report.profiles.stream().map(WebViews::profileInfo).toList());
        out.put("api", api(member.member(), p));
        out.put("method", q.method().name());
        out.put("methodLabel", q.method().label);
        out.put("methods", Arrays.stream(MiningMethod.values()).map(m -> Map.of("id", m.name(), "label", m.label)).toList());
        out.put("priceMode", q.mode().name());
        out.put("priceModes", Arrays.stream(MethodComparison.PriceMode.values()).map(m -> Map.of("id", m.name(), "label", m.label, "meaning", m.meaning)).toList());
        out.put("costMode", q.cost().name());
        out.put("costModes", Arrays.stream(CostMode.values()).map(m -> Map.of("id", m.name(), "label", m.label, "meaning", m.meaning)).toList());
        out.put("godPotion", q.god());
        out.put("efficiency", q.method().defaultEfficiency());
        out.put("model", model(member.member(), skin));
        switch (q.view()) {
            case "overview" -> overview(out, q, d, engine, p, prices, mayor);
            case "gear" -> gear(out, q, d, mayor);
            case "upgrades" -> upgrades(out, q, d, engine, p, prices, mayor);
            case "methods" -> methods(out, q, d, prices, mayor);
            case "start" -> start(out, q, d, prices);
            case "hotm" -> hotm(out, member.member(), p);
            default -> throw new IllegalArgumentException("Unknown view");
        }
        return out;
    }

    private static Map<String, Object> profileInfo(Profile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id); m.put("name", p.name); m.put("mode", p.mode); m.put("selected", p.selected);
        return m;
    }
    /** Which SkyBlock API settings gave data; missing data reads as unknown on the page, with how to turn it on. */
    private static Map<String, Object> api(JsonObject member, Profile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inventory", p.inventoryEnabled);
        m.put("vault", !string(at(member, "inventory", "personal_vault_contents", "data"), "").isBlank());
        m.put("collections", at(member, "collection").isJsonObject());
        m.put("skills", !at(member, "player_data", "experience", "SKILL_MINING").isJsonNull());
        m.put("pets", at(member, "pets_data", "pets").isJsonArray());
        return m;
    }
    private static StatContext context(Query q) { return new StatContext(q.method().defaultZone(), q.method().target(), q.god()); }

    // Overview: the mod's Home tab.
    private static void overview(Map<String, Object> out, Query q, PlayerData d, ProgressionEngine engine, Profile p, Map<String, Price> prices,
                                 List<StatBreakdown.MayorPerk> mayor) {
        MiningMethod m = q.method();
        StatsReport r = d.stats(q.profileId(), m, "", true, context(q), mayor);
        Map<Stat, Double> stats = totals(r);
        List<Map<String, Object>> shown = new ArrayList<>();
        for (Stat s : m.relevantStats()) {
            StatsReport.Ledger l = r.ledger(s);
            if (l == null) continue;
            Map<String, Object> row = ledgerHead(l);
            row.put("top", l.contributions.stream().filter(Breakdown.Contribution::counts).sorted(Comparator.comparingDouble(x -> -Math.abs(share(l, x))))
                    .limit(5).map(x -> source(l, x)).toList());
            shown.add(row);
        }
        out.put("stats", shown);
        out.put("rate", rate(m, stats, r.ability, prices, q.mode()));
        out.put("tool", toolName(r));
        out.put("loadout", loadout(r));
        var basis = basis(m, r);
        var costs = q.cost().apply(prices);
        var plan = p.inventoryEnabled ? engine.plan(p, m, stats, costs, basis, 1) : List.<ProgressionEngine.Step>of();
        out.put("next", plan.isEmpty() ? null : step(plan.getFirst(), costs));
        out.put("comparison", comparison(d, q, prices));
    }

    // Gear: the stat breakdown by source (the mod's Gear tab, stat cards).
    private static void gear(Map<String, Object> out, Query q, PlayerData d, List<StatBreakdown.MayorPerk> mayor) {
        MiningMethod m = q.method();
        StatsReport r = d.stats(q.profileId(), m, "", q.bestGear(), context(q), mayor);
        out.put("bestGear", r.bestGear);
        out.put("tool", toolName(r));
        out.put("loadout", loadout(r));
        out.put("accessories", r.accessories);
        out.put("hotmTree", StatBreakdown.treeLabel(r.hotmTree));
        out.put("hotmTrees", r.hotmTrees);
        out.put("ability", r.ability);
        out.put("relevant", m.relevantStats());
        out.put("groups", StatBreakdown.GROUPS);
        out.put("breakingPower", m.breakingPower());
        List<Map<String, Object>> ledgers = new ArrayList<>();
        for (StatsReport.Ledger l : r.stats) {
            Map<String, Object> row = ledgerHead(l);
            row.put("flat", l.flat); row.put("multiplier", l.multiplier);
            row.put("equipped", l.equipped); row.put("bestOwned", l.bestOwned);
            row.put("unknownMaxima", l.unknownMaxima);
            row.put("notes", l.notes);
            row.put("sources", l.contributions.stream().map(x -> source(l, x)).toList());
            ledgers.add(row);
        }
        out.put("ledgers", ledgers);
    }

    // Upgrades: ranked list, step-by-step plan and the Other view (guide stage, can't-check bonuses, grinding).
    private static void upgrades(Map<String, Object> out, Query q, PlayerData d, ProgressionEngine engine, Profile p, Map<String, Price> prices,
                                 List<StatBreakdown.MayorPerk> mayor) {
        MiningMethod m = q.method();
        StatsReport r = d.stats(q.profileId(), m, "", true, context(q), mayor);
        Map<Stat, Double> stats = totals(r);
        var basis = basis(m, r);
        boolean inventory = p.inventoryEnabled;
        var costs = q.cost().apply(prices); // purchases at the chosen cost mode; sale values and method coins keep the quotes
        var ranked = inventory ? engine.upgrades(p, m, null, stats, costs, basis) : List.<Upgrade>of();
        var plan = inventory ? engine.plan(p, m, stats, costs, basis, PLAN_STEPS) : List.<ProgressionEngine.Step>of();
        var other = OtherUpgrades.of(engine, p, m, r, stats, m.defaultEfficiency(), costs, basis);
        // The best method by coins / hour, to show what each upgrade is worth there too.
        var compare = d.compare(q.profileId(), q.god());
        var rows = rows(compare, prices, q.mode());
        var best = rows.isEmpty() || rows.getFirst().totalCoinsPerHour() == null ? null : rows.getFirst();
        var bestEntry = best == null ? null : compare.entry(best.method());
        boolean rated = ranked.stream().anyMatch(u -> u.rateBefore() != null);
        out.put("rated", rated);
        out.put("rateModel", MethodRates.supported(m));
        out.put("unit", MethodRates.unit(m, r.ability));
        out.put("windowHours", MethodRates.windowHours(m, r.ability));
        out.put("coinsEach", MethodComparison.coinsPerItem(prices, m, q.mode()));
        out.put("bestMethod", best == null ? null : Map.of("id", best.method().name(), "label", best.method().label, "coinsPerHour", best.coinsPerHour() == null ? 0 : best.coinsPerHour()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Upgrade u : ranked) {
            Map<String, Object> row = upgrade(u, costs);
            if (best != null && best.method() != m && bestEntry != null && bestEntry.stats != null) {
                Double g = MethodComparison.gain(best.method(), bestEntry.stats, best.method().defaultEfficiency(), bestEntry.ability, u.delta(), u.cooldownFactor());
                row.put("bestGain", g);
            }
            list.add(row);
        }
        out.put("ranked", list);
        out.put("plan", plan.stream().map(s -> step(s, costs)).toList());
        out.put("other", other);
    }

    // Methods: coins / hour of every method and the selected method's rate in detail.
    private static void methods(Map<String, Object> out, Query q, PlayerData d, Map<String, Price> prices, List<StatBreakdown.MayorPerk> mayor) {
        MiningMethod m = q.method();
        var compare = d.compare(q.profileId(), q.god());
        out.put("comparison", comparison(compare, prices, q.mode()));
        StatsReport r = d.stats(q.profileId(), m, "", true, context(q), mayor);
        Map<Stat, Double> stats = totals(r);
        double eff = m.defaultEfficiency();
        StatContext.Zone zone = m.defaultZone();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("supported", MethodRates.supported(m));
        detail.put("zone", zone == null ? "" : zone.label);
        var entry = compare.entry(m);
        detail.put("tool", entry != null && !entry.tool.isEmpty() ? entry.tool : toolName(r));
        detail.put("ability", r.ability);
        if (MethodRates.supported(m)) try {
            var gem = MethodRates.gem(m);
            var block = MethodRates.block(m);
            double perHour;
            if (block == null) {
                var rate = MethodRates.gems(m, stats, eff, r.ability, zone);
                perHour = rate.flawlessPerHour();
                detail.put("kind", "gem");
                detail.put("gem", rate);
                detail.put("nextTick", MethodRates.nextTickSpeed(rate.speed(), gem.strength()));
                double next = MethodRates.nextTickSpeed(rate.speed(), gem.strength());
                detail.put("nextTicks", Double.isFinite(next) ? MethodRates.ticks(next, gem.strength()) : null);
            } else {
                var rate = MethodRates.blocks(m, stats, eff, r.ability, zone);
                perHour = rate.perHour();
                detail.put("kind", "block");
                detail.put("block", rate);
                detail.put("perWindow", rate.perWindow());
                double nextGround = block.strength() > 0 ? MethodRates.nextTickSpeed(rate.speed(), block.strength(), block.instamineFactor()) : Double.NaN;
                detail.put("nextGround", nextGround);
                if (!Double.isFinite(nextGround) && rate.airTicks() > 1 && block.strength() > 0)
                    detail.put("nextAir", 5 * MethodRates.nextTickSpeed(rate.speed() / 5, block.strength(), block.instamineFactor()) - 4);
                var a = r.ability;
                if (a != null && a.speedBoost() && rate.boostedGroundTicks() > 1 && block.strength() > 0)
                    detail.put("instamineInAbility", Math.floor(block.instamineFactor() * block.strength() / (1 + a.boostPercent() / 100)) + 1);
            }
            detail.put("perHour", perHour);
            List<Map<String, Object>> coins = new ArrayList<>();
            for (var mode : MethodComparison.PriceMode.values()) {
                Double each = MethodComparison.coinsPerItem(prices, m, mode);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("mode", mode.name()); c.put("label", mode.label); c.put("each", each);
                c.put("perHour", each == null ? null : perHour * each);
                c.put("source", MethodComparison.priceSource(prices, m, mode));
                coins.add(c);
            }
            detail.put("coins", coins);
            List<MethodComparison.DropValue> secondary = new ArrayList<>();
            for (var drop : MethodRates.secondary(m, stats, eff, r.ability, zone)) secondary.add(MethodComparison.value(drop, prices, q.mode()));
            detail.put("secondary", secondary);
        } catch (IllegalArgumentException e) { detail.put("error", e.getMessage()); }
        out.put("detail", detail);
        // Same change, different value: +50 Mining Fortune and +100 Mining Speed on each method's own stats.
        List<Map<String, Object>> same = new ArrayList<>();
        for (var row : rows(compare, prices, q.mode())) {
            var e = compare.entry(row.method());
            if (e == null || e.stats == null) continue;
            double effM = row.method().defaultEfficiency();
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("method", row.method().name()); s.put("label", row.method().label);
            s.put("fortune", MethodComparison.gain(row.method(), e.stats, effM, e.ability, Map.of(Stat.MINING_FORTUNE, 50.0), 1));
            s.put("speed", MethodComparison.gain(row.method(), e.stats, effM, e.ability, Map.of(Stat.MINING_SPEED, 100.0), 1));
            s.put("coinsPerHour", row.coinsPerHour());
            same.add(s);
        }
        out.put("sameChange", same);
    }

    // Start with: best owned loadout, where each piece is, and the swaps with their rate change.
    private static void start(Map<String, Object> out, Query q, PlayerData d, Map<String, Price> prices) {
        MiningMethod m = q.method();
        StartWith.Report r = d.start(q.profileId(), m, "", context(q));
        double eff = m.defaultEfficiency();
        Double each = MethodComparison.coinsPerItem(prices, m, q.mode());
        out.put("inventoryKnown", r.inventoryKnown);
        out.put("petsKnown", r.petsKnown);
        out.put("hotmTree", StatBreakdown.treeLabel(r.hotmTree));
        out.put("hotmTrees", r.hotmTrees);
        out.put("otherAccessories", r.otherAccessories);
        out.put("best", r.best);
        out.put("unit", MethodComparison.itemName(m));
        out.put("lowerBound", MethodComparison.lowerBound(m));
        out.put("coinsEach", each);
        out.put("current", scenario(m, r.current, eff));
        out.put("bestOwned", scenario(m, r.bestOwned, eff));
        List<Map<String, Object>> swaps = new ArrayList<>();
        for (var sw : r.swaps) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("slot", sw.slot()); s.put("from", sw.from()); s.put("to", sw.to());
            s.put("rate", rateOf(m, sw.after(), eff));
            Map<String, Double> delta = new LinkedHashMap<>();
            for (Stat st : m.relevantStats()) {
                double change = stat(sw.after().stats, st) - stat(r.current.stats, st);
                if (Math.abs(change) > 1e-9) delta.put(st.label, change);
            }
            s.put("delta", delta);
            s.put("ability", Objects.equals(sw.after().ability, r.current.ability) ? null : sw.after().ability);
            swaps.add(s);
        }
        // Largest rate first; swaps without a computable rate last (as in the mod).
        swaps.sort(Comparator.comparing((Map<String, Object> s) -> s.get("rate") == null ? 1 : 0).thenComparing(s -> s.get("rate") == null ? 0 : -(Double)s.get("rate")));
        out.put("swaps", swaps);
        // Best method with the gear worn now.
        List<MethodComparison.Row> worn = new ArrayList<>();
        for (var e : r.worn) if (e != null && e.method != null && e.stats != null)
            worn.add(MethodComparison.row(e.method, e.stats, e.complete, e.method.defaultEfficiency(), e.ability, prices, q.mode(), e.tool));
        var top = MethodComparison.rank(worn).stream().filter(x -> x.totalCoinsPerHour() != null).findFirst().orElse(null);
        out.put("wornBest", top == null ? null : Map.of("id", top.method().name(), "label", top.method().label, "coinsPerHour", top.totalCoinsPerHour()));
        out.put("notes", r.notes);
    }

    // HOTM: each Loadouts tree's perk levels and the powder, for the page to compare with a guide's tree.
    private static void hotm(Map<String, Object> out, JsonObject member, Profile p) {
        out.put("tier", p.hotm);
        Map<String, Object> powder = new LinkedHashMap<>();
        for (String type : List.of("mithril", "gemstone", "glacite")) {
            JsonElement unspent = at(member, "mining_core", "powder_" + type), spent = at(member, "mining_core", "powder_spent_" + type);
            if (unspent.isJsonNull() && spent.isJsonNull()) continue;
            powder.put(type, Map.of("unspent", number(unspent, 0), "spent", number(spent, 0)));
        }
        out.put("powder", powder);
        JsonElement nodes = at(member, "skill_tree", "nodes");
        List<Map<String, Object>> trees = new ArrayList<>();
        List<String> names = nodes.isJsonObject() ? StatBreakdown.hotmTrees(member) : List.of();
        for (String tree : names) trees.add(tree(StatBreakdown.treeLabel(tree), at(nodes, tree)));
        if (trees.isEmpty() && at(member, "mining_core", "nodes").isJsonObject()) trees.add(tree("HOTM tree", at(member, "mining_core", "nodes")));
        out.put("trees", trees);
        out.put("tokensSpent", number(at(member, "skill_tree", "tokens_spent", "mining"), -1));
    }
    private static Map<String, Object> tree(String label, JsonElement nodes) {
        Map<String, Integer> levels = new TreeMap<>();
        List<String> off = new ArrayList<>();
        if (nodes.isJsonObject()) for (var e : nodes.getAsJsonObject().entrySet()) {
            if (e.getKey().startsWith("toggle_")) {
                if ("false".equals(string(e.getValue(), "true"))) off.add(e.getKey().substring(7));
            } else if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) levels.put(e.getKey(), e.getValue().getAsInt());
        }
        return Map.of("label", label, "levels", levels, "off", off);
    }

    // 3D model: the worn armor (item ID, Minecraft material, dye colour, player-head texture) and the skin.
    private static final Map<Integer, String> MATERIALS = Map.of(298, "leather", 302, "chainmail", 306, "iron", 310, "diamond", 314, "golden");
    private static final String[] ARMOR_SLOTS = {"boots", "leggings", "chestplate", "helmet"};
    private static Map<String, Object> model(JsonObject member, Skin skin) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("skin", skin);
        List<Map<String, Object>> armor = new ArrayList<>();
        String data = string(at(member, "inventory", "inv_armor", "data"), "");
        if (!data.isBlank()) try {
            JsonElement items = new InventoryReader().read(data).get("i");
            // inv_armor lists boots first and the helmet last.
            if (items != null && items.isJsonArray()) for (int n = 0; n < items.getAsJsonArray().size() && n < 4; n++) {
                JsonElement item = items.getAsJsonArray().get(n);
                String id = string(at(item, "tag", "ExtraAttributes", "id"), "");
                if (id.isEmpty()) continue;
                Map<String, Object> piece = new LinkedHashMap<>();
                piece.put("slot", ARMOR_SLOTS[n]);
                piece.put("id", id);
                piece.put("name", string(at(item, "tag", "display", "Name"), "").replaceAll("§.", ""));
                int vanilla = (int)number(at(item, "id"), 0);
                String material = vanilla == 397 ? "skull" : null;
                for (var e : MATERIALS.entrySet()) if (vanilla >= e.getKey() && vanilla < e.getKey() + 4) material = e.getValue();
                piece.put("material", material);
                JsonElement color = at(item, "tag", "display", "color");
                piece.put("color", color.isJsonPrimitive() ? String.format(Locale.ROOT, "#%06x", color.getAsInt() & 0xFFFFFF) : null);
                piece.put("texture", skullTexture(item));
                armor.add(piece);
            }
        } catch (IOException | RuntimeException ignored) { /* No model armor; the page shows the skin alone. */ }
        out.put("armor", armor);
        return out;
    }
    /** Texture hash of a player head (textures.minecraft.net/texture/<hash>), or null. */
    private static String skullTexture(JsonElement item) {
        JsonElement textures = at(item, "tag", "SkullOwner", "Properties", "textures");
        if (!textures.isJsonArray() || textures.getAsJsonArray().isEmpty()) return null;
        try {
            String json = new String(Base64.getDecoder().decode(string(at(textures.getAsJsonArray().get(0), "Value"), "")), StandardCharsets.UTF_8);
            return textureHash(string(at(JsonParser.parseString(json), "textures", "SKIN", "url"), ""));
        } catch (RuntimeException e) { return null; }
    }
    static String textureHash(String url) {
        var match = java.util.regex.Pattern.compile("https?://textures\\.minecraft\\.net/texture/([0-9a-f]{32,64})").matcher(url);
        return match.matches() ? match.group(1) : null;
    }

    // Shared pieces.
    private static Map<Stat, Double> totals(StatsReport r) {
        Map<Stat, Double> out = new EnumMap<>(Stat.class);
        for (var l : r.stats) out.put(l.stat, l.total);
        return out;
    }
    private static ProgressionEngine.RateBasis basis(MiningMethod m, StatsReport r) {
        return new ProgressionEngine.RateBasis(m.defaultEfficiency(), r.ability, r.petStats, r.petOptions, r.profileUpgrades, r.resale);
    }
    /** The evaluated pieces with readable storage ("worn", "wardrobe slot 3") instead of the API's container keys. */
    private static List<StatsReport.Tool> loadout(StatsReport r) {
        return r.loadout.stream().map(t -> new StatsReport.Tool(t.key(), t.name(), StartWith.where(t.location()), t.id())).toList();
    }
    private static String toolName(StatsReport r) {
        return r.loadout.stream().filter(t -> t.key().equals(r.tool)).map(StatsReport.Tool::name).findFirst().orElse("");
    }
    private static Map<String, Object> ledgerHead(StatsReport.Ledger l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stat", l.stat.name()); row.put("label", l.stat.label); row.put("percent", l.stat.percent);
        row.put("total", l.total); row.put("attainable", l.attainable); row.put("attainableKnown", l.attainableKnown); row.put("complete", l.complete);
        return row;
    }
    /** What one counted source adds to the total: additive % sources add their share of the flat sum (as the mod shows it). */
    private static double share(StatsReport.Ledger l, Breakdown.Contribution x) {
        if (x.value() == null) return 0;
        return x.category().equals(Breakdown.ADDITIVE_PERCENT) ? l.flat * x.value() / 100 : x.value();
    }
    private static Map<String, Object> source(StatsReport.Ledger l, Breakdown.Contribution x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", x.source()); m.put("category", x.category()); m.put("value", x.value()); m.put("maximum", x.maximum());
        m.put("share", x.value() == null ? null : share(l, x));
        m.put("additive", x.category().equals(Breakdown.ADDITIVE_PERCENT));
        m.put("state", x.state().name()); m.put("confidence", x.confidence().name()); m.put("condition", x.condition()); m.put("note", x.note());
        return m;
    }
    private static Map<String, Object> rate(MiningMethod m, Map<Stat, Double> stats, MethodRates.Ability ability, Map<String, Price> prices,
                                            MethodComparison.PriceMode mode) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("supported", MethodRates.supported(m));
        out.put("unit", MethodComparison.itemName(m));
        out.put("lowerBound", MethodComparison.lowerBound(m));
        out.put("model", MethodComparison.model(m));
        out.put("window", MethodComparison.window(m, ability));
        Double perHour = null;
        if (MethodRates.supported(m)) try {
            double v = MethodRates.perHour(m, stats, m.defaultEfficiency(), ability, m.defaultZone());
            if (Double.isFinite(v)) perHour = v;
        } catch (IllegalArgumentException e) { out.put("error", e.getMessage()); }
        Double each = MethodComparison.coinsPerItem(prices, m, mode);
        out.put("perHour", perHour);
        out.put("coinsEach", each);
        out.put("coinsPerHour", perHour == null || each == null ? null : perHour * each);
        out.put("priceSource", each == null ? "" : MethodComparison.priceSource(prices, m, mode));
        return out;
    }
    private static Double rateOf(MiningMethod m, StartWith.Scenario s, double eff) {
        if (s == null || s.stats == null) return null;
        try { double v = MethodRates.perHour(m, s.stats, eff, s.ability); return Double.isFinite(v) ? v : null; }
        catch (IllegalArgumentException e) { return null; }
    }
    private static Map<String, Object> scenario(MiningMethod m, StartWith.Scenario s, double eff) {
        if (s == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Double> stats = new LinkedHashMap<>();
        for (Stat st : m.relevantStats()) stats.put(st.label, stat(s.stats, st));
        out.put("stats", stats); out.put("complete", s.complete); out.put("ability", s.ability); out.put("rate", rateOf(m, s, eff));
        return out;
    }
    private static List<MethodComparison.Row> rows(MethodComparison.Report r, Map<String, Price> prices, MethodComparison.PriceMode mode) {
        List<MethodComparison.Row> rows = new ArrayList<>();
        for (var e : r.methods) if (e != null && e.method != null && e.stats != null)
            rows.add(MethodComparison.row(e.method, e.stats, e.complete, e.method.defaultEfficiency(), e.ability, prices, mode, e.tool));
        return MethodComparison.rank(rows);
    }
    private static Map<String, Object> comparison(PlayerData d, Query q, Map<String, Price> prices) {
        return comparison(d.compare(q.profileId(), q.god()), prices, q.mode());
    }
    private static Map<String, Object> comparison(MethodComparison.Report r, Map<String, Price> prices, MethodComparison.PriceMode mode) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("godPotion", r.godPotion);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var row : rows(r, prices, mode)) {
            var e = r.entry(row.method());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("method", row.method().name()); m.put("label", row.method().label); m.put("item", row.item());
            m.put("itemsPerHour", row.itemsPerHour()); m.put("coinsPerItem", row.coinsPerItem()); m.put("coinsPerHour", row.coinsPerHour());
            m.put("totalCoinsPerHour", row.totalCoinsPerHour()); m.put("priceNote", row.priceNote()); m.put("efficiency", row.efficiency());
            m.put("model", row.model()); m.put("window", row.window()); m.put("drops", row.drops()); m.put("lowerBound", row.lowerBound());
            m.put("statsComplete", row.statsComplete()); m.put("tool", row.tool()); m.put("zone", e == null ? "" : e.zone); m.put("error", row.error());
            m.put("secondary", row.secondary());
            rows.add(m);
        }
        out.put("rows", rows);
        return out;
    }
    private static Map<String, Object> item(Item i) {
        if (i == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.id); m.put("name", i.name); m.put("group", i.group); m.put("category", i.category);
        m.put("modification", i.modification); m.put("source", i.source); m.put("location", i.location);
        return m;
    }
    private static Map<String, Object> upgrade(Upgrade u, Map<String, Price> prices) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item", item(u.item())); m.put("current", item(u.current()));
        Map<String, Double> delta = new LinkedHashMap<>();
        u.delta().forEach((s, v) -> { if (v != 0) delta.put(s.label, v); });
        m.put("delta", delta);
        m.put("cooldownFactor", u.cooldownFactor());
        m.put("cost", u.cost()); m.put("gain", u.gain()); m.put("costPerUnit", u.costPerUnit());
        m.put("owned", u.owned()); m.put("blocked", u.blocked());
        m.put("rateBefore", u.rateBefore()); m.put("rateAfter", u.rateAfter());
        m.put("grind", "GRIND".equals(u.item().category));
        Price quote = prices.get(u.item().id);
        m.put("priceSource", quote != null && !u.item().modification ? quote.source : "");
        return m;
    }
    private static Map<String, Object> step(ProgressionEngine.Step s, Map<String, Price> prices) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("upgrade", upgrade(s.upgrade(), prices));
        m.put("totalGain", s.totalGain()); m.put("totalCost", s.totalCost()); m.put("rate", s.rate());
        return m;
    }
}
