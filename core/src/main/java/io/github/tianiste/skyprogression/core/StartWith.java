package io.github.tianiste.skyprogression.core;

import com.google.gson.JsonObject;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * "Start with" (docs/CALCULATIONS.md "Start with: best owned loadout"): the best owned loadout for a method, chosen by the
 * same rules as the stat breakdown ({@link Loadout#bestFor}, {@link Loadout#bestTool}, {@link StatBreakdown#chooseTree}),
 * where each piece is stored, and the swaps from what is worn now. Each swap is evaluated alone on the current loadout,
 * so the client can rate it with MethodRates at its own efficiency and price mode.
 */
public final class StartWith {
    private StartWith() {}

    /** Wire format of the companion's {@code /start} endpoint. */
    public static final class Report {
        public String player = "", profile = "", profileId = "", fetchedAt = "", method = "", zone = "", target = "";
        public boolean godPotion = true, inventoryKnown, petsKnown;
        /** True when the player chose the held tool (Breakdown tab); the API does not expose the held item. */
        public boolean toolKnown;
        /** True when the held tool comes from the game client (live gear), not from the player's Tool choice. */
        public boolean toolLive;
        /** When the game client showed each piece (Piece key, "pet" for the pet; epoch ms); absent = from the API. */
        public Map<String, Long> liveAt = new HashMap<>();
        /** Best owned loadout: armor, equipment, tool, pet and the accessories that add the method's stats. */
        public List<Piece> best = new ArrayList<>();
        /** Accessories in the best loadout that add none of the method's stats (not listed). */
        public int otherAccessories;
        public String hotmTree = "mining", currentTree = "mining";
        public int hotmTrees = 1;
        public Scenario current, bestOwned;
        public List<Swap> swaps = new ArrayList<>();
        /** Every rated method with the gear worn now, in its usual zone (for the best-method hint). */
        public List<MethodComparison.Entry> worn = new ArrayList<>();
        public List<String> notes = new ArrayList<>();
    }
    /** One piece: slot label, key (instance, item ID or "pet"), item ID (pet type for pets), name, readable storage, pet item or similar, and whether it is worn. */
    public record Piece(String slot, String key, String id, String name, String location, String detail, boolean worn) {}
    /** Totals of every supported stat for one loadout; complete is false when a counted source of a method stat is unknown. */
    public static final class Scenario {
        public Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        public boolean complete = true;
        public MethodRates.Ability ability;
        public String tool = "", hotmTree = "mining";
    }
    /** Replace {@code from} (null = empty slot) with {@code to}; {@code after} is the current loadout with only this change. */
    public record Swap(String slot, Piece from, Piece to, Scenario after) {}

    private static final List<String> SLOT_ORDER = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET");

    public static Report of(JsonObject member, Loadout.Owned owned, MiningMethod m, String heldTool, StatContext context) {
        Report r = new Report();
        r.method = m.name(); r.godPotion = context.godPotion();
        r.zone = context.zone() == null ? "" : context.zone().name();
        r.target = context.target() == null ? "" : context.target().name();
        r.inventoryKnown = owned.inventoryKnown; r.petsKnown = owned.petsKnown;
        String bestTool = Loadout.bestTool(owned, m);
        r.toolKnown = heldTool != null && !heldTool.isBlank();
        r.toolLive = !r.toolKnown && !owned.heldTool.isBlank();
        if (r.toolLive) { heldTool = owned.heldTool; r.toolKnown = true; }
        Loadout equipped = Loadout.equipped(owned, r.toolKnown ? heldTool : bestTool), best = Loadout.bestFor(owned, m, bestTool);
        if (!r.toolKnown) r.notes.add("The API does not show the held tool, so the current loadout assumes the best owned tool. Pick your held tool with Tool on the Breakdown tab to see a tool swap.");
        r.currentTree = StatBreakdown.chooseTree(member, equipped, context, m);
        r.hotmTree = StatBreakdown.chooseTree(member, best, context, m);
        r.hotmTrees = StatBreakdown.hotmTrees(member).size();
        r.current = scenario(member, equipped, context, m);
        r.bestOwned = scenario(member, best, context, m);

        for (String slot : SLOT_ORDER) {
            Item item = slot(Loadout.ARMOR.contains(slot) ? best.armor : best.equipment, slot);
            if (item != null) r.best.add(piece(item, equipped));
        }
        if (best.tool != null) r.best.add(new Piece("Tool", key(best.tool), best.tool.id, best.tool.name, where(best.tool.location),
                r.toolLive ? (best.tool == equipped.tool ? "held now" : "") : r.toolKnown ? "" : "held tool not in the API", r.toolKnown && best.tool == equipped.tool));
        if (best.pet != null) r.best.add(piece(best.pet, Objects.equals(best.pet, equipped.pet)));
        for (Item item : best.accessories) {
            if (Loadout.methodValue(item, m) > 0) r.best.add(piece(item, equipped));
            else r.otherAccessories++;
        }

        // One change at a time on the current loadout and its HOTM tree; unchanged pieces and no-op swaps are not listed.
        for (String slot : SLOT_ORDER) {
            boolean armor = Loadout.ARMOR.contains(slot);
            swap(r, member, context, m, equipped, slot(armor ? equipped.armor : equipped.equipment, slot), slot(armor ? best.armor : best.equipment, slot),
                    l -> armor ? l.armor : l.equipment);
        }
        if (r.toolKnown && best.tool != null && best.tool != equipped.tool) {
            Loadout l = equipped.copy(); l.tool = best.tool;
            add(r, member, context, m, equipped.tool == null ? null
                    : new Piece("Tool", key(equipped.tool), equipped.tool.id, equipped.tool.name, where(equipped.tool.location), "held", true), piece(best.tool, equipped), l);
        }
        if (best.pet != null && !Objects.equals(best.pet, equipped.pet)) {
            Loadout l = equipped.copy(); l.pet = best.pet;
            add(r, member, context, m, equipped.pet == null ? null : piece(equipped.pet, true), piece(best.pet, false), l);
        }
        for (Item item : best.accessories) {
            if (equipped.accessories.contains(item) || Loadout.methodValue(item, m) <= 0) continue;
            String family = family(item);
            Item old = equipped.accessories.stream().filter(a -> family(a).equals(family)).findFirst().orElse(null);
            Loadout l = equipped.copy(); if (old != null) l.accessories.remove(old); l.accessories.add(item);
            add(r, member, context, m, old == null ? null : piece(old, equipped), piece(item, equipped), l);
        }
        if (!r.hotmTree.equals(r.currentTree)) {
            Loadout l = equipped.copy(); l.hotmTree = r.hotmTree;
            add(r, member, context, m, new Piece("HOTM tree", r.currentTree, "", StatBreakdown.treeLabel(r.currentTree), "Loadouts", "", true),
                    new Piece("HOTM tree", r.hotmTree, "", StatBreakdown.treeLabel(r.hotmTree), "Loadouts", "", false), l);
        }

        for (MiningMethod other : MiningMethod.values()) {
            if (!MethodRates.supported(other)) continue;
            StatContext usual = other.usualContext();
            StatContext c = new StatContext(usual.zone(), usual.target(), context.godPotion());
            Loadout l = Loadout.equipped(owned, r.toolKnown ? heldTool : Loadout.bestTool(owned, other));
            l.hotmTree = StatBreakdown.chooseTree(member, l, c, other);
            Scenario s = scenario(member, l, c, other);
            MethodComparison.Entry e = new MethodComparison.Entry();
            e.method = other; e.stats = s.stats; e.complete = s.complete; e.ability = s.ability; e.tool = s.tool; e.hotmTree = s.hotmTree;
            e.zone = c.zone() == null ? "" : c.zone().label;
            r.worn.add(e);
        }
        for (Item item : equipped.gear()) if (item.liveAt > 0) r.liveAt.put(key(item), item.liveAt);
        for (Item item : best.gear()) if (item.liveAt > 0) r.liveAt.put(key(item), item.liveAt);
        if (owned.petLiveAt > 0) r.liveAt.put("pet", owned.petLiveAt);
        Set<String> notes = new LinkedHashSet<>(equipped.notes); notes.addAll(best.notes);
        r.notes.addAll(notes);
        return r;
    }

    static Scenario scenario(JsonObject member, Loadout l, StatContext context, MiningMethod m) {
        Scenario s = new Scenario();
        List<Stat> relevant = m.relevantStats();
        for (Stat stat : StatBreakdown.RATE_STATS) {
            Breakdown b = StatBreakdown.compute(member, l, context, stat);
            s.stats.put(stat, b.total());
            if (relevant.contains(stat) && !b.complete()) s.complete = false;
        }
        s.ability = StatBreakdown.effectiveAbility(member, l, context);
        s.tool = l.tool == null ? "" : l.tool.name;
        s.hotmTree = l.hotmTree;
        return s;
    }
    private static void swap(Report r, JsonObject member, StatContext context, MiningMethod m, Loadout equipped, Item from, Item to,
                             java.util.function.Function<Loadout, List<Item>> list) {
        if (to == null || to == from) return;
        Loadout l = equipped.copy();
        if (from != null) list.apply(l).remove(from);
        list.apply(l).add(to);
        add(r, member, context, m, from == null ? null : piece(from, equipped), piece(to, equipped), l);
    }
    private static void add(Report r, JsonObject member, StatContext context, MiningMethod m, Piece from, Piece to, Loadout after) {
        Scenario s = scenario(member, after, context, m);
        boolean changed = !Objects.equals(s.ability, r.current.ability)
                || m.relevantStats().stream().anyMatch(stat -> Math.abs(stat(s.stats, stat) - stat(r.current.stats, stat)) > 1e-9);
        if (changed) r.swaps.add(new Swap(to.slot(), from, to, s));
    }
    private static Item slot(List<Item> items, String category) { return items.stream().filter(i -> i.category.equals(category)).findFirst().orElse(null); }
    private static String family(Item item) { return item.group.isBlank() ? item.id : item.group; }
    private static String key(Item item) { return item.instance.isBlank() ? item.id : item.instance; }
    private static Piece piece(Item item, Loadout equipped) {
        boolean worn = equipped.armor.contains(item) || equipped.equipment.contains(item) || equipped.accessories.contains(item);
        return new Piece(slotLabel(item.category), key(item), item.id, item.name, where(item.location), "", worn);
    }
    private static Piece piece(Loadout.Pet pet, boolean active) {
        return new Piece("Pet", "pet", pet.type(), pet.label(), pet.active() ? "active pet" : "pets menu",
                pet.heldItem().isBlank() ? "no pet item" : "holding " + petItem(pet.heldItem()), active);
    }
    private static String slotLabel(String category) {
        return category.isEmpty() ? "Item" : category.charAt(0) + category.substring(1).toLowerCase(Locale.ROOT);
    }
    /** "PET_ITEM_QUICK_CLAW" -> "Quick Claw". */
    static String petItem(String id) {
        StringJoiner out = new StringJoiner(" ");
        for (String word : id.replaceFirst("^PET_ITEM_", "").split("_"))
            if (!word.isEmpty()) out.add(word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT));
        return out.toString();
    }
    /**
     * Readable storage for an item location ("container #slot" or a Loadouts label). Wardrobe pages hold 36 items
     * (4 rows of 9 slots), ender chest pages 45, and backpack keys count from 0.
     */
    public static String where(String location) {
        if (location == null || location.isBlank()) return "unknown storage";
        if (location.startsWith("loadout ")) {
            String[] p = location.split(" ");
            return p.length >= 4 ? "Loadouts " + p[1] + " set " + p[3] : "Loadouts";
        }
        int hash = location.lastIndexOf(" #");
        String container = hash < 0 ? location : location.substring(0, hash);
        int slot;
        try { slot = hash < 0 ? -1 : Integer.parseInt(location.substring(hash + 2)); } catch (NumberFormatException e) { slot = -1; }
        if (container.startsWith("backpack ")) {
            try { return "backpack slot " + (Integer.parseInt(container.substring(9)) + 1); } catch (NumberFormatException e) { return "backpack"; }
        }
        return switch (container) {
            case "inv_armor" -> "worn";
            case "equipment_contents" -> "worn (equipment)";
            case "inv_contents" -> slot >= 0 && slot < 9 ? "hotbar" : "inventory";
            case "wardrobe_contents" -> slot < 0 ? "wardrobe" : "wardrobe slot " + (slot / 36 * 9 + slot % 9 + 1);
            case "ender_chest_contents" -> slot < 0 ? "ender chest" : "ender chest page " + (slot / 45 + 1);
            case "personal_vault_contents" -> "personal vault";
            case "talisman_bag" -> "accessory bag";
            case LiveGear.MOVED -> "moved since the API read (storage unknown)";
            case "potion_bag", "quiver", "sacks_bag", "fishing_bag" -> container.replace('_', ' ');
            default -> location;
        };
    }
}
