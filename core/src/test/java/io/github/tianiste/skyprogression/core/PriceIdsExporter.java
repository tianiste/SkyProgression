package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.nio.file.*;

/** Writes every price ID the mod reads (and the NPC sell IDs) for the hosted price bundle. Argument: output file. */
public final class PriceIdsExporter {
    public static void main(String[] args) throws Exception {
        JsonObject out = new JsonObject();
        JsonArray items = new JsonArray(), npc = new JsonArray();
        GearUpgrades.priceIds(Catalog.load()).stream().sorted().forEach(items::add);
        MethodComparison.npcIds().stream().sorted().forEach(npc::add);
        out.add("items", items); out.add("npc", npc);
        Path file = Path.of(args.length > 0 ? args[0] : "build/price-ids.json");
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(out) + "\n");
        System.out.println("Wrote " + items.size() + " price IDs and " + npc.size() + " NPC IDs to " + file.toAbsolutePath());
    }
}
