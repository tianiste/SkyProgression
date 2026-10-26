package io.github.tianiste.skyprogression.core;

import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * What a replaced item could be sold for. The companion prices each evaluated copy with Coflnet's lowest BIN listing
 * that matches the copy's modifiers (rarity, recombobulation, reforge, powder coating, drill parts; pet rarity, level
 * and item), keeping only filters Coflnet offers for that item. Without a matching listing it falls back to Coflnet's
 * sell value for the plain item, labelled as such; without either there is no sale price. Nothing is estimated.
 */
public final class Resale {
    private Resale() {}
    /** tag: Coflnet item tag; filters: Coflnet BIN filter name -> wanted value. */
    public record Query(String tag, Map<String, String> filters) {}
    /** credit: coins (null when there is no sale price); note: where the value comes from. */
    public record Credit(Double credit, String note) {}

    public static Query of(Item item) {
        Map<String, String> f = new LinkedHashMap<>();
        if (!item.tier.isBlank()) f.put("Rarity", item.tier);
        f.put("Recombobulated", String.valueOf(item.recombobulated || number(at(item.extra, "rarity_upgrades"), 0) > 0));
        f.put("Reforge", item.modifier.isBlank() ? "None" : item.modifier);
        if ("DRILL".equals(item.category) || item.id.startsWith("DIVAN_")) f.put("PowderCoating", number(at(item.extra, "divan_powder_coating"), 0) > 0 ? "yes" : "no");
        if ("DRILL".equals(item.category)) {
            f.put("DrillPartEngine", part(item, "drill_part_engine"));
            f.put("DrillPartFuelTank", part(item, "drill_part_fuel_tank"));
            f.put("DrillPartUpgradeModule", part(item, "drill_part_upgrade_module"));
        }
        return new Query(item.id, f);
    }
    public static Query of(Loadout.Pet pet) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Rarity", pet.tier());
        f.put("PetLevel", String.valueOf(pet.level()));
        f.put("PetItem", pet.heldItem().isBlank() ? "None" : pet.heldItem());
        return new Query("PET_" + pet.type(), f);
    }
    private static String part(Item item, String field) {
        String key = string(at(item.extra, field), "");
        return key.isEmpty() ? "None" : key.toUpperCase(Locale.ROOT);
    }
    /**
     * Sale value of a replaced copy (key = instance UUID or "pet") or of a plain item returned to the player (parts,
     * removed gems): the modifier-aware quote first, else Coflnet's sell value for the item ID.
     */
    public static Credit credit(String key, String id, Map<String, Price> copies, Map<String, Price> prices) {
        Price copy = key == null ? null : copies.get(key);
        if (copy != null && copy.sell > 0) return new Credit(copy.sell, copy.source);
        Price plain = id == null ? null : prices.get(id);
        if (plain != null && plain.sell > 0 && Double.isFinite(plain.sell))
            return new Credit(plain.sell, (key != null && !key.equals(id) ? "your copy's modifiers not priced; " : "") + "Coflnet sell value for " + id);
        return new Credit(null, "No sale price for " + (id == null ? "the replaced pet" : id));
    }
}
