package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regression checks against the sanitized Debotes · Zucchini fixture (see FixtureExtractor). */
class ProfileFixtureTest {
    static JsonObject fixture() throws IOException {
        try (var in = ProfileFixtureTest.class.getResourceAsStream("/fixtures/debotes-zucchini.json")) {
            assertNotNull(in, "fixture missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    static JsonObject member(JsonObject root) {
        return at(root.getAsJsonArray("profiles").get(0), "members", FixtureExtractor.MEMBER).getAsJsonObject();
    }
    static Profile profile() throws IOException {
        return ProfileParser.parse(fixture(), FixtureExtractor.MEMBER, "Debotes").getFirst();
    }
    static Item gear(Profile p, String id) {
        return p.gear.stream().filter(i -> i.id.equals(id)).findFirst().orElseThrow(() -> new AssertionError(id + " not read"));
    }

    @Test void readsHotmFromSkillTree() throws IOException {
        var p = profile();
        assertEquals(10, p.hotm);
        assertTrue(p.inventoryEnabled);
    }
    @Test void readsEquippedGearAndDisplayedTotals() throws IOException {
        var p = profile();
        for (String id : List.of("DIVAN_HELMET", "DIVAN_CHESTPLATE", "DIVAN_LEGGINGS", "DIVAN_BOOTS",
                "TITANIUM_NECKLACE", "TITANIUM_CLOAK", "TITANIUM_BELT", "DWARVEN_HANDWARMERS", "TITANIUM_DRILL_4",
                "MINERAL_TALISMAN", "TITANIUM_RELIC"))
            assertTrue(gear(p, id).observed, id + " lore totals");
        var drill = gear(p, "TITANIUM_DRILL_4");
        assertEquals(1985, stat(drill.stats, Stat.MINING_SPEED));
        assertEquals(235, stat(drill.stats, Stat.MINING_FORTUNE));
        assertEquals("auspicious", drill.modifier);
        assertEquals("ruby_polished_drill_engine", string(at(drill.extra, "drill_part_engine"), ""));
        assertEquals("goblin_omelette_sunny_side", string(at(drill.extra, "drill_part_upgrade_module"), ""));
        var boots = gear(p, "DIVAN_BOOTS");
        assertEquals(160, stat(boots.stats, Stat.MINING_FORTUNE));
        assertEquals(340, stat(boots.stats, Stat.MINING_SPEED));
        assertTrue(boots.recombobulated);
        assertEquals(3, stat(gear(p, "MINERAL_TALISMAN").stats, Stat.MINING_FORTUNE));
    }
    @Test void exposesRawSourcesForTheStatLedger() throws IOException {
        var m = member(fixture());
        assertEquals(50, number(at(m, "skill_tree", "nodes", "mining", "mining_fortune"), -1));
        assertEquals(50, number(at(m, "skill_tree", "nodes", "mining", "mining_speed"), -1));
        assertEquals(3, number(at(m, "player_data", "perks", "dwarven_training"), -1));
        assertEquals(96, number(at(m, "attributes", "stacks", "fortunate_miner"), -1));
        assertEquals(381, number(at(m, "accessory_bag_storage", "highest_magical_power"), -1));
        assertTrue(at(m, "player_data", "active_effects").getAsJsonArray().isEmpty());
        var active = new ArrayList<String>();
        at(m, "pets_data", "pets").getAsJsonArray().forEach(pet -> {
            if (pet.getAsJsonObject().get("active").getAsBoolean()) active.add(string(at(pet, "type"), "") + "/" + string(at(pet, "heldItem"), ""));
        });
        assertEquals(List.of("BAL/PET_ITEM_QUICK_CLAW"), active);
    }
    @Test void fixtureCarriesNoIdentifiers() throws IOException {
        var root = fixture();
        var text = root.toString();
        assertFalse(text.matches("(?s).*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-.*"), "UUID in fixture JSON");
        var inventory = at(member(root), "inventory").getAsJsonObject();
        var containers = new ArrayList<JsonElement>(List.of(inventory.get("inv_armor"), inventory.get("equipment_contents"),
                inventory.get("inv_contents"), at(inventory, "bag_contents", "talisman_bag")));
        for (var container : containers) {
            String decoded = new InventoryReader().read(string(at(container, "data"), "")).toString();
            assertFalse(decoded.contains("\"uuid\""), "item uuid in fixture inventory");
            assertFalse(decoded.contains("\"timestamp\""), "timestamp in fixture inventory");
        }
    }
}
