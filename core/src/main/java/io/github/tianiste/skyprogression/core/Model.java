package io.github.tianiste.skyprogression.core;

import java.util.*;

public final class Model {
    private Model() {}
    public static class Item {
        public String id = "", name = "", group = "", source = "Auction House / Bazaar";
        public Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        public int hotm;
        public String tier = "", category = "", instance = "", location = "", modifier = "";
        public boolean observed, modification, recombobulated;
        public Map<String, Integer> enchantments = new HashMap<>();
        public com.google.gson.JsonObject extra = new com.google.gson.JsonObject();
        public List<Slot> slots = new ArrayList<>();
        public List<MiningMethod> methods = new ArrayList<>();
        /** Cleaned lore lines of an observed copy; kept for the stat ledger, not serialized. */
        public transient List<String> lore = new ArrayList<>();
        public boolean applies(MiningMethod m) { return methods.isEmpty() || methods.contains(m); }
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
        public double buy;
        public String source = "", updatedAt = "";
        public boolean available;
        public Price() {}
        public Price(double buy, String source) { this.buy = buy; this.source = source; available = true; }
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
    }
    public static class Report {
        public List<Profile> profiles = new ArrayList<>();
        public Map<String, Price> prices = new HashMap<>();
        public String fetchedAt = "", error = "";
        public List<String> warnings = new ArrayList<>();
    }
    public record Upgrade(Item item, Item current, Map<Stat, Double> delta, Double cost,
                          double gain, boolean owned, String blocked) {
        public double costPerUnit() { return cost == null || gain <= 0 ? Double.POSITIVE_INFINITY : cost / gain; }
    }
    public record Overview(String group, Item current, Item target, double value, double maximum) {}
    public record Milestone(String name, double current, double maximum, String unit) {}
    public static double stat(Map<Stat, Double> stats, Stat stat) { return stats.getOrDefault(stat, 0.0); }
}
