package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Regenerates `core/src/main/resources/mining-recipes.json`: the crafting or forge recipe of every priced item (catalog
 * items, drill parts, materials) from the NotEnoughUpdates repository (MIT) `items/<ID>.json`, and the recipes of their inputs a few levels deep, with the
 * unlock it needs ("Requires: HotM 9" or a collection tier, resolved to an amount with the official collections
 * resource). Pass a commit SHA to pin the source; the default is master. Run with `./gradlew :core:extractRecipes`.
 */
public final class RecipeExtractor {
    private static final List<String> ROMAN = List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII");
    public static void main(String[] args) throws Exception {
        String ref = args.length > 0 ? args[0] : "master";
        HttpClient http = HttpClient.newHttpClient();
        // Collection name -> key and tier amounts (official resource).
        Map<String, String> collectionKeys = new HashMap<>();
        Map<String, JsonArray> collectionTiers = new HashMap<>();
        JsonObject collections = JsonParser.parseString(get(http, "https://api.hypixel.net/v2/resources/skyblock/collections")).getAsJsonObject();
        for (var category : collections.getAsJsonObject("collections").entrySet())
            for (var item : category.getValue().getAsJsonObject().getAsJsonObject("items").entrySet()) {
                String name = item.getValue().getAsJsonObject().get("name").getAsString();
                collectionKeys.put(name, item.getKey());
                collectionTiers.put(name, item.getValue().getAsJsonObject().getAsJsonArray("tiers"));
            }
        Set<String> ids = new TreeSet<>();
        for (String id : GearUpgrades.priceIds(Catalog.load())) if (!id.contains(";") && !id.startsWith("ESSENCE_")) ids.add(id);
        JsonObject out = new JsonObject();
        // Inputs' own recipes too, so an input that cannot be bought can be crafted in turn (Recipes.MAX_DEPTH levels).
        Set<String> round = ids, seen = new HashSet<>(ids);
        for (int depth = 0; depth <= Recipes.MAX_DEPTH + 1 && !round.isEmpty(); depth++) {
            Set<String> next = new TreeSet<>();
            for (String id : round) {
                String body = get(http, "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/" + ref + "/items/" + id.replace(':', '-') + ".json");
                if (body == null) continue;
                JsonObject item;
                try { item = JsonParser.parseString(body).getAsJsonObject(); } catch (RuntimeException e) { continue; }
                JsonObject recipe = new JsonObject(), inputs = new JsonObject();
                JsonObject forge = null;
                if (item.has("recipes")) for (JsonElement r : item.getAsJsonArray("recipes"))
                    if (r.isJsonObject() && "forge".equals(r.getAsJsonObject().has("type") ? r.getAsJsonObject().get("type").getAsString() : "")) { forge = r.getAsJsonObject(); break; }
                if (forge != null) {
                    recipe.addProperty("type", "forge");
                    for (JsonElement in : forge.getAsJsonArray("inputs")) add(inputs, in.getAsString());
                    recipe.addProperty("count", forge.has("count") ? forge.get("count").getAsDouble() : 1);
                    recipe.addProperty("duration", forge.has("duration") ? forge.get("duration").getAsLong() : 0);
                } else if (item.has("recipe") && item.get("recipe").isJsonObject()) {
                    recipe.addProperty("type", "craft");
                    JsonObject grid = item.getAsJsonObject("recipe");
                    for (String slot : List.of("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"))
                        if (grid.has(slot) && !grid.get(slot).getAsString().isBlank()) add(inputs, grid.get(slot).getAsString());
                    recipe.addProperty("count", grid.has("count") ? grid.get("count").getAsDouble() : 1);
                } else continue;
                if (inputs.isEmpty()) continue;
                recipe.add("inputs", inputs);
                String requires = item.has("crafttext") ? item.get("crafttext").getAsString().replaceFirst("^Requires:\\s*", "").strip() : "";
                if (!requires.isEmpty()) {
                    recipe.addProperty("requires", requires);
                    for (String part : requires.split("\\s*&\\s*")) {
                        java.util.regex.Matcher hotm = java.util.regex.Pattern.compile("^HotM (\\d+)$").matcher(part);
                        java.util.regex.Matcher tier = java.util.regex.Pattern.compile("^(.+) ([IVX]+)$").matcher(part);
                        if (hotm.find()) recipe.addProperty("hotm", Integer.parseInt(hotm.group(1)));
                        else if (tier.find() && collectionKeys.containsKey(tier.group(1))) {
                            int n = ROMAN.indexOf(tier.group(2)) + 1;
                            for (JsonElement t : collectionTiers.get(tier.group(1)))
                                if (t.getAsJsonObject().get("tier").getAsInt() == n) {
                                    recipe.addProperty("collection", collectionKeys.get(tier.group(1)));
                                    recipe.addProperty("collectionAmount", t.getAsJsonObject().get("amountRequired").getAsDouble());
                                }
                        } else recipe.addProperty("unchecked", part);
                    }
                }
                out.add(id, recipe);
                for (String in : inputs.keySet()) if (seen.add(in)) next.add(in);
            }
            round = next;
        }
        JsonObject file = new JsonObject();
        file.addProperty("_note", "Generated by ./gradlew :core:extractRecipes from NotEnoughUpdates-REPO items/<ID>.json (" + ref + ") and the official collections resource.");
        file.addProperty("_license", "Source data: NotEnoughUpdates-REPO, MIT License, Copyright (c) 2020 Moulberry. https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/blob/master/LICENSE");
        file.add("recipes", out);
        Path target = Path.of("core/src/main/resources/mining-recipes.json");
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(file) + "\n", StandardCharsets.UTF_8);
        System.out.println("Wrote " + out.size() + " recipes for " + seen.size() + " items to " + target);
    }
    private static void add(JsonObject inputs, String entry) {
        int colon = entry.lastIndexOf(':');
        // NEU writes damage values as "INK_SACK-4"; the Bazaar and Coflnet call the item "INK_SACK:4".
        String id = (colon < 0 ? entry : entry.substring(0, colon)).replaceFirst("-(\\d+)$", ":$1");
        double n = colon < 0 ? 1 : Double.parseDouble(entry.substring(colon + 1));
        inputs.addProperty(id, (inputs.has(id) ? inputs.get(id).getAsDouble() : 0) + n);
    }
    private static String get(HttpClient http, String url) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? response.body() : null;
    }
}
