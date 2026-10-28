package io.github.tianiste.skyprogression.core;

import java.util.*;

/**
 * Pet alternatives for the upgrade ranking: each catalogued pet at level 100 (one option per rarity whose stats
 * differ), and the catalogued mining pet items for the current pet. Options carry the pet's full counted stats in the
 * evaluated context, so the engine compares them with the current pet's. Since 0.24.1 a pet item comes back when it is
 * replaced, and a Super Scrubber removes one without a replacement (wiki Pet Items, Super Scrubber), so a new pet keeps
 * the current mining pet item (moved with a Super Scrubber) and a pet-item swap returns the old item. Pet perks without
 * mining stats are ignored.
 */
public final class PetUpgrades {
    public static final String QUICK_CLAW = "PET_ITEM_QUICK_CLAW";
    public static final String SUPER_SCRUBBER = "SUPER_SCRUBBER";
    /** Super Scrubber price at Plumber Joe (wiki Super Scrubber). */
    public static final double SCRUBBER_COINS = 25_000;
    /** Pet items with counted mining stats and their rarity (wiki Pet Items: Quick Claw and Hephaestus Relic Legendary, Bejeweled Collar Rare). */
    static final Map<String, String> MINING_ITEMS = new LinkedHashMap<>();
    static {
        MINING_ITEMS.put(QUICK_CLAW, "LEGENDARY"); MINING_ITEMS.put(StatBreakdown.HEPHAESTUS, "LEGENDARY"); MINING_ITEMS.put(StatBreakdown.BEJEWELED_COLLAR, "RARE");
    }
    /** Coins to replace or remove a pet item, by the item's rarity (wiki Pet Items and Super Scrubber). */
    private static final Map<String, Double> FEES = Map.of("COMMON", 25_000.0, "UNCOMMON", 50_000.0, "RARE", 100_000.0, "EPIC", 250_000.0,
            "LEGENDARY", 500_000.0, "MYTHIC", 1_000_000.0, "DIVINE", 2_500_000.0);
    /**
     * priceId: "PET_TYPE;RARITY;100" (lowest level-100 BIN) or a pet item ID. heldItem: changes the current pet's item.
     * fees: coins paid on top of the price (pet item replacement or removal fee, Super Scrubber), null when unknown.
     * returned: pet item ID given back to the player ("" when none). carried: the current pet item moved to the new pet ("" when none).
     */
    public record Option(String priceId, String label, Map<Stat, Double> stats, boolean owned, boolean heldItem, Double fees, String returned, String carried) {
        public Option(String priceId, String label, Map<Stat, Double> stats, boolean owned, boolean heldItem) { this(priceId, label, stats, owned, heldItem, 0.0, "", ""); }
    }

    /** Rarity of a pet item: the catalogued mining items, else a rarity suffix (skill boosts); null when unknown. */
    static String rarity(String item) {
        if (MINING_ITEMS.containsKey(item)) return MINING_ITEMS.get(item);
        for (String tier : FEES.keySet()) if (item.endsWith("_" + tier)) return item.endsWith("_UNCOMMON") ? "UNCOMMON" : tier;
        return null;
    }
    /** Fee to replace or remove the item; the wiki does not say whose rarity sets a replacement fee, so the higher one is used. */
    static Double fee(String held, String applied) {
        if (held.isEmpty()) return 0.0;
        String a = rarity(held), b = applied == null ? null : rarity(applied);
        if (a == null || applied != null && b == null) return null;
        return Math.max(FEES.get(a), b == null ? 0 : FEES.get(b));
    }

    public static List<Option> options(Loadout.Owned owned, Loadout.Pet current, StatContext context, boolean drill) {
        List<Option> out = new ArrayList<>();
        if (!owned.petsKnown) return out;
        String held = current == null ? "" : current.heldItem();
        // A catalogued mining item moves to the new pet: Super Scrubber plus the removal fee by its rarity.
        String carried = MINING_ITEMS.containsKey(held) ? held : "";
        Double moveFees = carried.isEmpty() ? Double.valueOf(0) : SCRUBBER_COINS + fee(carried, null);
        StatBreakdown.petTiers().forEach((type, tiers) -> {
            for (String tier : tiers) {
                if (current != null && current.type().equals(type) && current.tier().equals(tier) && current.level() >= 100) continue;
                Loadout.Pet pet = new Loadout.Pet(type, tier, 0, 100, carried, false);
                Map<Stat, Double> stats = StatBreakdown.petStats(pet, context, drill);
                if (stats == null) continue;
                boolean have = owned.pets.stream().anyMatch(p -> p.type().equals(type) && p.tier().equals(tier) && p.level() >= 100);
                out.add(new Option("PET_" + type + ";" + tier + ";100", new Loadout.Pet(type, tier, 0, 100, "", false).label()
                        + (carried.isEmpty() ? "" : " (keeps " + StartWith.petItem(carried) + ")"), stats, have, false, moveFees, "", carried));
            }
        });
        if (current != null) for (String item : MINING_ITEMS.keySet()) {
            if (held.equals(item)) continue;
            Map<Stat, Double> stats = StatBreakdown.petStats(new Loadout.Pet(current.type(), current.tier(), current.exp(), current.level(), item, current.active()), context, drill);
            if (stats != null) out.add(new Option(item, StartWith.petItem(item) + " on " + current.label(), stats, false, true, fee(held, item), held, ""));
        }
        return out;
    }
    public static Set<String> priceIds() {
        Set<String> ids = new LinkedHashSet<>(MINING_ITEMS.keySet());
        StatBreakdown.petTiers().forEach((type, tiers) -> tiers.forEach(t -> ids.add("PET_" + type + ";" + t + ";100")));
        return ids;
    }
}
