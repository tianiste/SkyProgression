package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Gear the game client shows (docs/CALCULATIONS.md "Live gear"): worn armor, the main inventory with the held slot,
 * the equipment seen in the equipment menu and the active pet. Items use the inventory API's NBT shape
 * ({@code {"tag":{"ExtraAttributes":{..},"display":{"Name":..,"Lore":[..]}},"Count":n}}), so they are read by the same
 * {@link GearReader} as API items. {@link #apply} merges a snapshot over the API's ownership: live containers replace
 * the API's copies, and the API fills what the client has not seen. Snapshots are immutable; the client builds a new
 * one when the gear changes.
 */
public final class LiveGear {
    /** One container read at {@code at} (epoch ms): index = slot, null = empty slot. */
    public record Part(List<JsonObject> slots, long at) {}
    /**
     * Active pet: type "" = no pet summoned; tier "" = unknown; heldItem null = unknown. from: where it was seen
     * ("pets menu", "tab list", "chat").
     */
    public record Pet(String type, String tier, int level, String heldItem, long at, String from) {}

    /** Container label of API copies whose live container no longer holds them ({@link StartWith#where}). */
    public static final String MOVED = "moved";
    public final Part armor, inventory, equipment;
    /** Inventory slot of the held tool (the selected hotbar slot, or the last one that held a tool); -1 = none. */
    public final int toolSlot;
    public final long toolAt;
    public final Pet pet;
    /** Changes whenever the gear in this snapshot changes; part of the client's report keys. */
    public final int version;
    private List<Item> items;

    public LiveGear(Part armor, Part inventory, Part equipment, int toolSlot, long toolAt, Pet pet, int version) {
        this.armor = armor; this.inventory = inventory; this.equipment = equipment;
        this.toolSlot = toolSlot; this.toolAt = toolAt; this.pet = pet; this.version = version;
    }

    /** Live gear pieces (armor, inventory, equipment), parsed once per snapshot. */
    public synchronized List<Item> items() {
        if (items != null) return items;
        List<Item> out = new ArrayList<>();
        List<Item> catalog = Catalog.load();
        read(armor, "inv_armor", catalog, out);
        read(inventory, "inv_contents", catalog, out);
        read(equipment, "equipment_contents", catalog, out);
        items = List.copyOf(out);
        return items;
    }
    private static void read(Part part, String label, List<Item> catalog, List<Item> out) {
        if (part == null) return;
        for (int slot = 0; slot < part.slots().size(); slot++) {
            JsonObject nbt = part.slots().get(slot);
            if (nbt == null) continue;
            Item item = Loadout.item(nbt, catalog, label, slot);
            if (item == null) continue;
            item.liveAt = part.at();
            out.add(item);
        }
    }

    /** The held tool's key (instance, else item ID), or "" when the tool slot holds no tool. */
    public String heldTool() {
        if (toolSlot < 0) return "";
        String location = "inv_contents #" + toolSlot;
        return items().stream().filter(i -> i.location.equals(location) && Loadout.TOOLS.contains(i.category))
                .map(i -> i.instance.isBlank() ? i.id : i.instance).findFirst().orElse("");
    }

    /**
     * The API's ownership with this snapshot on top. Live armor, inventory and equipment replace the API's copies of
     * those containers; an API copy the live data shows elsewhere is dropped; a copy the API lists in a live container
     * but the client does not show is kept as "moved" (it is somewhere the client cannot see). The live active pet
     * replaces the API's: matched to an API pet of the same type (and rarity when known) for its XP and pet item.
     */
    public Loadout.Owned apply(Loadout.Owned api) {
        Loadout.Owned out = new Loadout.Owned();
        List<Item> live = items();
        Set<String> seen = new HashSet<>();
        for (Item i : live) if (i.extra.has("uuid")) seen.add(i.instance);
        Set<String> replaced = new HashSet<>();
        if (armor != null) replaced.add("inv_armor");
        if (inventory != null) replaced.add("inv_contents");
        if (equipment != null) replaced.add("equipment_contents");
        out.items.addAll(live);
        for (Item i : api.items) {
            boolean uuid = i.extra.has("uuid");
            if (uuid && seen.contains(i.instance)) continue;
            int hash = i.location.lastIndexOf(" #");
            if (!replaced.contains(hash < 0 ? i.location : i.location.substring(0, hash))) { out.items.add(i); continue; }
            if (!uuid) continue;
            Item moved = i.copy(); moved.location = MOVED + " #0";
            out.items.add(moved);
        }
        out.inventoryKnown = api.inventoryKnown || inventory != null;
        if (!api.inventoryKnown && inventory != null) {
            api.notes.stream().filter(n -> !n.startsWith("Inventory API unavailable")).forEach(out.notes::add);
            out.notes.add("Inventory API off: only what the game shows is known (armor, inventory, equipment once its menu was opened). Storage and the accessory bag are unknown.");
        } else out.notes.addAll(api.notes);
        out.heldTool = heldTool();
        pets(api, out);
        return out;
    }

    private void pets(Loadout.Owned api, Loadout.Owned out) {
        out.petsKnown = api.petsKnown;
        if (pet == null) { out.pets.addAll(api.pets); return; }
        Loadout.Pet match = null;
        if (!pet.type().isEmpty()) match = api.pets.stream()
                .filter(p -> p.type().equals(pet.type()) && (pet.tier().isEmpty() || p.tier().equals(pet.tier())))
                .min(Comparator.comparingInt((Loadout.Pet p) -> pet.level() > 0 ? Math.abs(p.level() - pet.level()) : 0)
                        .thenComparing(p -> !p.active()).thenComparingInt(p -> -p.level()))
                .orElse(null);
        for (Loadout.Pet p : api.pets) {
            if (p == match) continue;
            out.pets.add(p.active() ? new Loadout.Pet(p.type(), p.tier(), p.exp(), p.level(), p.heldItem(), false) : p);
        }
        if (pet.type().isEmpty()) { out.petLiveAt = pet.at(); out.petsKnown = true; return; }
        if (match != null) {
            out.pets.add(new Loadout.Pet(match.type(), match.tier(), match.exp(), match.level(), pet.heldItem() == null ? match.heldItem() : pet.heldItem(), true));
            out.petLiveAt = pet.at(); out.petsKnown = true;
        } else if (!pet.tier().isEmpty() && pet.level() > 0) {
            out.pets.add(new Loadout.Pet(pet.type(), pet.tier(), Loadout.petExp(pet.level(), pet.tier()), pet.level(),
                    pet.heldItem() == null ? "" : pet.heldItem(), true));
            out.petLiveAt = pet.at(); out.petsKnown = true;
            if (pet.heldItem() == null) out.notes.add("Pet item of the live " + pet.type() + " pet is unknown: open the pets menu to read it.");
        } else out.notes.add("Live pet " + pet.type() + " not matched: open the pets menu to read its rarity.");
    }

    /** Pet item ID from its lore name: "Quick Claw" -> PET_ITEM_QUICK_CLAW; other names become PET_ITEM_<NAME> (not counted by the ledger). */
    public static String petItemId(String name) {
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "quick claw" -> "PET_ITEM_QUICK_CLAW";
            case "hephaestus relic" -> "HEPHAESTUS_RELIC";
            case "bejeweled collar" -> "BEJEWELED_COLLAR";
            case "tier boost" -> "PET_ITEM_TIER_BOOST";
            default -> "PET_ITEM_" + name.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        };
    }
}
