package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

public final class ProfileParser {
    /** Cumulative HOTM XP for tiers 1..10 (wiki Heart of the Mountain tier table). */
    public static final long[] HOTM_XP = {0, 3000, 12000, 37000, 97000, 197000, 347000, 557000, 847000, 1247000};
    public static JsonElement at(JsonElement value, String... path) {
        for (String part : path) {
            if (value == null || !value.isJsonObject()) return JsonNull.INSTANCE;
            value = value.getAsJsonObject().get(part);
        }
        return value == null ? JsonNull.INSTANCE : value;
    }
    public static String string(JsonElement value, String fallback) {
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
    public static double number(JsonElement value, double fallback) {
        try { double n = value.getAsDouble(); return Double.isFinite(n) ? n : fallback; }
        catch (RuntimeException e) { return fallback; }
    }
    /**
     * Pieces saved in Loadouts armor/equipment sets (`loadout.armor.<n>.<SLOT>`, one item per slot).
     * The worn set is also in inv_armor/equipment_contents; UUID deduplication drops the copy.
     */
    public static void loadoutPieces(JsonElement member, java.util.function.BiConsumer<String, JsonElement> piece) {
        for (String kind : List.of("armor", "equipment")) {
            JsonElement sets = at(member, "loadout", kind);
            if (!sets.isJsonObject()) continue;
            for (var set : sets.getAsJsonObject().entrySet()) {
                if (!set.getValue().isJsonObject()) continue;
                for (var slot : set.getValue().getAsJsonObject().entrySet())
                    if (slot.getValue().isJsonObject()) piece.accept("loadout " + kind + " set " + set.getKey() + " " + slot.getKey(), slot.getValue());
            }
        }
    }
    public static List<Profile> parse(JsonObject root, String uuid, String ign) {
        List<Profile> result = new ArrayList<>();
        JsonElement profiles = root.get("profiles");
        if (profiles == null || !profiles.isJsonArray()) return result;
        for (JsonElement entry : profiles.getAsJsonArray()) {
            JsonElement member = at(entry, "members", uuid);
            if (!member.isJsonObject()) continue;
            Profile p = new Profile();
            p.id = string(at(entry, "profile_id"), "");
            p.name = string(at(entry, "cute_name"), "Profile"); p.player = ign;
            p.selected = "true".equals(string(at(entry, "selected"), "false"));
            p.mode = string(at(entry, "game_mode"), "normal");
            JsonElement inventory = at(member, "inventory");
            p.inventoryEnabled = !string(at(inventory, "inv_contents", "data"), "").isBlank();
            if (!p.inventoryEnabled) p.notes.add("Inventory API unavailable. SkyBlock Menu > Settings > API Settings > Inventory API: ON.");
            else {
                for (String field : List.of("inv_contents", "inv_armor", "equipment_contents", "ender_chest_contents", "wardrobe_contents"))
                    readContainer(at(inventory, field), p, field);
                JsonElement bags = at(inventory, "bag_contents");
                if (bags.isJsonObject()) bags.getAsJsonObject().entrySet().forEach(e -> readContainer(e.getValue(), p, e.getKey()));
                JsonElement backpacks = at(inventory, "backpack_contents");
                if (backpacks.isJsonObject()) backpacks.getAsJsonObject().entrySet().forEach(e -> readContainer(e.getValue(), p, "backpack " + e.getKey()));
                loadoutPieces(member, (label, piece) -> readContainer(piece, p, label));
                JsonElement sacks = at(inventory, "sacks_counts");
                if (!sacks.isJsonObject()) sacks = at(member, "sacks_counts");
                if (sacks.isJsonObject()) sacks.getAsJsonObject().entrySet().forEach(e -> p.owned.merge(e.getKey(), (int)number(e.getValue(), 0), Integer::sum));
            }
            // Current profiles store HOTM under skill_tree; mining_core.experience is the legacy location.
            double xp = number(at(member, "skill_tree", "experience", "mining"), number(at(member, "mining_core", "experience"), -1));
            long[] thresholds = HOTM_XP;
            if (xp >= 0) { p.hotm = 1; for (int i = 1; i < thresholds.length; i++) if (xp >= thresholds[i]) p.hotm = i + 1; }
            p.progress.put("Heart of the Mountain tier", (double)p.hotm);
            if (xp >= 0) p.milestones.add(new Milestone("Heart of the Mountain", p.hotm, 10, "tiers"));
            // mining_core.powder_<type> is the unspent powder (21.2M Gemstone on the owner's profile, matching the game);
            // powder_spent_<type> is what the tree used. Only the unspent amount is shown.
            for (String powder : List.of("mithril", "gemstone", "glacite")) {
                JsonElement available = at(member, "mining_core", "powder_" + powder);
                if (!available.isJsonNull()) p.progress.put(powder + " powder (unspent)", number(available, 0));
            }
            JsonElement miningXp = at(member, "player_data", "experience", "SKILL_MINING");
            if (!miningXp.isJsonNull()) p.progress.put("Mining skill XP", number(miningXp, 0));
            JsonObject rules = Catalog.progressionRules();
            JsonElement levels = at(rules, "mining", "levels");
            JsonElement enchantingXp=at(member,"player_data","experience","SKILL_ENCHANTING");
            if(!enchantingXp.isJsonNull()&&levels.isJsonArray()) {
                p.enchantingLevel=0;
                for(JsonElement rule:levels.getAsJsonArray())if(number(enchantingXp,0)>=number(at(rule,"totalExpRequired"),Double.MAX_VALUE))
                    p.enchantingLevel=Math.max(p.enchantingLevel,(int)number(at(rule,"level"),0));
            }
            if (!miningXp.isJsonNull() && levels.isJsonArray()) {
                int level = 0;
                for (JsonElement rule : levels.getAsJsonArray())
                    if (number(miningXp, 0) >= number(at(rule, "totalExpRequired"), Double.MAX_VALUE))
                        level = Math.max(level, (int)number(at(rule, "level"), 0));
                int maximum = (int)number(at(rules, "mining", "maxLevel"), 60);
                p.milestones.add(new Milestone("Mining skill", level, maximum, "levels"));
                p.milestones.add(new Milestone("Mining skill Fortune", level * 4, maximum * 4, "Mining Fortune"));
            }
            for (String collection : List.of("TUNGSTEN", "UMBER", "GLACITE", "OBSIDIAN")) {
                JsonElement count = at(member, "collection", collection);
                if (!count.isJsonNull()) p.progress.put(collection + " collection", number(count, 0));
                JsonElement tiers = at(rules, "collections", collection, "tiers");
                if (!count.isJsonNull() && tiers.isJsonArray()) {
                    int current = 0;
                    for (JsonElement rule : tiers.getAsJsonArray())
                        if (number(count,0) >= number(at(rule,"amountRequired"),Double.MAX_VALUE))
                            current = Math.max(current,(int)number(at(rule,"tier"),0));
                    p.milestones.add(new Milestone(collection + " collection",current,
                            number(at(rules,"collections",collection,"maxTiers"),tiers.getAsJsonArray().size()),"tiers"));
                }
            }
            JsonElement pets = at(member, "pets_data", "pets");
            if (pets.isJsonArray()) for (JsonElement pet : pets.getAsJsonArray()) {
                if ("true".equals(string(at(pet, "active"), "false")))
                    p.notes.add("Active pet: " + string(at(pet, "type"), "Unknown") + " (" + string(at(pet, "tier"), "Unknown") + "). Pet bonuses need your effective stats.");
            }
            p.notes.add("Gear values use stored item lore when available; these include displayed modifiers but may reflect the item's last context. Pet, HOTM, attributes and temporary buffs still require effective stats.");
            result.add(p);
        }
        return result;
    }
    private static void readContainer(JsonElement container, Profile p, String label) {
        String data = string(at(container, "data"), "");
        if (data.isEmpty()) return;
        try {
            JsonElement items = new InventoryReader().read(data).get("i");
            if (items == null || !items.isJsonArray()) throw new java.io.IOException("Missing inventory item list");
            List<Item> catalog = Catalog.load();
            int slot=0;
            for (JsonElement item : items.getAsJsonArray()) GearReader.accept(item,p,catalog,label,slot++);
        } catch (Exception e) {
            p.inventoryEnabled = false;
            p.notes.add("Could not read " + label + ". Recommendations are paused because ownership is incomplete. Refresh later.");
        }
    }
}
