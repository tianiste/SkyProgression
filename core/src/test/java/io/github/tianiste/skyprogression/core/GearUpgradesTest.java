package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class GearUpgradesTest {
    Item drill() {
        Item i=new Item();i.id="TITANIUM_DRILL_4";i.name="Drill";i.group="Tool";i.category="DRILL";i.tier="LEGENDARY";i.instance="one";
        i.stats.put(Stat.MINING_FORTUNE,120.0);i.stats.put(Stat.MINING_SPEED,1600.0);return i;
    }
    Profile profile(Item item) {
        Profile p=new Profile();p.inventoryEnabled=true;p.enchantingLevel=60;p.hotm=10;p.owned.put(item.id,1);p.gear.add(item);return p;
    }
    Slot jadeSlot(boolean locked) {
        Slot slot=new Slot();slot.slot_type="JADE";
        if(locked){Ingredient i=new Ingredient();i.type="ITEM";i.item_id="GEMSTONE_CHAMBER";i.amount=1;slot.costs.add(i);}
        return slot;
    }
    @Test void readsInstanceModifiersAndDoesNotDoubleCountLore() {
        Item base=drill();base.instance="";
        var nbt=JsonParser.parseString("""
           {"tag":{"ExtraAttributes":{"id":"TITANIUM_DRILL_4","uuid":"abc","rarity_upgrades":1,
             "modifier":"auspicious","enchantments":{"fortune":3,"efficiency":7}},
             "display":{"Name":"§6Auspicious Drill","Lore":["§7Mining Fortune: §a+295 §8(+20)","Mining Speed: +2,050","Pristine: +4.5"]}}}
           """);
        Item actual=GearReader.read(nbt,base,"backpack 1",2);
        assertEquals(295,actual.stats.get(Stat.MINING_FORTUNE));assertEquals(2050,actual.stats.get(Stat.MINING_SPEED));
        assertEquals(4.5,actual.stats.get(Stat.PRISTINE));assertEquals("MYTHIC",actual.tier);assertEquals(3,actual.enchantments.get("fortune"));
        assertEquals("backpack 1 #2",actual.location);assertEquals(120,base.stats.get(Stat.MINING_FORTUNE));
    }
    @Test void samePhysicalItemIsNotCountedTwiceAcrossContainers() {
        Item base=drill();Profile p=new Profile();
        var nbt=JsonParser.parseString("{\"Count\":1,\"tag\":{\"ExtraAttributes\":{\"id\":\"TITANIUM_DRILL_4\",\"uuid\":\"same\"}}}");
        GearReader.accept(nbt,p,List.of(base),"inventory",0);GearReader.accept(nbt,p,List.of(base),"wardrobe",0);
        assertEquals(1,p.gear.size());assertEquals(1,p.owned.get(base.id));
    }
    @Test void differentCopiesCompeteWithoutStacking() {
        Item a=drill(),b=drill();b.instance="two";b.stats.put(Stat.MINING_FORTUNE,200.0);
        Profile p=profile(a);p.gear.add(b);
        var engine=new ProgressionEngine(List.of(drill()));
        assertEquals("two",engine.bestOwned(p,MiningMethod.MF_GEMS,Stat.MINING_FORTUNE).get("Tool").instance);
        assertEquals(200,engine.subtotal(p,MiningMethod.MF_GEMS).get(Stat.MINING_FORTUNE));
    }
    @Test void fortuneDeltaIsIncrementalAndSilkTouchIsRespected() {
        Item i=drill();i.enchantments.put("fortune",3);var p=profile(i);
        var candidates=GearUpgrades.forItem(i,MiningMethod.COAL,p,Map.of());
        assertEquals(15,candidates.stream().filter(c->c.label().startsWith("Fortune")).findFirst().orElseThrow().delta().get(Stat.MINING_FORTUNE));
        i.enchantments.put("silk_touch",1);
        assertTrue(GearUpgrades.forItem(i,MiningMethod.COAL,p,Map.of()).stream().noneMatch(c->c.label().startsWith("Fortune")));
    }
    @Test void maxedEnchantmentsAreNotRecommendedAgain() {
        Item i=drill();i.enchantments.putAll(Map.of("fortune",4,"efficiency",10,"pristine",5));i.extra.addProperty("polarvoid",5);i.extra.addProperty("drill_part_fuel_tank","perfectly_cut_fuel_tank");i.extra.addProperty("drill_part_engine","amber_polished_drill_engine");i.extra.addProperty("drill_part_upgrade_module","goblin_omelette_sunny_side");
        // Only trade-offs remain (e.g. Starfall Seasoning: more Speed, less Fortune), which the rate or drop model then values.
        assertTrue(GearUpgrades.forItem(i,MiningMethod.MF_GEMS,profile(i),Map.of()).stream().allMatch(c->c.delta().values().stream().anyMatch(v->v<0)));
    }
    @Test void prismaticUsesHalfPointPerLevelAndCheaperCombination() {
        Item i=drill();i.enchantments.put("pristine",3);var p=profile(i);
        var prices=Map.of("ENCHANTMENT_PRISTINE_3",new Price(100,"Bazaar"),"ENCHANTMENT_PRISTINE_4",new Price(300,"Bazaar"));
        var proposal=GearUpgrades.forItem(i,MiningMethod.MF_GEMS,p,prices).stream().filter(c->c.label().equals("Prismatic 3 -> 4")).findFirst().orElseThrow();
        assertEquals(.5,proposal.delta().get(Stat.PRISTINE));assertEquals(100,GearUpgrades.cost(proposal.plan(),p,prices).coins());
    }
    @Test void ownedSilexReducesPurchasesWithoutMutatingInventory() {
        Item i=drill();i.enchantments.put("efficiency",8);var p=profile(i);p.owned.put("SIL_EX",1);
        var proposal=GearUpgrades.forItem(i,MiningMethod.COAL,p,Map.of()).stream().filter(c->c.label().equals("Efficiency 8 -> 10")).findFirst().orElseThrow();
        var cost=GearUpgrades.cost(proposal.plan(),p,Map.of("SIL_EX",new Price(500,"Bazaar")));
        assertEquals(500,cost.coins());assertEquals(40,proposal.delta().get(Stat.MINING_SPEED));assertEquals(1,p.owned.get("SIL_EX"));
    }
    @Test void missingMaterialQuoteMakesWholePlanUnknown() {
        var plan=new GearUpgrades.Plan(Map.of("GEMSTONE_CHAMBER",1,"PERFECT_JADE_GEM",1),50000,"test");
        assertNull(GearUpgrades.cost(plan,new Profile(),Map.of("GEMSTONE_CHAMBER",new Price(1,"Bazaar"))).coins());
    }
    @Test void gemUpgradeIncludesRemovalFeeAndUsesEffectiveRarity() {
        Item i=drill();i.tier="MYTHIC";i.slots.add(jadeSlot(true));
        i.extra=JsonParser.parseString("{\"gems\":{\"JADE_0\":{\"quality\":\"FLAWLESS\"}}}").getAsJsonObject();
        Profile p=profile(i);p.owned.put("PERFECT_JADE_GEM",1);
        var proposal=GearUpgrades.forItem(i,MiningMethod.MF_GEMS,p,Map.of()).stream().filter(c->c.label().contains("PERFECT JADE")).findFirst().orElseThrow();
        assertEquals(15,proposal.delta().get(Stat.MINING_FORTUNE));
        assertEquals(100000,GearUpgrades.cost(proposal.plan(),p,Map.of()).coins());
        assertFalse(proposal.plan().materials().containsKey("GEMSTONE_CHAMBER"));
    }
    @Test void emptyLockedSocketUsesOwnedChamber() {
        Item i=drill();i.slots.add(jadeSlot(true));Profile p=profile(i);p.owned.put("GEMSTONE_CHAMBER",1);
        var proposal=GearUpgrades.forItem(i,MiningMethod.MF_GEMS,p,Map.of()).stream().filter(c->c.label().contains("PERFECT JADE")).findFirst().orElseThrow();
        assertEquals(40,proposal.delta().get(Stat.MINING_FORTUNE));
        assertEquals(123,GearUpgrades.cost(proposal.plan(),p,Map.of("PERFECT_JADE_GEM",new Price(123,"Bazaar"))).coins());
    }
    @Test void accessoryGemStatsAreHalved() {
        assertEquals(25,GearUpgrades.gemValue("JADE","PERFECT","MYTHIC",true));
        assertEquals(1,GearUpgrades.gemValue("TOPAZ","PERFECT","MYTHIC",true));
        assertEquals(2.2,GearUpgrades.gemValue("TOPAZ","PERFECT","DIVINE",false));
    }
    @Test void maxedGemAndUnknownQualityAreNotOfferedAsEmpty() {
        Item i=drill();i.slots.add(jadeSlot(false));
        for(String quality:List.of("PERFECT","NEW_UNSUPPORTED_QUALITY")) {
            i.extra=JsonParser.parseString("{\"gems\":{\"JADE_0\":\""+quality+"\"}}").getAsJsonObject();
            assertTrue(GearUpgrades.forItem(i,MiningMethod.MF_GEMS,profile(i),Map.of()).stream().noneMatch(c->c.label().contains("JADE")));
        }
    }
    @Test void polarvoidFortuneIsGrantedOnce() {
        Item i=drill();i.extra.addProperty("polarvoid",1);
        var proposal=GearUpgrades.forItem(i,MiningMethod.COAL,profile(i),Map.of()).stream().filter(c->c.label().equals("Polarvoid Books 1 -> 5")).findFirst().orElseThrow();
        assertFalse(proposal.delta().containsKey(Stat.MINING_FORTUNE));assertEquals(40,proposal.delta().get(Stat.MINING_SPEED));
    }
}
