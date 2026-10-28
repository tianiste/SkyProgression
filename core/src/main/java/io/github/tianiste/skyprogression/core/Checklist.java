package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * Guide requirements (the website's guide format, /schema/guide.json) checked against one profile member. Each entry
 * is owned, partial (with the unmet parts), missing, unknown (the API data it needs is absent; never shown as missing)
 * or manual (not in the API). Generic: it reads IDs and levels, never guide wording.
 */
public final class Checklist {
    private Checklist() {}
    public enum State { @SerializedName("owned") OWNED, @SerializedName("partial") PARTIAL, @SerializedName("missing") MISSING,
        @SerializedName("unknown") UNKNOWN, @SerializedName("manual") MANUAL }
    /** One unmet or unchecked part: "modifier" (with its index in the requirement's modifiers), "tier", "level", "item" or "count". */
    public record Part(String part, Integer modifier, String text, String have) {}
    public static final class Result {
        public String id;
        public State state;
        /** Numeric progress (levels, shards, amounts) when the kind has one. */
        public Double have, need;
        public String unit;
        /** Short English summary, for clients without their own wording. */
        public String text;
        public List<Part> missing, unchecked;
        /** Where the counted copy is stored ("Accessory bag", "Backpack 3", "Pets menu (active)"). */
        public List<String> where;
        /** An owned item crafted from the required one (e.g. Divan's Drill for a Titanium Drill). */
        public String coveredBy;
        /** API setting whose data is missing, for unknown entries: inventory, collections, skills, pets, attributes, essence, hotm. */
        public String api;
        public List<Result> options;
        public Integer chosen;
    }
    /** Which SkyBlock API data the profile exposes (false = turned off in the API settings or not present). */
    public record Api(boolean inventory, boolean vault, boolean collections, boolean skills) {}
    public record Report(List<Result> results, Api api) {}

    private static final List<Item> CATALOG = Catalog.load();
    /** Upper bound on entries (options included) per request. */
    public static final int MAX_REQUIREMENTS = 400;
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE");
    private static final List<String> QUALITIES = List.of("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT");
    /** Rarity of attributes the stat ledger does not use (wiki shard pages); the ledger's own table wins. */
    private static final Map<String, String> ATTRIBUTE_RARITY = Map.ofEntries(
            Map.entry("mountain_climber", "UNCOMMON"), Map.entry("bigger_box", "UNCOMMON"), Map.entry("dwarven_training", "UNCOMMON"),
            Map.entry("crystal_serendipity", "RARE"), Map.entry("breeze", "RARE"), Map.entry("dwarven_serendipity", "RARE"),
            Map.entry("fungy_luck", "RARE"), Map.entry("scrap_chance", "RARE"), Map.entry("refined_luck", "RARE"),
            Map.entry("glossy_fortune", "EPIC"), Map.entry("why_not_more", "EPIC"), Map.entry("pity", "LEGENDARY"),
            Map.entry("fog_elemental", "COMMON"), Map.entry("water_elemental", "UNCOMMON"), Map.entry("torrent_elemental", "RARE"),
            Map.entry("frost_elemental", "EPIC"), Map.entry("snow_elemental", "LEGENDARY"), Map.entry("echo_of_elemental", "LEGENDARY"),
            Map.entry("maximal_torment", "LEGENDARY"), Map.entry("mana_regeneration", "EPIC"), Map.entry("echo_of_echoes", "LEGENDARY"));
    /** In-game HOTM perk maxima (same tree as ProfileUpgrades' layout); abilities count their ability level. */
    private static final Map<String, Integer> PERK_MAX = new HashMap<>();
    static {
        String[] rows = {"mining_speed 50", "mining_speed_boost 3", "precision_mining 1", "mining_fortune 50", "titanium_insanium 50", "pickobulus 3",
                "luck_of_the_cave 45", "efficient_miner 100", "quick_forge 20", "sky_mall 1", "old_school 20", "professional 140", "mole 200",
                "gem_lover 20", "seasoned_mineman 100", "front_loaded 1", "daily_grind 1", "core_of_the_mountain 10", "daily_powder 1",
                "anomalous_desire 3", "blockhead 20", "subterranean_fisher 40", "keep_it_cool 50", "lonesome_miner 45", "great_explorer 20",
                "maniac_miner 3", "speedy_mineman 50", "powder_buff 50", "fortunate_mineman 50", "miners_blessing 1", "no_stone_unturned 50",
                "strong_arm 100", "steady_hand 100", "warm_heart 50", "surveyor 20", "mineshaft_mayhem 1", "metal_head 20", "rags_to_riches 50",
                "eager_adventurer 100", "gemstone_infusion 3", "crystalline 50", "gifts_from_the_departed 100", "mining_master 10",
                "dead_mans_chest 50", "vanguard_seeker 50", "sheer_force 3"};
        for (String row : rows) { String[] f = row.split(" "); PERK_MAX.put(f[0], Integer.parseInt(f[1])); }
    }
    private static final Map<String, String> BAGS = Map.of("talisman_bag", "Accessory bag", "potion_bag", "Potion bag", "fishing_bag", "Fishing bag",
            "quiver", "Quiver", "sacks_bag", "Sack of Sacks");
    private static final Map<String, String> CONTAINERS = new LinkedHashMap<>();
    static {
        CONTAINERS.put("inv_armor", "Worn armor"); CONTAINERS.put("equipment_contents", "Worn equipment"); CONTAINERS.put("inv_contents", "Inventory");
        CONTAINERS.put("ender_chest_contents", "Ender chest"); CONTAINERS.put("wardrobe_contents", "Wardrobe"); CONTAINERS.put("personal_vault_contents", "Personal vault");
    }

    /** One stored stack: the item, how many, where, and its NBT (read into an Item only when a requirement needs modifiers). */
    private record Copy(String id, int count, String where, JsonElement nbt) {}
    private static final class Stock {
        final List<Copy> copies = new ArrayList<>();
        final Map<Copy, Item> items = new IdentityHashMap<>();
        boolean inventory, vault;
        List<Copy> of(String id) { return copies.stream().filter(c -> c.id.equals(id)).toList(); }
        Item item(Copy c) {
            return items.computeIfAbsent(c, copy -> {
                Item definition = CATALOG.stream().filter(i -> i.id.equals(copy.id)).findFirst().orElseGet(() -> { Item s = new Item(); s.id = copy.id; s.name = copy.id; return s; });
                return GearReader.read(copy.nbt, definition, copy.where, 0);
            });
        }
    }

    public static Report evaluate(JsonArray requirements, JsonObject member) {
        int entries = 0;
        for (JsonElement r : requirements) entries += 1 + (at(r, "options").isJsonArray() ? at(r, "options").getAsJsonArray().size() : 0);
        if (entries > MAX_REQUIREMENTS) throw new IllegalArgumentException("Too many requirements (" + entries + ", max " + MAX_REQUIREMENTS + ")");
        Stock stock = stock(member);
        Api api = new Api(stock.inventory, stock.vault, at(member, "collection").isJsonObject(),
                !at(member, "player_data", "experience", "SKILL_MINING").isJsonNull());
        List<Result> out = new ArrayList<>();
        for (JsonElement r : requirements) if (r.isJsonObject()) out.add(entry(r.getAsJsonObject(), member, stock));
        return new Report(out, api);
    }

    private static Result entry(JsonObject r, JsonObject member, Stock stock) {
        String kind = string(r.get("kind"), "");
        Result res = switch (kind) {
            case "item" -> item(r, stock, false);
            case "accessory" -> item(r, stock, true);
            case "consumable" -> consumable(r, member, stock);
            case "pet" -> pet(r, member);
            case "hotm" -> hotm(r, member);
            case "powder" -> powder(r, member);
            case "attribute" -> attribute(r, member);
            case "essence" -> essence(r, member);
            case "collection" -> collection(r, member);
            case "skill" -> skill(r, member);
            case "any_of" -> anyOf(r, member, stock);
            default -> { Result m = new Result(); m.state = State.MANUAL; m.text = "Not in the API; check it yourself."; yield m; }
        };
        res.id = string(r.get("id"), "");
        return res;
    }

    private static Result anyOf(JsonObject r, JsonObject member, Stock stock) {
        Result res = new Result();
        res.options = new ArrayList<>();
        JsonElement options = r.get("options");
        if (options != null && options.isJsonArray()) for (JsonElement o : options.getAsJsonArray())
            if (o.isJsonObject()) res.options.add(entry(o.getAsJsonObject(), member, stock));
        List<State> order = List.of(State.OWNED, State.PARTIAL, State.UNKNOWN, State.MISSING, State.MANUAL);
        int best = -1;
        for (int i = 0; i < res.options.size(); i++)
            if (best < 0 || order.indexOf(res.options.get(i).state) < order.indexOf(res.options.get(best).state)) best = i;
        if (best < 0) { res.state = State.MANUAL; return res; }
        Result b = res.options.get(best);
        // One manual option among missing ones may be what the player did: ask instead of saying missing.
        boolean manual = res.options.stream().anyMatch(o -> o.state == State.MANUAL);
        res.state = b.state == State.MISSING && manual ? State.MANUAL : b.state;
        res.chosen = best; res.where = b.where; res.api = b.api;
        res.text = b.state == State.OWNED || b.state == State.PARTIAL ? "Option " + (best + 1) + ": " + b.text : b.text;
        return res;
    }

    // Items, accessories and consumables.

    private static Result item(JsonObject r, Stock stock, boolean accessory) {
        Result res = new Result();
        String id = string(r.get("item"), "");
        int need = Math.max(1, (int)number(r.get("count"), 1));
        JsonArray mods = r.get("modifiers") != null && r.get("modifiers").isJsonArray() ? r.getAsJsonArray("modifiers") : new JsonArray();
        List<Copy> copies = stock.of(id);
        if (accessory) {
            // Only the accessory bag and the inventory count towards magical power and stats.
            List<Copy> active = copies.stream().filter(c -> c.where.equals("Accessory bag") || c.where.startsWith("Inventory")).toList();
            if (!active.isEmpty()) copies = active;
        }
        if (copies.isEmpty()) {
            Copy upgrade = stock.copies.stream().filter(c -> upgradedFrom(c.id, id, 0)).findFirst().orElse(null);
            if (upgrade != null) {
                res.state = State.OWNED; res.coveredBy = upgrade.id; res.where = List.of(upgrade.where);
                res.text = "Upgraded to " + name(upgrade.id) + ".";
                return res;
            }
            return absent(res, stock, name(id));
        }
        int have = copies.stream().mapToInt(Copy::count).sum();
        // The copy with the fewest unmet modifiers; ties keep the first found (worn before stored).
        List<Part> bestMissing = null, bestUnchecked = null;
        Copy best = null;
        for (Copy c : copies) {
            List<Part> missing = new ArrayList<>(), unchecked = new ArrayList<>();
            if (mods.size() > 0) modifiers(stock.item(c), mods, missing, unchecked);
            if (bestMissing == null || missing.size() < bestMissing.size()) { bestMissing = missing; bestUnchecked = unchecked; best = c; }
        }
        res.where = need > 1 ? copies.stream().map(Copy::where).distinct().toList() : List.of(best.where);
        res.missing = bestMissing.isEmpty() ? null : bestMissing;
        res.unchecked = bestUnchecked.isEmpty() ? null : bestUnchecked;
        if (need > 1) { res.have = (double)have; res.need = (double)need; res.unit = "items"; }
        if (have < need) (res.missing == null ? res.missing = new ArrayList<>() : res.missing).add(0, new Part("count", null, need + " needed", String.valueOf(have)));
        res.state = res.missing == null ? State.OWNED : State.PARTIAL;
        res.text = res.missing == null ? "Owned (" + best.where + ")." : "Missing: " + String.join(", ", res.missing.stream().map(Part::text).toList()) + ".";
        return res;
    }

    private static Result absent(Result res, Stock stock, String what) {
        if (!stock.inventory) { res.state = State.UNKNOWN; res.api = "inventory"; res.text = "Inventory API off: can't see items."; }
        else { res.state = State.MISSING; res.text = what + " not found."; }
        return res;
    }

    private static Result consumable(JsonObject r, JsonObject member, Stock stock) {
        Result res = new Result();
        String id = string(r.get("item"), "");
        int need = Math.max(1, (int)number(r.get("count"), 1));
        if (id.equals("REAPER_PEPPER")) {
            // Eaten peppers are permanent (player_data.reaper_peppers_eaten, omitted until the first pepper).
            JsonElement eaten = at(member, "player_data", "reaper_peppers_eaten");
            if (!eaten.isJsonNull()) {
                int n = (int)number(eaten, 0);
                res.have = (double)n; res.need = (double)need; res.unit = "eaten";
                res.state = n >= need ? State.OWNED : n > 0 ? State.PARTIAL : State.MISSING;
                res.text = n + " of " + need + " eaten.";
                if (n >= need) return res;
            }
        }
        List<Copy> copies = stock.of(id);
        if (copies.isEmpty()) return res.state == State.PARTIAL ? res : absent(res, stock, name(id));
        int have = copies.stream().mapToInt(Copy::count).sum();
        res.where = copies.stream().map(Copy::where).distinct().toList();
        res.have = (double)have; res.need = (double)need; res.unit = "items";
        res.state = have >= need ? State.OWNED : State.PARTIAL;
        res.text = have + " of " + need + " stored.";
        return res;
    }

    /** Recombobulated, reforge, gems, enchantments, drill parts and applied items on one copy. */
    private static void modifiers(Item item, JsonArray mods, List<Part> missing, List<Part> unchecked) {
        for (int i = 0; i < mods.size(); i++) {
            JsonElement m = mods.get(i);
            String kind = string(at(m, "kind"), "");
            switch (kind) {
                case "recomb" -> {
                    if (!(item.recombobulated || number(at(item.extra, "rarity_upgrades"), 0) > 0)) missing.add(new Part("modifier", i, "Recombobulated", null));
                }
                case "reforge" -> {
                    String id = string(at(m, "id"), "");
                    if (!id.equalsIgnoreCase(item.modifier)) missing.add(new Part("modifier", i, title(id) + " reforge", item.modifier.isEmpty() ? null : title(item.modifier)));
                }
                case "gem" -> {
                    String gem = string(at(m, "gem"), ""), quality = string(at(m, "quality"), "PERFECT");
                    int need = Math.max(1, (int)number(at(m, "count"), 1));
                    int[] counts = gems(item, gem, quality);
                    if (counts[0] < need) missing.add(new Part("modifier", i, need + " " + title(quality) + " " + title(gem),
                            counts[0] + " " + title(quality) + (counts[1] > counts[0] ? ", " + (counts[1] - counts[0]) + " lower" : "")));
                }
                case "enchant" -> {
                    String id = string(at(m, "id"), "");
                    int level = (int)number(at(m, "level"), 1), now = item.enchantments.getOrDefault(id, 0);
                    if (now < level) missing.add(new Part("modifier", i, title(id) + " " + level, now > 0 ? String.valueOf(now) : null));
                }
                case "drill_part" -> {
                    String slot = string(at(m, "slot"), ""), want = string(at(m, "item"), "");
                    String key = switch (slot) { case "engine" -> "drill_part_engine"; case "tank" -> "drill_part_fuel_tank"; default -> "drill_part_upgrade_module"; };
                    String has = string(at(item.extra, key), "").toUpperCase(Locale.ROOT);
                    if (!has.equals(want) && !upgradedFrom(has, want, 0))
                        missing.add(new Part("modifier", i, name(want), has.isEmpty() ? null : name(has)));
                }
                case "applied" -> {
                    String id = string(at(m, "item"), "");
                    int need = Math.max(1, (int)number(at(m, "count"), 1));
                    Integer have = applied(item, id);
                    if (have == null) unchecked.add(new Part("modifier", i, name(id), null));
                    else if (have < need) missing.add(new Part("modifier", i, (need > 1 ? need + " " : "") + name(id), have > 0 ? String.valueOf(have) : null));
                }
                default -> { /* Notes are text only. */ }
            }
        }
    }
    /** How many of an applied item the copy holds, or null when the engine does not know its key. */
    private static Integer applied(Item item, String id) {
        if (id.equals("POLARVOID_BOOK")) return (int)number(at(item.extra, "polarvoid"), 0);
        if (id.equals("DIVAN_POWDER_COATING")) return number(at(item.extra, "divan_powder_coating"), 0) > 0 ? 1 : 0;
        if (id.endsWith("_POWER_SCROLL")) return id.equals(string(at(item.extra, "power_ability_scroll"), "")) ? 1 : 0;
        if (id.equals("RECOMBOBULATOR_3000")) return number(at(item.extra, "rarity_upgrades"), 0) > 0 ? 1 : 0;
        if (id.equals("HOT_POTATO_BOOK")) return (int)Math.min(10, number(at(item.extra, "hot_potato_count"), 0));
        return null;
    }
    /** {slots holding the gem at or above the quality, slots holding it at any quality}. */
    private static int[] gems(Item item, String gem, String quality) {
        JsonElement gems = at(item.extra, "gems");
        int good = 0, any = 0, need = QUALITIES.indexOf(quality);
        if (!gems.isJsonObject()) return new int[]{0, 0};
        for (var e : gems.getAsJsonObject().entrySet()) {
            String key = e.getKey();
            if (key.endsWith("_gem") || key.equals("unlocked_slots") || !key.matches("[A-Z]+_[0-9]+")) continue;
            String type = key.substring(0, key.lastIndexOf('_'));
            if (!type.equals(gem)) type = string(at(gems, key + "_gem"), type);
            if (!type.equals(gem)) continue;
            String q = e.getValue().isJsonObject() ? string(at(e.getValue(), "quality"), "") : string(e.getValue(), "");
            if (QUALITIES.indexOf(q) < 0) continue;
            any++;
            if (QUALITIES.indexOf(q) >= need) good++;
        }
        return new int[]{good, any};
    }
    /**
     * Whether {@code owned} is made from {@code required} (its recipe, or a recipe further down, takes exactly one of it),
     * so owning the later item covers the earlier one: Divan's Drill covers the Titanium Drill DR-X655, an Amber-Polished
     * engine covers a Sapphire-Polished one.
     */
    static boolean upgradedFrom(String owned, String required, int depth) {
        if (owned.isEmpty() || depth > 8) return false;
        Recipes.Recipe recipe = Recipes.recipe(owned);
        if (recipe == null) return false;
        for (var in : recipe.inputs().entrySet()) {
            if (in.getValue() != 1) continue;
            if (in.getKey().equals(required) || upgradedFrom(in.getKey(), required, depth + 1)) return true;
        }
        return false;
    }

    private static Stock stock(JsonObject member) {
        Stock s = new Stock();
        JsonElement inventory = at(member, "inventory");
        s.inventory = !string(at(inventory, "inv_contents", "data"), "").isBlank();
        s.vault = !string(at(inventory, "personal_vault_contents", "data"), "").isBlank();
        if (s.inventory) {
            Set<String> uuids = new HashSet<>();
            for (var c : CONTAINERS.entrySet()) read(at(inventory, c.getKey()), c.getValue(), uuids, s);
            JsonElement bags = at(inventory, "bag_contents");
            if (bags.isJsonObject()) for (var e : bags.getAsJsonObject().entrySet()) read(e.getValue(), BAGS.getOrDefault(e.getKey(), title(e.getKey())), uuids, s);
            JsonElement backpacks = at(inventory, "backpack_contents");
            if (backpacks.isJsonObject()) for (var e : backpacks.getAsJsonObject().entrySet()) {
                int n = (int)number(new JsonPrimitive(e.getKey()), -1);
                read(e.getValue(), n >= 0 ? "Backpack " + (n + 1) : "Backpack", uuids, s);
            }
            loadoutPieces(member, (label, piece) -> read(piece, label.replaceFirst("^loadout (armor|equipment) set (\\S+).*", "Loadout $2 ($1)"), uuids, s));
            JsonElement sacks = at(inventory, "sacks_counts");
            if (!sacks.isJsonObject()) sacks = at(member, "sacks_counts");
            if (sacks.isJsonObject()) for (var e : sacks.getAsJsonObject().entrySet()) {
                int n = (int)number(e.getValue(), 0);
                if (n > 0) s.copies.add(new Copy(e.getKey(), n, "Sacks", JsonNull.INSTANCE));
            }
        }
        return s;
    }
    private static void read(JsonElement container, String label, Set<String> uuids, Stock s) {
        String data = string(at(container, "data"), "");
        if (data.isEmpty()) return;
        try {
            JsonElement items = new InventoryReader().read(data).get("i");
            if (items == null || !items.isJsonArray()) return;
            int slot = 0;
            for (JsonElement nbt : items.getAsJsonArray()) {
                int index = slot++;
                String id = string(at(nbt, "tag", "ExtraAttributes", "id"), "");
                if (id.isBlank()) continue;
                String uuid = string(at(nbt, "tag", "ExtraAttributes", "uuid"), "");
                if (!uuid.isBlank() && !uuids.add(uuid)) continue;
                // Ender chest pages hold 45 slots each.
                String where = label.equals("Ender chest") ? "Ender chest page " + (index / 45 + 1) : label;
                s.copies.add(new Copy(id, Math.max(1, (int)number(at(nbt, "Count"), 1)), where, nbt));
            }
        } catch (Exception e) {
            // An unreadable container leaves its items unknown; the rest still counts.
        }
    }

    // Profile progress.

    private static Result pet(JsonObject r, JsonObject member) {
        Result res = new Result();
        JsonElement pets = at(member, "pets_data", "pets");
        if (!pets.isJsonArray()) { res.state = State.UNKNOWN; res.api = "pets"; res.text = "No pet data in the API response."; return res; }
        String type = string(at(r, "pet", "type"), ""), minTier = string(at(r, "pet", "minTier"), "COMMON"), held = string(at(r, "pet", "item"), "");
        int level = (int)number(at(r, "pet", "level"), 100);
        List<Part> best = null;
        String where = null;
        for (JsonElement p : pets.getAsJsonArray()) {
            if (!type.equals(string(at(p, "type"), ""))) continue;
            String tier = string(at(p, "tier"), "COMMON"), item = string(at(p, "heldItem"), "");
            // A Tier Boost shows the boosted rarity in game; the API keeps the base tier and the boost as the held item.
            if (item.equals("PET_ITEM_TIER_BOOST") && RARITIES.indexOf(tier) >= 0 && RARITIES.indexOf(tier) < RARITIES.size() - 1) tier = RARITIES.get(RARITIES.indexOf(tier) + 1);
            int lvl = Loadout.petLevel(number(at(p, "exp"), 0), tier);
            List<Part> missing = new ArrayList<>();
            if (RARITIES.indexOf(tier) < RARITIES.indexOf(minTier)) missing.add(new Part("tier", null, title(minTier) + " or better", title(tier)));
            if (lvl < level) missing.add(new Part("level", null, "Level " + level, String.valueOf(lvl)));
            if (!held.isEmpty() && !held.equals(item)) missing.add(new Part("item", null, name(held), item.isEmpty() ? null : name(item)));
            if (best == null || missing.size() < best.size()) {
                best = missing;
                where = "Pets menu" + ("true".equals(string(at(p, "active"), "false")) ? " (active)" : "") + ", " + title(tier) + " level " + lvl;
            }
        }
        if (best == null) { res.state = State.MISSING; res.text = title(type) + " pet not owned."; return res; }
        res.where = List.of(where);
        res.missing = best.isEmpty() ? null : best;
        res.state = best.isEmpty() ? State.OWNED : State.PARTIAL;
        res.text = best.isEmpty() ? "Owned (" + where + ")." : "Missing: " + String.join(", ", best.stream().map(Part::text).toList()) + ".";
        return res;
    }

    private static Result hotm(JsonObject r, JsonObject member) {
        Result res = new Result();
        String perk = string(r.get("perk"), "");
        int max = PERK_MAX.getOrDefault(perk, 0);
        JsonElement l = r.get("level");
        int need = l != null && l.isJsonPrimitive() && l.getAsJsonPrimitive().isNumber() ? l.getAsInt() : max;
        if (need <= 0) { res.state = State.MANUAL; res.text = "Unknown perk " + perk + "."; return res; }
        // Loadouts can hold several trees: the best one counts.
        JsonElement nodes = at(member, "skill_tree", "nodes");
        List<JsonElement> trees = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (nodes.isJsonObject()) for (String tree : StatBreakdown.hotmTrees(member)) { trees.add(at(nodes, tree)); labels.add(StatBreakdown.treeLabel(tree)); }
        if (trees.isEmpty() && at(member, "mining_core", "nodes").isJsonObject()) { trees.add(at(member, "mining_core", "nodes")); labels.add("HOTM tree"); }
        if (trees.isEmpty()) { res.state = State.UNKNOWN; res.api = "hotm"; res.text = "No HOTM tree in the API response."; return res; }
        int have = 0; String where = labels.getFirst();
        for (int i = 0; i < trees.size(); i++) {
            int level = (int)number(at(trees.get(i), perk), 0);
            if (level > have) { have = level; where = labels.get(i); }
        }
        return levels(res, have, need, "levels", "Level " + have + " of " + need, List.of(where));
    }

    private static Result powder(JsonObject r, JsonObject member) {
        Result res = new Result();
        String type = string(r.get("powder"), "mithril");
        JsonElement unspent = at(member, "mining_core", "powder_" + type), spent = at(member, "mining_core", "powder_spent_" + type);
        if (unspent.isJsonNull() && spent.isJsonNull()) { res.state = State.UNKNOWN; res.api = "hotm"; res.text = "No powder data in the API response."; return res; }
        // Spent plus unspent: the powder a tree preset needs in total.
        double have = number(unspent, 0) + number(spent, 0), need = number(r.get("amount"), 0);
        return levels(res, have, need, "powder", String.format(Locale.ROOT, "%,.0f of %,.0f %s Powder earned", have, need, title(type)), null);
    }

    private static Result attribute(JsonObject r, JsonObject member) {
        Result res = new Result();
        String id = string(r.get("attribute"), "");
        int need = (int)number(r.get("level"), 10);
        JsonElement stacks = at(member, "attributes", "stacks");
        if (!stacks.isJsonObject()) { res.state = State.UNKNOWN; res.api = "attributes"; res.text = "No attribute data in the API response."; return res; }
        String rarity = StatBreakdown.ATTRIBUTES.stream().filter(a -> a.id().equals(id)).map(StatBreakdown.Attribute::rarity).findFirst()
                .orElseGet(() -> StatBreakdown.ECHOES.stream().filter(e -> e.id().equals(id)).map(StatBreakdown.Echo::rarity).findFirst()
                        .orElse(ATTRIBUTE_RARITY.get(id)));
        int shards = (int)number(at(stacks, id), 0);
        if (rarity == null) {
            res.state = shards > 0 ? State.PARTIAL : State.MISSING; res.have = (double)shards; res.unit = "shards";
            res.text = shards + " shards syphoned (level table for this attribute not known).";
            return res;
        }
        int level = StatBreakdown.attributeLevel(stacks, id, rarity), needShards = StatBreakdown.ATTRIBUTE_SHARDS.get(rarity)[Math.min(10, Math.max(1, need)) - 1];
        return levels(res, level, need, "levels", "Level " + level + " of " + need + " (" + shards + " of " + needShards + " shards)", null);
    }

    private static Result essence(JsonObject r, JsonObject member) {
        Result res = new Result();
        JsonElement perks = at(member, "player_data", "perks");
        if (!perks.isJsonObject()) { res.state = State.UNKNOWN; res.api = "essence"; res.text = "No essence perk data in the API response."; return res; }
        int have = (int)number(at(perks, string(r.get("perk"), "")), 0), need = (int)number(r.get("level"), 1);
        return levels(res, have, need, "levels", "Level " + have + " of " + need, null);
    }

    private static Result collection(JsonObject r, JsonObject member) {
        Result res = new Result();
        String id = string(r.get("collection"), "");
        int need = (int)number(r.get("level"), 1);
        JsonElement counts = at(member, "collection");
        if (!counts.isJsonObject()) { res.state = State.UNKNOWN; res.api = "collections"; res.text = "Collections API off."; return res; }
        double count = number(at(counts, id), 0);
        JsonElement tiers = at(Catalog.progressionRules(), "collections", id, "tiers");
        if (!tiers.isJsonArray()) { res.state = State.UNKNOWN; res.text = String.format(Locale.ROOT, "%,.0f collected; tier table not known.", count); return res; }
        int tier = 0; double needAmount = -1;
        for (JsonElement t : tiers.getAsJsonArray()) {
            int n = (int)number(at(t, "tier"), 0);
            double amount = number(at(t, "amountRequired"), Double.MAX_VALUE);
            if (count >= amount) tier = Math.max(tier, n);
            if (n == need) needAmount = amount;
        }
        String text = tier >= need ? "Tier " + tier + " (" + need + " needed)"
                : "Tier " + tier + " of " + need + (needAmount > 0 ? String.format(Locale.ROOT, " (%,.0f of %,.0f)", count, needAmount) : "");
        return levels(res, tier, need, "tiers", text, null);
    }

    private static Result skill(JsonObject r, JsonObject member) {
        Result res = new Result();
        int need = (int)number(r.get("level"), 1);
        if ("HOTM".equals(string(r.get("skill"), ""))) {
            double xp = number(at(member, "skill_tree", "experience", "mining"), number(at(member, "mining_core", "experience"), -1));
            if (xp < 0) { res.state = State.UNKNOWN; res.api = "hotm"; res.text = "No HOTM data in the API response."; return res; }
            int tier = 1;
            for (int i = 1; i < HOTM_XP.length; i++) if (xp >= HOTM_XP[i]) tier = i + 1;
            return levels(res, tier, need, "tiers", "HOTM " + tier + " of " + need, null);
        }
        Integer level = StatBreakdown.miningLevel(member);
        if (level == null) { res.state = State.UNKNOWN; res.api = "skills"; res.text = "Skills API off."; return res; }
        return levels(res, level, need, "levels", "Mining " + level + " of " + need, null);
    }

    private static Result levels(Result res, double have, double need, String unit, String text, List<String> where) {
        res.have = have; res.need = need; res.unit = unit; res.text = text + "."; res.where = where;
        res.state = have >= need ? State.OWNED : have > 0 ? State.PARTIAL : State.MISSING;
        return res;
    }

    private static String name(String id) {
        if (id.isEmpty()) return id;
        return CATALOG.stream().filter(i -> i.id.equals(id) && !i.name.isBlank()).map(i -> i.name).findFirst().orElseGet(() -> title(id));
    }
    private static String title(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.toLowerCase(Locale.ROOT).split("[_ ]+")) if (!w.isEmpty()) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }
}
