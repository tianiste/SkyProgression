package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

public final class ProfileParser {
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
            p.inventoryEnabled = at(inventory, "inv_contents", "data").isJsonPrimitive();
            if (!p.inventoryEnabled) p.notes.add("Inventory API unavailable. SkyBlock Menu > Settings > API Settings > Inventory API: ON.");
            else {
                for (String field : List.of("inv_contents", "inv_armor", "equipment_contents", "ender_chest_contents", "wardrobe_contents"))
                    readContainer(at(inventory, field), p, field);
                JsonElement bags = at(inventory, "bag_contents");
                if (bags.isJsonObject()) bags.getAsJsonObject().entrySet().forEach(e -> readContainer(e.getValue(), p, e.getKey()));
                JsonElement backpacks = at(inventory, "backpack_contents");
                if (backpacks.isJsonObject()) backpacks.getAsJsonObject().entrySet().forEach(e -> readContainer(e.getValue(), p, "backpack " + e.getKey()));
                JsonElement sacks = at(inventory, "sacks_counts");
                if (!sacks.isJsonObject()) sacks = at(member, "sacks_counts");
                if (sacks.isJsonObject()) sacks.getAsJsonObject().entrySet().forEach(e -> p.owned.merge(e.getKey(), (int)number(e.getValue(), 0), Integer::sum));
            }
            double xp = number(at(member, "mining_core", "experience"), -1);
            long[] thresholds = {0, 3000, 12000, 37000, 97000, 197000, 347000, 557000, 847000, 1247000};
            if (xp >= 0) { p.hotm = 1; for (int i = 1; i < thresholds.length; i++) if (xp >= thresholds[i]) p.hotm = i + 1; }
            p.progress.put("Heart of the Mountain tier", (double)p.hotm);
            for (String powder : List.of("mithril", "gemstone", "glacite")) {
                double spent = number(at(member, "mining_core", "powder_spent_" + powder), 0);
                double available = number(at(member, "mining_core", "powder_" + powder), 0);
                p.progress.put(powder + " powder (spent + available)", spent + available);
            }
            JsonElement miningXp = at(member, "player_data", "experience", "SKILL_MINING");
            if (!miningXp.isJsonNull()) p.progress.put("Mining skill XP", number(miningXp, 0));
            for (String collection : List.of("TUNGSTEN", "UMBER", "GLACITE", "OBSIDIAN")) {
                JsonElement count = at(member, "collection", collection);
                if (!count.isJsonNull()) p.progress.put(collection + " collection", number(count, 0));
            }
            JsonElement pets = at(member, "pets_data", "pets");
            if (pets.isJsonArray()) for (JsonElement pet : pets.getAsJsonArray()) {
                if ("true".equals(string(at(pet, "active"), "false")))
                    p.notes.add("Active pet: " + string(at(pet, "type"), "Unknown") + " (" + string(at(pet, "tier"), "Unknown") + "). Pet bonuses need your effective stats.");
            }
            p.notes.add("Base item stats only. Reforges, gems, enchants, pet bonuses, HOTM perks, attributes and temporary buffs are not included in this subtotal.");
            result.add(p);
        }
        return result;
    }
    private static void readContainer(JsonElement container, Profile p, String label) {
        String data = string(at(container, "data"), "");
        if (data.isEmpty()) return;
        try {
            JsonElement items = new InventoryReader().read(data).get("i");
            if (items == null || !items.isJsonArray()) return;
            for (JsonElement item : items.getAsJsonArray()) {
                String id = string(at(item, "tag", "ExtraAttributes", "id"), "");
                if (!id.isEmpty()) p.owned.merge(id, Math.max(1, (int)number(at(item, "Count"), 1)), Integer::sum);
            }
        } catch (Exception e) { p.notes.add("Could not read " + label + "; ownership may be incomplete."); }
    }
}
