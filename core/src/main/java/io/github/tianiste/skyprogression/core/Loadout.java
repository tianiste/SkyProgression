package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * One coherent set of gear, accessories and pet to evaluate. The equipped loadout is what the API
 * reports as worn (inv_armor, equipment_contents, the accessory bag and the active pet) plus a tool
 * the player selects, because the held item is not exposed. A best-owned loadout is a separate,
 * hypothetical choice per slot; stored items are never mixed into the equipped one.
 */
public final class Loadout {
    public enum Kind { EQUIPPED, BEST_OWNED }
    public record Pet(String type, String tier, double exp, int level, String heldItem, boolean active) {
        public String label() {
            String name = type.isEmpty() ? "Unknown" : type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
            return name + " pet (" + tier.charAt(0) + tier.substring(1).toLowerCase(Locale.ROOT) + ", level " + level + ")";
        }
    }
    /** Everything the member owns that the API exposes, with each copy's container. */
    public static final class Owned {
        public final List<Item> items = new ArrayList<>();
        public final List<Pet> pets = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public boolean inventoryKnown, petsKnown;
        public List<Item> in(String container) { return items.stream().filter(i -> i.location.startsWith(container + " #")).toList(); }
        public List<Item> tools() { return items.stream().filter(i -> TOOLS.contains(i.category)).toList(); }
    }

    static final Set<String> ARMOR = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
    static final Set<String> EQUIPMENT = Set.of("NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET");
    static final Set<String> TOOLS = Set.of("DRILL", "PICKAXE", "GAUNTLET");
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL");
    private static final Pattern RARITY_LINE = Pattern.compile("(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|VERY SPECIAL|SPECIAL)(?: DUNGEON)? ([A-Z][A-Z ]*?)(?: a)?$");
    private static final List<String> CONTAINERS = List.of("inv_armor", "equipment_contents", "inv_contents",
            "ender_chest_contents", "wardrobe_contents", "personal_vault_contents");

    public final Kind kind;
    public final List<Item> armor = new ArrayList<>(), equipment = new ArrayList<>(), accessories = new ArrayList<>();
    public Item tool;
    public Pet pet;
    /** HOTM tree read for perks: `skill_tree.nodes.<key>`, "mining" or "mining_2".."mining_5" (Loadouts trees). */
    public String hotmTree = "mining";
    public boolean inventoryKnown, petsKnown;
    public final List<String> notes = new ArrayList<>();
    private Loadout(Kind kind, Owned owned) {
        this.kind = kind; inventoryKnown = owned.inventoryKnown; petsKnown = owned.petsKnown; notes.addAll(owned.notes);
    }
    /** Same pieces, pet and HOTM tree, so one piece can be swapped without touching this loadout. */
    public Loadout copy() {
        Loadout l = new Loadout(kind, new Owned());
        l.armor.addAll(armor); l.equipment.addAll(equipment); l.accessories.addAll(accessories);
        l.tool = tool; l.pet = pet; l.hotmTree = hotmTree; l.inventoryKnown = inventoryKnown; l.petsKnown = petsKnown; l.notes.addAll(notes);
        return l;
    }
    public List<Item> gear() {
        List<Item> all = new ArrayList<>(armor); all.addAll(equipment);
        if (tool != null) all.add(tool);
        all.addAll(accessories);
        return all;
    }

    public static Owned read(JsonObject member) {
        Owned owned = new Owned();
        List<Item> catalog = Catalog.load();
        JsonElement inventory = at(member, "inventory");
        owned.inventoryKnown = !string(at(inventory, "inv_contents", "data"), "").isBlank();
        if (!owned.inventoryKnown) owned.notes.add("Inventory API unavailable: gear, equipment and accessories are unknown.");
        else {
            Set<String> uuids = new HashSet<>();
            for (String field : CONTAINERS) readContainer(at(inventory, field), field, catalog, uuids, owned);
            for (String group : List.of("bag_contents", "backpack_contents")) {
                JsonElement bags = at(inventory, group);
                if (bags.isJsonObject()) bags.getAsJsonObject().entrySet()
                        .forEach(e -> readContainer(e.getValue(), group.equals("bag_contents") ? e.getKey() : "backpack " + e.getKey(), catalog, uuids, owned));
            }
            loadoutPieces(member, (label, piece) -> readContainer(piece, label, catalog, uuids, owned));
        }
        JsonElement pets = at(member, "pets_data", "pets");
        owned.petsKnown = pets.isJsonArray();
        if (!owned.petsKnown) owned.notes.add("Pet data unavailable.");
        else for (JsonElement p : pets.getAsJsonArray()) {
            String type = string(at(p, "type"), ""), tier = string(at(p, "tier"), "COMMON");
            double exp = number(at(p, "exp"), 0);
            owned.pets.add(new Pet(type, tier, exp, petLevel(exp, tier), string(at(p, "heldItem"), ""),
                    "true".equals(string(at(p, "active"), "false"))));
        }
        return owned;
    }
    private static void readContainer(JsonElement container, String label, List<Item> catalog, Set<String> uuids, Owned owned) {
        String data = string(at(container, "data"), "");
        if (data.isEmpty()) return;
        try {
            JsonElement items = new InventoryReader().read(data).get("i");
            if (items == null || !items.isJsonArray()) throw new java.io.IOException("Missing inventory item list");
            int slot = 0;
            for (JsonElement nbt : items.getAsJsonArray()) {
                int index = slot++;
                JsonElement attributes = at(nbt, "tag", "ExtraAttributes");
                String id = string(at(attributes, "id"), "");
                if (id.isBlank()) continue;
                String uuid = string(at(attributes, "uuid"), "");
                if (!uuid.isBlank() && !uuids.add(uuid)) continue;
                Item definition = catalog.stream().filter(i -> i.id.equals(id)).findFirst().orElseGet(() -> {
                    Item stub = new Item(); stub.id = id; stub.name = id; stub.source = ""; return stub;
                });
                Item gear = GearReader.read(nbt, definition, label, index);
                if (gear.category.isBlank()) gear.category = loreCategory(gear.lore);
                if (gear.tier.isBlank()) gear.tier = loreRarity(gear.lore);
                if (!gear.category.isBlank()) owned.items.add(gear);
            }
        } catch (Exception e) {
            owned.inventoryKnown = false;
            owned.notes.add("Could not read " + label + "; gear from it is unknown.");
        }
    }
    static String loreCategory(List<String> lore) {
        for (int i = lore.size() - 1; i >= 0; i--) {
            Matcher m = RARITY_LINE.matcher(lore.get(i).replaceFirst("^a ", ""));
            if (m.find()) return m.group(2).strip();
            if (!lore.get(i).isBlank()) break;
        }
        return "";
    }
    static String loreRarity(List<String> lore) {
        for (int i = lore.size() - 1; i >= 0; i--) {
            Matcher m = RARITY_LINE.matcher(lore.get(i).replaceFirst("^a ", ""));
            if (m.find()) return m.group(1);
            if (!lore.get(i).isBlank()) break;
        }
        return "";
    }

    /** What the player wears now; {@code tool} picks a copy by instance or item ID (null when not chosen). */
    public static Loadout equipped(Owned owned, String tool) {
        Loadout l = new Loadout(Kind.EQUIPPED, owned);
        owned.in("inv_armor").stream().filter(i -> ARMOR.contains(i.category)).forEach(l.armor::add);
        owned.in("equipment_contents").stream().filter(i -> EQUIPMENT.contains(i.category)).forEach(l.equipment::add);
        l.accessories.addAll(uniqueFamilies(owned.in("talisman_bag").stream().filter(i -> i.category.equals("ACCESSORY")).toList(), l.notes));
        l.tool = pick(owned, tool, l.notes);
        l.pet = owned.pets.stream().filter(Pet::active).findFirst().orElse(null);
        return l;
    }

    /**
     * The best owned copy per slot for {@code focus}, chosen independently per slot from all readable
     * storage, plus one accessory per family and the single best pet. Hypothetical: not what is worn.
     */
    public static Loadout bestOwned(Owned owned, Stat focus) {
        Loadout l = new Loadout(Kind.BEST_OWNED, owned);
        for (String slot : ARMOR) best(owned.items, slot, focus).ifPresent(l.armor::add);
        for (String slot : EQUIPMENT) best(owned.items, slot, focus).ifPresent(l.equipment::add);
        l.tool = owned.tools().stream().max(Comparator.comparingDouble(i -> StatBreakdown.itemValue(i, focus))).orElse(null);
        List<Item> accessories = owned.items.stream().filter(i -> i.category.equals("ACCESSORY")).toList();
        l.accessories.addAll(uniqueFamilies(accessories, l.notes));
        l.pet = owned.pets.stream().filter(p -> StatBreakdown.petValue(p, focus, StatContext.UNSPECIFIED) != null)
                .max(Comparator.comparingDouble(p -> StatBreakdown.petValue(p, focus, StatContext.UNSPECIFIED))).orElse(null);
        if (l.pet == null && !owned.pets.isEmpty()) l.notes.add("No owned pet has catalogued " + focus.label + ".");
        return l;
    }
    /**
     * Method weighting shared with the upgrade ranking: Mining Fortune and the method's fortune type first,
     * Pristine x 100 and the method's Spread next, Mining Speed at 1% (ignored for instant blocks).
     */
    public static double weigh(MiningMethod m, java.util.function.ToDoubleFunction<Stat> value) {
        return value.applyAsDouble(Stat.MINING_FORTUNE) + value.applyAsDouble(m.fortune) + value.applyAsDouble(m.spread())
                + (m.gems() ? value.applyAsDouble(Stat.PRISTINE) * 100 : 0) + (m.instant ? 0 : value.applyAsDouble(Stat.MINING_SPEED) * .01);
    }
    /** Item value for a method's yield (unconditional stat lines and drill parts). */
    static double methodValue(Item item, MiningMethod m) {
        return weigh(m, s -> StatBreakdown.itemValue(item, s));
    }
    /** Pet value for a method in its usual zone and block target, or null when a catalogued value is missing. */
    static Double methodValue(Pet pet, MiningMethod m) {
        StatContext context = m.usualContext();
        for (Stat s : m.relevantStats()) if (StatBreakdown.petValue(pet, s, context) == null) return null;
        return weigh(m, s -> StatBreakdown.petValue(pet, s, context));
    }
    /** Key ({@code instance}, else item ID) of the owned tool with the highest value for the method. */
    public static String bestTool(Owned owned, MiningMethod m) {
        return owned.tools().stream().filter(i -> i.applies(m)).max(Comparator.comparingDouble(i -> methodValue(i, m)))
                .map(i -> i.instance.isBlank() ? i.id : i.instance).orElse(null);
    }
    /**
     * Best owned gear for the method: one copy per armor/equipment slot from all readable storage, the
     * given tool, one accessory per family and the best catalogued pet. Slots are chosen independently,
     * so a full set bonus can be broken when single pieces score higher.
     */
    public static Loadout bestFor(Owned owned, MiningMethod m, String tool) {
        Loadout l = new Loadout(Kind.BEST_OWNED, owned);
        Map<Item, Double> values = new IdentityHashMap<>();
        Comparator<Item> byValue = Comparator.comparingDouble(i -> values.computeIfAbsent(i, item -> methodValue(item, m)));
        for (String slot : ARMOR) owned.items.stream().filter(i -> i.category.equals(slot) && i.applies(m)).max(byValue).ifPresent(l.armor::add);
        for (String slot : EQUIPMENT) owned.items.stream().filter(i -> i.category.equals(slot) && i.applies(m)).max(byValue).ifPresent(l.equipment::add);
        l.tool = pick(owned, tool, l.notes);
        l.accessories.addAll(uniqueFamilies(owned.items.stream().filter(i -> i.category.equals("ACCESSORY")).toList(), l.notes));
        Pet active = owned.pets.stream().filter(Pet::active).findFirst().orElse(null);
        if (active != null && methodValue(active, m) == null) {
            l.pet = active;
            l.notes.add("Kept the active " + active.label() + ": its stats are not catalogued, so it cannot be compared.");
        } else l.pet = owned.pets.stream().filter(p -> methodValue(p, m) != null)
                .max(Comparator.comparingDouble(p -> methodValue(p, m))).orElse(active);
        return l;
    }
    private static Item pick(Owned owned, String tool, List<String> notes) {
        if (tool == null) return null;
        Item found = owned.tools().stream().filter(i -> i.instance.equals(tool)).findFirst()
                .orElseGet(() -> owned.tools().stream().filter(i -> i.id.equals(tool)).findFirst().orElse(null));
        if (found == null) notes.add("Selected tool " + tool + " is not owned or not readable.");
        return found;
    }
    private static Optional<Item> best(List<Item> items, String category, Stat focus) {
        return items.stream().filter(i -> i.category.equals(category)).max(Comparator.comparingDouble(i -> StatBreakdown.itemValue(i, focus)));
    }
    /**
     * Accessories of one family do not stack: keep the highest-rarity copy of each catalog group
     * (or of each item ID when the family is not catalogued).
     */
    static List<Item> uniqueFamilies(List<Item> accessories, List<String> notes) {
        Map<String, Item> families = new LinkedHashMap<>();
        for (Item item : accessories) {
            String family = item.group.isBlank() ? item.id : item.group;
            Item old = families.get(family);
            if (old == null) { families.put(family, item); continue; }
            Item keep = RARITIES.indexOf(item.tier) > RARITIES.indexOf(old.tier) ? item : old;
            notes.add((keep == item ? old : item).name + " not counted: same accessory family as " + keep.name + ".");
            families.put(family, keep);
        }
        return new ArrayList<>(families.values());
    }

    private static int[] petLevels;
    private static Map<String, Integer> petOffsets;
    static synchronized int petLevel(double exp, String tier) {
        if (petLevels == null) {
            try (var stream = Loadout.class.getResourceAsStream("/pet-levels.json")) {
                JsonObject table = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                petLevels = Catalog.GSON.fromJson(table.get("levels"), int[].class);
                Map<String, Integer> offsets = new HashMap<>();
                table.getAsJsonObject("rarityOffset").entrySet().forEach(e -> offsets.put(e.getKey(), e.getValue().getAsInt()));
                petOffsets = offsets;
            } catch (Exception e) { throw new IllegalStateException("Missing pet level table", e); }
        }
        int offset = petOffsets.getOrDefault(tier, 0), level = 1;
        double total = 0;
        for (int i = offset; i < offset + 99 && i < petLevels.length; i++) {
            if (total + petLevels[i] > exp) break;
            total += petLevels[i]; level++;
        }
        return level;
    }
}
