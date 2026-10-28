package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.nio.file.*;

/**
 * Writes every price ID the mod reads (and the NPC sell IDs) for the hosted price bundle. Arguments: output file, and
 * optionally the hosted API's copy to compare with (a warning, not a failure, when it lists other IDs).
 */
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
        if (args.length > 1) {
            JsonElement hosted = JsonParser.parseString(Files.readString(Path.of(args[1])));
            if (!hosted.equals(out)) System.out.println("WARNING: " + args[1] + " lists other price IDs than the mod reads; items missing there have no"
                    + " price in the hosted bundle. Run ./gradlew :core:exportPriceIds -PpriceIdsOut=" + args[1] + " and deploy the Worker.");
            return;
        }
        System.out.println("Wrote " + items.size() + " price IDs and " + npc.size() + " NPC IDs to " + file.toAbsolutePath());
    }
}
