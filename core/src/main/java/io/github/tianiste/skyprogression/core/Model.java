package io.github.tianiste.skyprogression.core;

import java.util.*;

public final class Model {
    private Model() {}
    public static class Item {
        public String id = "", name = "", group = "", source = "Auction House / Bazaar";
        public Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        public int hotm;
        public String tier = "", category = "", instance = "", location = "", modifier = "";
        /** Upgrade entries: the item the entry is about (UpgradeIcons); "" = the item's own icon. */
        public String icon = "";
        public boolean observed, modification, recombobulated;
        public Map<String, Integer> enchantments = new HashMap<>();
        public com.google.gson.JsonObject extra = new com.google.gson.JsonObject();
        public List<Slot> slots = new ArrayList<>();
        public List<MiningMethod> methods = new ArrayList<>();
        /** Cleaned lore lines of an observed copy; kept for the stat ledger, not serialized. */
        public transient List<String> lore = new ArrayList<>();
        /** When the game client showed this copy (epoch ms); 0 = read from the Hypixel API. Not serialized. */
        public transient long liveAt;
        public boolean applies(MiningMethod m) { return methods.isEmpty() || methods.contains(m); }
        /** Copy whose stats, enchantments and extra attributes can change without touching this item. */
        public Item copy() {
            Item c = new Item();
            c.id = id; c.name = name; c.group = group; c.source = source; c.hotm = hotm; c.tier = tier; c.category = category;
            c.instance = instance; c.location = location; c.modifier = modifier; c.icon = icon; c.observed = observed; c.modification = modification;
            c.recombobulated = recombobulated; c.stats.putAll(stats); c.enchantments.putAll(enchantments); c.extra = extra.deepCopy();
            c.slots = slots; c.methods = methods; c.lore = lore; c.liveAt = liveAt;
            return c;
        }
    }
    public static class Slot {
        public String slot_type = "";
        public List<Ingredient> costs = new ArrayList<>();
    }
    public static class Ingredient {
        public String type = "", item_id = "";
        public int amount;
        public double coins;
    }
    public static class Price {
        /** buy: what buying costs now; sell: Coflnet's sale estimate (instant sell on the bazaar, after tax on the AH); 0 = none. */
        public double buy, sell;
        public String source = "", updatedAt = "";
        /** NPC sell price from the official items resource (npc_sell_price) and when it was fetched; 0 = none listed. */
        public double npc;
        public String npcUpdatedAt = "";
        public boolean available;
        public Price() {}
        public Price(double buy, String source) { this.buy = buy; this.source = source; available = true; }
    }
    /**
     * How purchases are priced. INSTANT_BUY: the buy quote (Bazaar lowest sell offer, Auction House lowest BIN). BUY_ORDER:
     * Bazaar items at the highest buy order (the quote's sell field, Hypixel Bazaar sell_summary top), which fills only when
     * a seller accepts your order; Auction House items keep the lowest BIN.
     */
    public enum CostMode {
        INSTANT_BUY("instant-buy", "Buying now: the lowest Bazaar sell offer or the lowest Auction House BIN"),
        BUY_ORDER("buy order", "Bazaar items at the highest buy order: fills only when a seller accepts your order. Auction House items stay at the lowest BIN");
        public final String label, meaning;
        CostMode(String label, String meaning) { this.label = label; this.meaning = meaning; }
        /** Quotes to cost purchases with: unchanged for instant-buy; Bazaar buy quotes replaced by the top buy order (sale values untouched). */
        public Map<String, Price> apply(Map<String, Price> prices) {
            if (this == INSTANT_BUY) return prices;
            Map<String, Price> out = new HashMap<>(prices);
            prices.forEach((id, p) -> {
                if (p == null || !p.source.startsWith("Bazaar") || !(p.sell > 0) || !Double.isFinite(p.sell) || p.buy > 0 && p.sell >= p.buy) return;
                Price q = new Price(p.sell, p.source + ", buy order");
                q.sell = p.sell; q.updatedAt = p.updatedAt; q.npc = p.npc; q.npcUpdatedAt = p.npcUpdatedAt;
                out.put(id, q);
            });
            return out;
        }
    }
    public static class Profile {
        public String id = "", name = "", player = "", mode = "";
        public boolean selected, inventoryEnabled;
        public int hotm;
        public Map<String, Integer> owned = new HashMap<>();
        public List<Item> gear = new ArrayList<>();
        public transient Set<String> seenUuids = new HashSet<>();
        public int enchantingLevel = -1;
        public List<String> notes = new ArrayList<>();
        public Map<String, Double> progress = new LinkedHashMap<>();
        public List<Milestone> milestones = new ArrayList<>();
        /** Collection counts by item key; null when the Collections API is off. */
        public Map<String, Double> collections = new HashMap<>();
    }
    public static class Report {
        public List<Profile> profiles = new ArrayList<>();
        public Map<String, Price> prices = new HashMap<>();
        public String fetchedAt = "", error = "";
        public List<String> warnings = new ArrayList<>();
    }
    /**
     * One alternative against the current setup. cooldownFactor scales the pickaxe ability cooldown (1 = unchanged).
     * rateBefore/rateAfter are the method rate (Flawless / hour) when the method has a rate model, else null.
     */
    public record Upgrade(Item item, Item current, Map<Stat, Double> delta, Double cost,
                          double gain, boolean owned, String blocked, double cooldownFactor, Double rateBefore, Double rateAfter,
                          Double resale, String resaleNote) {
        public Upgrade(Item item, Item current, Map<Stat, Double> delta, Double cost, double gain, boolean owned, String blocked) {
            this(item, current, delta, cost, gain, owned, blocked, 1, null, null);
        }
        public Upgrade(Item item, Item current, Map<Stat, Double> delta, Double cost, double gain, boolean owned, String blocked,
                       double cooldownFactor, Double rateBefore, Double rateAfter) {
            this(item, current, delta, cost, gain, owned, blocked, cooldownFactor, rateBefore, rateAfter, null, "");
        }
        /** Same upgrade with the sale value of what it replaces (null credit = no sale price; empty note = nothing replaced). */
        public Upgrade withResale(Resale.Credit c) {
            return c == null ? this : new Upgrade(item, current, delta, cost, gain, owned, blocked, cooldownFactor, rateBefore, rateAfter, c.credit(), c.note());
        }
        /** Cash outlay minus the replaced item's sale value; null when either is unknown or nothing is replaced. */
        public Double net() { return cost == null || resale == null || resaleNote.isEmpty() ? null : cost - resale; }
        public double costPerUnit() { return cost == null || gain <= 0 ? Double.POSITIVE_INFINITY : cost / gain; }
    }
    public record Overview(String group, Item current, Item target, double value, double maximum) {}
    public record Milestone(String name, double current, double maximum, String unit) {}
    public static double stat(Map<Stat, Double> stats, Stat stat) { return stats.getOrDefault(stat, 0.0); }
}
