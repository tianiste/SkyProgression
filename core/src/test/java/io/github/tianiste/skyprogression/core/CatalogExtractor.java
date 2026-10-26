package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Regenerates `core/src/main/resources/mining-items.json` from the official items resource: every obtainable item
 * whose stats include a mining stat. Stats, rarity, category, gemstone slots and the HOTM requirement come from
 * the resource; tools are limited to the methods their Breaking Power can mine. Entries the resource has no mining
 * stats for (lanterns, Mithril/Titanium equipment) are hand-checked and kept as they are, as are existing groups,
 * sources and non-tool methods. Run with `./gradlew :core:extractCatalog` (or pass a saved resource file).
 */
public final class CatalogExtractor {
    private static final Set<String> MINING = Set.of("MINING_SPEED", "MINING_FORTUNE", "GEMSTONE_FORTUNE", "ORE_FORTUNE",
            "DWARVEN_METAL_FORTUNE", "BLOCK_FORTUNE", "MINING_SPREAD", "GEMSTONE_SPREAD", "PRISTINE");
    private static final Set<String> TOOLS = Set.of("PICKAXE", "DRILL", "GAUNTLET");
    private static final Map<String, String> GROUPS = Map.of("HELMET", "Helmet", "CHESTPLATE", "Chestplate", "LEGGINGS", "Leggings",
            "BOOTS", "Boots", "NECKLACE", "Necklace", "CLOAK", "Cloak", "BELT", "Belt", "GLOVES", "Gloves", "BRACELET", "Bracelet");
    /** Accessory families (only the highest-rarity member counts); unknown families use the item name. */
    private static final Map<String, String> FAMILIES = Map.of("MINERAL_TALISMAN", "Mineral accessory", "GLOSSY_MINERAL_TALISMAN", "Mineral accessory",
            "HASTE_RING", "Haste accessory", "HASTE_ARTIFACT", "Haste accessory", "SPEED_RELIC", "Speed accessory");
    private static final List<String> ORDER = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "PICKAXE", "DRILL", "GAUNTLET",
            "NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET", "ACCESSORY");

    public static void main(String[] args) throws Exception {
        String body;
        if (args.length > 0) body = Files.readString(Path.of(args[0]));
        else {
            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/resources/skyblock/items")).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Items resource unavailable: " + response.statusCode());
            body = response.body();
        }
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.get("success").getAsBoolean()) throw new IllegalStateException("Items resource unavailable");
        Path target = Path.of("core/src/main/resources/mining-items.json");
        Map<String, JsonObject> existing = new LinkedHashMap<>();
        for (JsonElement e : JsonParser.parseString(Files.readString(target)).getAsJsonArray()) existing.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject());
        Map<String, JsonObject> generated = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("items")) {
            JsonObject item = e.getAsJsonObject();
            JsonObject stats = item.has("stats") ? item.getAsJsonObject("stats") : new JsonObject();
            String tier = item.has("tier") ? item.get("tier").getAsString() : "COMMON";
            String category = item.has("category") ? item.get("category").getAsString() : "";
            if (stats.keySet().stream().noneMatch(MINING::contains) || tier.equals("SPECIAL") || tier.equals("UNOBTAINABLE")
                    || !ORDER.contains(category)) continue;
            String id = item.get("id").getAsString();
            JsonObject old = existing.get(id), out = new JsonObject();
            out.addProperty("id", id);
            out.addProperty("name", item.get("name").getAsString().replaceAll("§.", ""));
            out.addProperty("group", old != null ? old.get("group").getAsString() : TOOLS.contains(category) ? "Tool"
                    : category.equals("ACCESSORY") ? FAMILIES.getOrDefault(id, item.get("name").getAsString().replaceAll("§.", "")) : GROUPS.get(category));
            JsonObject kept = new JsonObject();
            for (String s : MINING) if (stats.has(s)) kept.add(s, stats.get(s));
            if (stats.has("COLD_RESISTANCE")) kept.add("COLD_RESISTANCE", stats.get("COLD_RESISTANCE"));
            out.add("stats", kept);
            int hotm = 0;
            List<String> needs = new ArrayList<>();
            if (item.has("requirements")) for (JsonElement r : item.getAsJsonArray("requirements")) {
                JsonObject req = r.getAsJsonObject();
                switch (req.get("type").getAsString()) {
                    case "HEART_OF_THE_MOUNTAIN" -> hotm = req.get("tier").getAsInt();
                    case "COLLECTION" -> needs.add(title(req.get("collection").getAsString()) + " collection " + req.get("tier").getAsInt());
                    case "SKILL" -> needs.add(title(req.get("skill").getAsString()) + " " + req.get("level").getAsInt());
                    default -> {}
                }
            }
            out.addProperty("hotm", hotm);
            JsonArray methods = new JsonArray();
            if (TOOLS.contains(category)) {
                int power = stats.has("BREAKING_POWER") ? stats.get("BREAKING_POWER").getAsInt() : 0;
                for (MiningMethod m : MiningMethod.values()) if (m.breakingPower() != null && power >= m.breakingPower()) methods.add(m.name());
            } else if (old != null) methods = old.getAsJsonArray("methods");
            out.add("methods", methods);
            out.addProperty("source", old != null ? old.get("source").getAsString()
                    : "Auction House / Bazaar" + (needs.isEmpty() ? "; check item requirements" : "; requires " + String.join(", ", needs)));
            out.addProperty("tier", tier);
            out.addProperty("category", category);
            out.add("slots", item.has("gemstone_slots") ? item.getAsJsonArray("gemstone_slots") : new JsonArray());
            generated.put(id, out);
        }
        List<JsonObject> result = new ArrayList<>();
        // Existing order first (refreshed or hand-checked), then new items by category.
        for (var e : existing.entrySet()) result.add(generated.getOrDefault(e.getKey(), e.getValue()));
        generated.values().stream().filter(o -> !existing.containsKey(o.get("id").getAsString()))
                .sorted(Comparator.comparingInt((JsonObject o) -> ORDER.indexOf(o.get("category").getAsString())).thenComparing(o -> o.get("id").getAsString()))
                .forEach(result::add);
        JsonArray array = new JsonArray();
        result.forEach(array::add);
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(array) + "\n", StandardCharsets.UTF_8);
        System.out.println("Wrote " + result.size() + " items (" + generated.size() + " from the resource, lastUpdated "
                + root.get("lastUpdated").getAsLong() + ") to " + target);
    }
    private static String title(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.toLowerCase(Locale.ROOT).split("_")) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }
}
