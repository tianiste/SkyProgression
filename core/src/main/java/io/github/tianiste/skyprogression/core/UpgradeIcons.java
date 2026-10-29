package io.github.tianiste.skyprogression.core;

import java.util.*;

/**
 * The item an upgrade entry is about, for its icon: a SkyBlock item ID (looked up in the mod's icon table), a pet as
 * "PET_" + type, or a fixed vanilla icon as "minecraft:" + item for entries with no item (HOTM perks, skills).
 * "" means none; the interface then uses the gear piece's own icon.
 */
public final class UpgradeIcons {
    private UpgradeIcons() {}
    /** HOTM perks by the powder they cost: Mithril (prismarine crystals), Gemstone (pink), Glacite (packed ice). */
    private static final Map<String, String> POWDER = Map.of("POWDER_MITHRIL", "minecraft:prismarine_crystals",
            "POWDER_GEMSTONE", "minecraft:pink_dye", "POWDER_GLACITE", "minecraft:packed_ice");

    /** A plan material as an icon: enchantment books ("ENCHANTMENT_FORTUNE_4") are an enchanted book. */
    public static String material(String id) {
        if (id == null) return "";
        return id.startsWith("ENCHANTMENT_") ? "ENCHANTED_BOOK" : id;
    }
    /** A gear upgrade shows the item it uses: the drill part, reforge stone, gem, book, Recombobulator or coating. */
    public static String proposal(GearUpgrades.Proposal p) {
        if (!p.icon().isEmpty()) return p.icon();
        var materials = p.plan().materials();
        return materials.isEmpty() ? "" : material(materials.keySet().iterator().next());
    }
    /** Essence perks show the essence, attributes the shard, HOTM perks the powder, collections the collection item. */
    public static String option(ProfileUpgrades.Option o) {
        String r = o.resource() == null ? "" : o.resource();
        return switch (o.group()) {
            case "Essence", "Attribute" -> r;
            case "HOTM" -> POWDER.getOrDefault(r, "minecraft:emerald");
            case "Skill" -> "minecraft:stone_pickaxe";
            case "Collection" -> r.startsWith("COLLECTION_") ? r.substring(11) : "";
            default -> "";
        };
    }
    /** Pet items show the item; pets show the pet ("PET_BAL" from "PET_BAL;LEGENDARY;100"). */
    public static String pet(PetUpgrades.Option o) {
        if (o.heldItem()) return o.priceId();
        int end = o.priceId().indexOf(';');
        return end < 0 ? o.priceId() : o.priceId().substring(0, end);
    }
    /** Other & progress entries: forge items, bonus items (cereal, shard), Compact, the evaluated pet, profile options. */
    public static String other(OtherUpgrades.Entry e, StatsReport report) {
        String id = e.id();
        if (id.startsWith("forge:")) return id.substring(6);
        if (id.equals("compact")) return "ENCHANTED_BOOK";
        if (id.equals("pet-level")) return report == null || report.petLevel == null ? "" : "PET_" + report.petLevel.type();
        if (id.startsWith("profile:")) {
            if (report == null || report.profileUpgrades == null) return "";
            for (ProfileUpgrades.Option o : report.profileUpgrades.options())
                if (id.equals("profile:" + o.chain() + ":" + o.rank())) return option(o);
            return "";
        }
        return id;
    }
}
