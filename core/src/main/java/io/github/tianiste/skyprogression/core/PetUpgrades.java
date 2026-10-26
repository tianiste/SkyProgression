package io.github.tianiste.skyprogression.core;

import java.util.*;

/**
 * Pet alternatives for the upgrade ranking: each catalogued pet at level 100 (one option per rarity whose stats
 * differ), and a Quick Claw for the current pet. Options carry the pet's full counted stats in the evaluated
 * context, so the engine compares them with the current pet's. A swapped-in pet is assumed to have no pet item
 * (the current item is not credited), and pet perks without mining stats are ignored.
 */
public final class PetUpgrades {
    public static final String QUICK_CLAW = "PET_ITEM_QUICK_CLAW";
    /** priceId: "PET_TYPE;RARITY;100" (lowest level-100 BIN) or a pet item ID. heldItem: changes the current pet's item. */
    public record Option(String priceId, String label, Map<Stat, Double> stats, boolean owned, boolean heldItem) {}

    public static List<Option> options(Loadout.Owned owned, Loadout.Pet current, StatContext context, boolean drill) {
        List<Option> out = new ArrayList<>();
        if (!owned.petsKnown) return out;
        StatBreakdown.petTiers().forEach((type, tiers) -> {
            for (String tier : tiers) {
                if (current != null && current.type().equals(type) && current.tier().equals(tier) && current.level() >= 100) continue;
                Loadout.Pet pet = new Loadout.Pet(type, tier, 0, 100, "", false);
                Map<Stat, Double> stats = StatBreakdown.petStats(pet, context, drill);
                if (stats == null) continue;
                boolean have = owned.pets.stream().anyMatch(p -> p.type().equals(type) && p.tier().equals(tier) && p.level() >= 100);
                out.add(new Option("PET_" + type + ";" + tier + ";100", pet.label(), stats, have, false));
            }
        });
        if (current != null && !current.heldItem().equals(QUICK_CLAW)) {
            Map<Stat, Double> stats = StatBreakdown.petStats(new Loadout.Pet(current.type(), current.tier(), current.exp(), current.level(), QUICK_CLAW, current.active()), context, drill);
            if (stats != null) out.add(new Option(QUICK_CLAW, "Quick Claw on " + current.label(), stats, false, true));
        }
        return out;
    }
    public static Set<String> priceIds() {
        Set<String> ids = new LinkedHashSet<>(List.of(QUICK_CLAW));
        StatBreakdown.petTiers().forEach((type, tiers) -> tiers.forEach(t -> ids.add("PET_" + type + ";" + t + ";100")));
        return ids;
    }
}
