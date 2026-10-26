package io.github.tianiste.skyprogression.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionEngineTest {
    Item item(String id, double fortune) {
        Item i = new Item(); i.id = id; i.name = id; i.group = "accessory";
        i.stats.put(Stat.MINING_FORTUNE, fortune); return i;
    }
    Profile profile() { Profile p = new Profile(); p.inventoryEnabled = true; p.hotm = 10; return p; }
    @Test void replacementsOnlyCountDifferenceAndNeverStack() {
        var engine = new ProgressionEngine(List.of(item("a", 3), item("b", 6)));
        var p = profile(); p.owned.put("a", 1);
        var list = engine.upgrades(p, MiningMethod.COAL, Stat.MINING_FORTUNE, Map.of(), Map.of("b", new Price(600, "AH")));
        assertEquals(1, list.size()); assertEquals(3, list.getFirst().gain()); assertEquals(200, list.getFirst().costPerUnit());
        p.owned.put("b", 1); assertEquals(6, engine.subtotal(p, MiningMethod.COAL).get(Stat.MINING_FORTUNE));
    }
    @Test void missingApiIsNotAnEmptyInventory() {
        var p = profile(); p.inventoryEnabled = false;
        assertTrue(new ProgressionEngine(List.of(item("a", 3))).upgrades(p, MiningMethod.COAL, null, Map.of(), Map.of()).isEmpty());
    }
    @Test void unknownPriceIsNeverFree() {
        var engine = new ProgressionEngine(List.of(item("a", 3)));
        var u = engine.upgrades(profile(), MiningMethod.COAL, Stat.MINING_FORTUNE, Map.of(), Map.of()).getFirst();
        assertNull(u.cost()); assertEquals(Double.POSITIVE_INFINITY, u.costPerUnit());
    }
    @Test void ratesScaleWithEfficiencyAndFortune() {
        var a = ProgressionEngine.rate(MiningMethod.COAL, Map.of(), 50, 1, 1, 2, .1);
        var b = ProgressionEngine.rate(MiningMethod.COAL, Map.of(Stat.MINING_FORTUNE, 100.0), 100, 1, 1, 2, .1);
        assertEquals(1800, a.itemsPerHour()); assertEquals(7200, b.itemsPerHour()); assertEquals(14400, b.coinsPerHour());
    }
    @Test void pristineAppliesOnlyToGems() {
        var stats = Map.of(Stat.PRISTINE, 10.0);
        assertEquals(1, ProgressionEngine.yieldMultiplier(MiningMethod.COAL, stats));
        assertEquals(8.9, ProgressionEngine.yieldMultiplier(MiningMethod.MF_GEMS, stats), .0001);
    }
    @Test void rejectsInvalidRates() {
        assertThrows(IllegalArgumentException.class, () -> ProgressionEngine.rate(MiningMethod.COAL, Map.of(), 101, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ProgressionEngine.rate(MiningMethod.COAL, Map.of(), 50, Double.NaN, 1, 1, 1));
    }
    @Test void requirementsSortAfterAvailableUpgrades() {
        var a = item("a", 6); a.hotm = 10;
        var b = item("b", 3); var p = profile(); p.hotm = 2;
        var list = new ProgressionEngine(List.of(a,b)).upgrades(p, MiningMethod.COAL, Stat.MINING_FORTUNE, Map.of(), Map.of("a",new Price(1,"AH"),"b",new Price(100,"AH")));
        assertEquals("b", list.getFirst().item().id); assertEquals("Requires HOTM 10",list.getLast().blocked());
    }
    @Test void catalogHasUniqueIdsAndAllMethodsExist() {
        var catalog = Catalog.load();
        assertTrue(catalog.size() > 30);
        assertEquals(catalog.size(), catalog.stream().map(i -> i.id).distinct().count());
        assertEquals(14, MiningMethod.values().length);
    }
}
