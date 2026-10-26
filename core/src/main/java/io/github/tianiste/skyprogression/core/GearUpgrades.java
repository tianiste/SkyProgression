package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/** In-place upgrades: costs consume owned materials first, independently for each alternative. */
public final class GearUpgrades {
    private static final List<String> TIERS=List.of("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC","DIVINE");
    private static final List<String> QUALITIES=List.of("ROUGH","FLAWED","FINE","FLAWLESS","PERFECT");
    private static final double[][] JADE={{2,4,6,8,10,12,14},{3,5,7,10,14,18,22},{5,7,10,15,20,25,30},{7,10,15,20,27,35,44},{10,14,20,30,40,50,60}};
    private static final double[][] AMBER={{4,8,12,16,20,24,28},{6,10,14,18,24,30,36},{10,14,20,28,36,45,54},{14,20,30,44,58,75,92},{20,28,40,60,80,100,120}};
    private static final double[] REMOVAL={1,100,10000,100000,500000};
    public record Plan(Map<String,Integer> materials,double fees,String instruction) {}
    public record Cost(Double coins,boolean materialsOwned,String detail) {}
    /** cooldownFactor: new / old pickaxe ability cooldown (fuel tanks); 1 when the ability is unchanged. */
    /**
     * One in-place upgrade. cooldownFactor: new / old pickaxe ability cooldown (fuel tanks), 1 when unchanged.
     * apply records the upgrade on a copy of the gear (enchantments, parts, gems) so a sequential plan can propose
     * the next step from the new state; stat totals are updated by the caller.
     */
    /** returned: item ID given back to the player by the swap (old drill part, removed gem), or "" when none. */
    public record Proposal(Item gear,String label,Map<Stat,Double> delta,Plan plan,String blocked,double cooldownFactor,Consumer<Item> apply,String returned) {
        public Proposal(Item gear,String label,Map<Stat,Double> delta,Plan plan,String blocked,double cooldownFactor,Consumer<Item> apply){this(gear,label,delta,plan,blocked,cooldownFactor,apply,"");}
        public Proposal(Item gear,String label,Map<Stat,Double> delta,Plan plan,String blocked,Consumer<Item> apply){this(gear,label,delta,plan,blocked,1,apply,"");}
    }
    /** Fuel tanks by drill-part key and Pickaxe Ability Cooldown reduction (wiki Drill Parts); only one tank fits a drill. */
    public static final Map<String,Double> FUEL_TANKS=new LinkedHashMap<>();
    /** Drill engines by part key: Mining Speed, Mining Fortune (wiki Drill Parts). */
    public static final Map<String,double[]> ENGINES=new LinkedHashMap<>();
    /** Upgrade modules with mining stats: Mining Speed, Mining Fortune (wiki Drill Parts). Starfall's near-star doubling is not counted. */
    public static final Map<String,double[]> MODULES=new LinkedHashMap<>();
    static {
        FUEL_TANKS.put("mithril_fuel_tank",.02);FUEL_TANKS.put("titanium_fuel_tank",.04);
        FUEL_TANKS.put("gemstone_fuel_tank",.06);FUEL_TANKS.put("perfectly_cut_fuel_tank",.10);
        ENGINES.put("mithril_drill_engine",new double[]{75,5});ENGINES.put("titanium_drill_engine",new double[]{150,15});
        ENGINES.put("ruby_polished_drill_engine",new double[]{250,30});ENGINES.put("sapphire_polished_drill_engine",new double[]{400,50});
        ENGINES.put("amber_polished_drill_engine",new double[]{600,100});
        MODULES.put("goblin_omelette_sunny_side",new double[]{0,50});MODULES.put("starfall_seasoning",new double[]{25,10});
    }
    private static final Map<String,String> PART_NAMES=Map.ofEntries(
            Map.entry("mithril_fuel_tank","Mithril-Infused Fuel Tank"),Map.entry("titanium_fuel_tank","Titanium-Infused Fuel Tank"),
            Map.entry("gemstone_fuel_tank","Gemstone Fuel Tank"),Map.entry("perfectly_cut_fuel_tank","Perfectly-Cut Fuel Tank"),
            Map.entry("mithril_drill_engine","Mithril-Plated Drill Engine"),Map.entry("titanium_drill_engine","Titanium-Plated Drill Engine"),
            Map.entry("ruby_polished_drill_engine","Ruby-Polished Drill Engine"),Map.entry("sapphire_polished_drill_engine","Sapphire-Polished Drill Engine"),
            Map.entry("amber_polished_drill_engine","Amber-Polished Drill Engine"),Map.entry("goblin_omelette_sunny_side","Sunny Side Goblin Omelette"),
            Map.entry("starfall_seasoning","Starfall Seasoning"),Map.entry("goblin_omelette","Goblin Omelette"),
            Map.entry("goblin_omelette_pesto","Pesto Goblin Omelette"),Map.entry("goblin_omelette_spicy","Spicy Goblin Omelette"),
            Map.entry("goblin_omelette_blue_cheese","Blue Cheese Goblin Omelette"));
    static final String BLUE_CHEESE="goblin_omelette_blue_cheese";
    static String partName(String key){return key.isEmpty()?"none":PART_NAMES.getOrDefault(key,key);}

    public static Cost cost(Plan plan,Profile profile,Map<String,Price> prices) {
        double sum=plan.fees; boolean known=true,allOwned=true;
        StringJoiner detail=new StringJoiner("; ");
        for(var e:plan.materials.entrySet()) {
            int owned=Math.min(e.getValue(),Math.max(0,profile.owned.getOrDefault(e.getKey(),0)));
            int buy=e.getValue()-owned;allOwned &= buy==0;
            detail.add(e.getKey()+": "+owned+" owned, "+buy+" to get");
            if(buy>0) {
                Recipes.Path path=Recipes.acquire(e.getKey(),buy,profile,prices);
                if(path.coins()==null)known=false;
                else sum+=path.coins();
                if(!path.detail().isEmpty())detail.add(path.detail().replaceAll("\\.$",""));
            }
        }
        if(plan.fees>0)detail.add("Fees: "+plan.fees+" coins");
        return new Cost(known?Double.valueOf(sum):null,allOwned,detail+". "+plan.instruction);
    }
    public static List<Proposal> forItem(Item gear,MiningMethod method,Profile profile,Map<String,Price> prices) {return forItem(gear,method,profile,prices,Map.of());}
    /** omelette: mining stats the Blue Cheese Goblin Omelette's +1 HOTM perk levels add (ProfileUpgrades.State.omeletteGain). */
    public static List<Proposal> forItem(Item gear,MiningMethod method,Profile profile,Map<String,Price> prices,Map<Stat,Double> omelette) {
        if(gear.instance.isBlank())return List.of(); // Cannot infer modifiers from item ID alone.
        List<Proposal> out=new ArrayList<>();
        if(gear.group.equals("Tool")) {
            int fortune=gear.enchantments.getOrDefault("fortune",0);
            if(fortune<4 && !gear.enchantments.containsKey("silk_touch")) {
                int current=fortune>=1&&fortune<=3?fortune*10:0;
                out.add(new Proposal(gear,"Fortune "+fortune+" -> IV",Map.of(Stat.MINING_FORTUNE,(double)(45-current)),
                        new Plan(Map.of("ENCHANTMENT_FORTUNE_4",1),0,"Apply at an anvil; XP cost is not priced."),"",g->g.enchantments.put("fortune",4)));
            }
            int efficiency=gear.enchantments.getOrDefault("efficiency",0);
            if(efficiency<5)out.add(new Proposal(gear,"Efficiency "+efficiency+" -> V",Map.of(Stat.MINING_SPEED,(double)(110-(efficiency==0?0:efficiency*20+10))),
                    new Plan(Map.of("ENCHANTMENT_EFFICIENCY_5",1),0,"Apply at an anvil; XP cost is not priced."),"",g->g.enchantments.put("efficiency",5)));
            if(efficiency>=5&&efficiency<10&&!gear.id.equals("STONK_PICKAXE")) for(int level=efficiency+1;level<=10;level++) {
                int to=level;
                out.add(new Proposal(gear,"Efficiency "+efficiency+" -> "+level,Map.of(Stat.MINING_SPEED,20.0*(level-efficiency)),
                        new Plan(Map.of("SIL_EX",level-efficiency),0,"Apply Silex sequentially; alternatives are not cumulative."),"",g->g.enchantments.put("efficiency",to)));
            }
            if(method.gems()) {
                int pristine=gear.enchantments.getOrDefault("pristine",0);
                for(int level=pristine+1;level<=5;level++) {
                    int to=level;
                    Plan direct=new Plan(Map.of("ENCHANTMENT_PRISTINE_"+level,1),0,"Prismatic book; apply at an anvil. XP cost is not priced.");
                    if(pristine>0) {
                        Plan combine=new Plan(Map.of("ENCHANTMENT_PRISTINE_"+pristine,(1<<(level-pristine))-1),0,"Combine books, then combine with the existing enchantment. XP cost is not priced.");
                        Cost a=cost(direct,profile,prices),b=cost(combine,profile,prices);
                        if(b.coins!=null&&(a.coins==null||b.coins<a.coins))direct=combine;
                    }
                    out.add(new Proposal(gear,"Prismatic "+pristine+" -> "+level,Map.of(Stat.PRISTINE,.5*(level-pristine)),direct,
                            profile.enchantingLevel<16?"Requires Enchanting 16"+(profile.enchantingLevel<0?" (API level unknown)":""):"",g->g.enchantments.put("pristine",to)));
                }
            }
            if("DRILL".equals(gear.category)) {
                int books=(int)number(at(gear.extra,"polarvoid"),0);
                if(books<5)for(int n=books+1;n<=5;n++) {
                    int to=n;
                    Map<Stat,Double> delta=new EnumMap<>(Stat.class);delta.put(Stat.MINING_SPEED,10.0*(n-books));
                    if(books==0)delta.put(Stat.MINING_FORTUNE,5.0);
                    out.add(new Proposal(gear,"Polarvoid Books "+books+" -> "+n,delta,new Plan(Map.of("POLARVOID_BOOK",n-books),0,"Apply to this drill; Fortune bonus applies only to the first book."),"",
                            g->g.extra.addProperty("polarvoid",to)));
                }
                String tank=string(at(gear.extra,"drill_part_fuel_tank"),"");
                double old=FUEL_TANKS.getOrDefault(tank,0.0);
                for(var e:FUEL_TANKS.entrySet())if(e.getValue()>old)
                    out.add(new Proposal(gear,partName(e.getKey())+" (-"+Math.round(e.getValue()*100)+"% ability cooldown)",Map.of(),
                            part(e.getKey(),tank),"",(1-e.getValue())/(1-old),g->g.extra.addProperty("drill_part_fuel_tank",e.getKey()),tank.toUpperCase(Locale.ROOT)));
                parts(gear,"drill_part_engine",ENGINES,out,Map.of());
                String module=string(at(gear.extra,"drill_part_upgrade_module"),"");
                parts(gear,"drill_part_upgrade_module",MODULES,out,module.equals(BLUE_CHEESE)?omelette:Map.of());
                if(!module.equals(BLUE_CHEESE)&&!omelette.isEmpty()) {
                    Map<Stat,Double> delta=new EnumMap<>(omelette);
                    double[] now=MODULES.getOrDefault(module,new double[2]);
                    delta.merge(Stat.MINING_SPEED,-now[0],Double::sum);delta.merge(Stat.MINING_FORTUNE,-now[1],Double::sum);
                    delta.values().removeIf(v->v==0);
                    if(delta.values().stream().anyMatch(v->v>0))out.add(new Proposal(gear,partName(BLUE_CHEESE)+" (+1 HOTM perk levels)",delta,part(BLUE_CHEESE,module),"",1,
                            g->g.extra.addProperty("drill_part_upgrade_module",BLUE_CHEESE),module.toUpperCase(Locale.ROOT)));
                }
            }
        }
        reforges(gear,method,out);
        gemstones(gear,method,out);
        recombobulate(gear,method,out);
        coating(gear,out);
        return out;
    }
    private static final Set<String> DIVAN_COATED=Set.of("DIVAN_HELMET","DIVAN_CHESTPLATE","DIVAN_LEGGINGS","DIVAN_BOOTS","DIVAN_PENDANT");
    /** Divan's Powder Coating (item lore): +500 Mining Speed on a drill, +10 Mining Fortune on Armor/Pendant of Divan; once per item. */
    private static void coating(Item gear,List<Proposal> out) {
        if(number(at(gear.extra,"divan_powder_coating"),0)>0)return;
        Map<Stat,Double> delta="DRILL".equals(gear.category)?Map.of(Stat.MINING_SPEED,500.0):DIVAN_COATED.contains(gear.id)?Map.of(Stat.MINING_FORTUNE,10.0):null;
        if(delta==null)return;
        out.add(new Proposal(gear,"Divan's Powder Coating",delta,new Plan(Map.of("DIVAN_POWDER_COATING",1),0,"Combine in an anvil; once per item."),"",
                g->g.extra.addProperty("divan_powder_coating",1)));
    }
    /**
     * Recombobulator 3000: one rarity up, once per item. Base stats do not change; the reforge and socketed gems take
     * their values at the new rarity.
     */
    private static void recombobulate(Item gear,MiningMethod method,List<Proposal> out) {
        int index=TIERS.indexOf(gear.tier);
        if(gear.recombobulated||number(at(gear.extra,"rarity_upgrades"),0)>0||index<0||index>=TIERS.size()-1)return;
        String next=TIERS.get(index+1);
        Map<Stat,Double> delta=new EnumMap<>(Stat.class);
        Reforges.Reforge reforge=Reforges.byModifier(gear.modifier);
        if(reforge!=null) {
            Map<Stat,Double> now=reforge.at(gear.tier,method),then=reforge.at(next,method);
            if(now==null||then==null)return;
            for(Stat s:Stat.values())delta.merge(s,then.getOrDefault(s,0.0)-now.getOrDefault(s,0.0),Double::sum);
        }
        Map<Stat,Double> gemsNow=socketed(gear,gear.tier),gemsThen=socketed(gear,next);
        if(gemsNow==null||gemsThen==null)return;
        for(Stat s:Stat.values())delta.merge(s,gemsThen.getOrDefault(s,0.0)-gemsNow.getOrDefault(s,0.0),Double::sum);
        delta.values().removeIf(v->v==0);
        if(delta.values().stream().noneMatch(v->v>0))return;
        out.add(new Proposal(gear,"Recombobulator 3000 ("+next.toLowerCase(Locale.ROOT)+")",delta,
                new Plan(Map.of("RECOMBOBULATOR_3000",1),0,"Combine in an anvil; once per item. Only reforge and gem values change."),"",
                g->{g.tier=next;g.recombobulated=true;g.extra.addProperty("rarity_upgrades",1);}));
    }
    /** Mining stats of the socketed gems at a rarity, or null when a gem's quality is not recognised. */
    private static Map<Stat,Double> socketed(Item gear,String tier) {
        Map<Stat,Double> out=new EnumMap<>(Stat.class);
        if(gear.slots==null)return out;
        JsonElement gems=at(gear.extra,"gems");
        Map<String,Integer> indexes=new HashMap<>();
        for(Slot slot:gear.slots) {
            if(slot==null)continue;
            String key=slot.slot_type+"_"+(indexes.merge(slot.slot_type,1,Integer::sum)-1);
            JsonElement gem=at(gems,key);
            String quality=gem.isJsonObject()?string(at(gem,"quality"),""):string(gem,"");
            if(quality.isEmpty())continue;
            String type=string(at(gems,key+"_gem"),slot.slot_type);
            if(gemStat(type)==null)continue;
            double value=gemValue(type,quality,tier,"ACCESSORY".equals(gear.category));
            if(!Double.isFinite(value))return null;
            out.merge(gemStat(type),value,Double::sum);
        }
        return out;
    }
    /**
     * Reforge-stone swaps at the item's effective rarity: new reforge stats minus the current reforge's (unknown or
     * non-mining reforges count as zero). Cost is the stone plus the Blacksmith fee; abilities are mostly not counted.
     */
    private static void reforges(Item gear,MiningMethod method,List<Proposal> out) {
        if(!TIERS.contains(gear.tier))return;
        Reforges.Reforge current=Reforges.byModifier(gear.modifier);
        Map<Stat,Double> now=current==null?Map.of():current.at(gear.tier,method);
        if(now==null)return;
        for(Reforges.Reforge r:Reforges.all()) {
            if(r.stone()==null||r==current||!r.fits(gear))continue;
            Map<Stat,Double> next=r.at(gear.tier,method);
            if(next==null)continue;
            Map<Stat,Double> delta=new EnumMap<>(Stat.class);
            for(Stat s:Stat.values()) {
                double d=next.getOrDefault(s,0.0)-now.getOrDefault(s,0.0);
                if(d!=0)delta.put(s,d);
            }
            if(delta.values().stream().noneMatch(v->v>0))continue;
            String name=r.name().toLowerCase(Locale.ROOT);
            out.add(new Proposal(gear,r.name()+" reforge"+(gear.modifier.isBlank()?"":" (now "+gear.modifier.substring(0,1).toUpperCase(Locale.ROOT)+gear.modifier.substring(1)+")"),delta,
                    new Plan(Map.of(r.stone(),1),r.costs().getOrDefault(gear.tier,0.0),"Reforge Anvil; the fee is for "+gear.tier.toLowerCase(Locale.ROOT)+"."
                            +(r.ability().isEmpty()||r.name().equals("Fleet")?"":" Ability not counted: "+r.ability())),"",g->g.modifier=name));
        }
    }
    /** Records a socketed gem the way the API stores it: quality under the slot key, the type for flexible slots, unlocked. */
    private static void socket(Item g,String key,String quality,String type,boolean flexible) {
        JsonElement gems=g.extra.get("gems");
        JsonObject o=gems!=null&&gems.isJsonObject()?gems.getAsJsonObject():new JsonObject();
        g.extra.add("gems",o);o.addProperty(key,quality);
        if(flexible)o.addProperty(key+"_gem",type);
        JsonArray unlocked=o.has("unlocked_slots")&&o.get("unlocked_slots").isJsonArray()?o.getAsJsonArray("unlocked_slots"):new JsonArray();
        if(!unlocked.contains(new JsonPrimitive(key)))unlocked.add(key);
        o.add("unlocked_slots",unlocked);
    }
    /** Fitting a part; the current part of that kind must be removed first for 9,999 coins and is given back (wiki part pages). */
    private static Plan part(String key,String current) {
        return new Plan(Map.of(key.toUpperCase(Locale.ROOT),1),current.isEmpty()?0:9999,
                "Apply at the drill anvil."+(current.isEmpty()?"":" Removing the "+partName(current)+" costs 9,999 coins; you keep it."));
    }
    /** Engine or module swaps that add Mining Speed or Fortune over the current part of that kind (one per drill). */
    /** lost: stats the current part gives outside the table (the Blue Cheese omelette's perk levels), lost on a swap. */
    private static void parts(Item gear,String field,Map<String,double[]> table,List<Proposal> out,Map<Stat,Double> lost) {
        String current=string(at(gear.extra,field),"");
        double[] now=table.getOrDefault(current,new double[2]);
        for(var e:table.entrySet()) {
            Map<Stat,Double> delta=new EnumMap<>(Stat.class);
            delta.put(Stat.MINING_SPEED,e.getValue()[0]-now[0]);delta.put(Stat.MINING_FORTUNE,e.getValue()[1]-now[1]);
            lost.forEach((s,v)->delta.merge(s,-v,Double::sum));
            delta.values().removeIf(v->v==0);
            if(e.getKey().equals(current)||delta.values().stream().noneMatch(v->v>0))continue;
            out.add(new Proposal(gear,partName(e.getKey()),delta,part(e.getKey(),current),"",1,g->g.extra.addProperty(field,e.getKey()),current.toUpperCase(Locale.ROOT)));
        }
    }
    public static double gemValue(String type,String quality,String tier,boolean accessory) {
        int q=QUALITIES.indexOf(quality),r=TIERS.indexOf(tier);
        if(q<0||r<0)return Double.NaN;
        double value=switch(type) {
            case "JADE" -> JADE[q][r];
            case "AMBER" -> AMBER[q][r];
            case "TOPAZ" -> r==6?new double[]{.5,.9,1.3,1.8,2.2}[q]:.4*(q+1);
            default -> 0;
        };
        return value*(accessory?.5:1);
    }
    private static Stat gemStat(String type) {
        return switch(type){case "JADE"->Stat.MINING_FORTUNE;case "AMBER"->Stat.MINING_SPEED;case "TOPAZ"->Stat.PRISTINE;default->null;};
    }
    private static void gemstones(Item gear,MiningMethod method,List<Proposal> out) {
        if(gear.slots==null||!TIERS.contains(gear.tier))return;
        JsonElement gems=at(gear.extra,"gems");
        Map<String,Integer> indexes=new HashMap<>();
        for(Slot slot:gear.slots) {
            if(slot==null)continue;
            int index=indexes.merge(slot.slot_type,1,Integer::sum)-1;
            String key=slot.slot_type+"_"+index;
            JsonElement gem=at(gems,key);
            String quality=gem.isJsonObject()?string(at(gem,"quality"),""):string(gem,"");
            String oldType=string(at(gems,key+"_gem"),slot.slot_type);
            if(!quality.isEmpty()&&!QUALITIES.contains(quality))continue;
            boolean unlocked=!quality.isEmpty()||slot.costs==null||slot.costs.isEmpty();
            JsonElement unlockedSlots=at(gems,"unlocked_slots");
            if(unlockedSlots.isJsonArray())for(JsonElement e:unlockedSlots.getAsJsonArray())if(key.equals(string(e,"")))unlocked=true;
            List<String> types=List.of("MINING","UNIVERSAL").contains(slot.slot_type)?List.of("JADE","AMBER","TOPAZ"):List.of(slot.slot_type);
            for(String type:types) {
                if(gemStat(type)==null || type.equals("TOPAZ")&&!method.gems())continue;
                for(String target:List.of("FLAWLESS","PERFECT")) {
                    // Same type: only higher qualities. Another type (flexible slots): the old gem's stat is lost.
                    boolean swap=!quality.isEmpty()&&!type.equals(oldType);
                    if(!quality.isEmpty()&&!swap&&QUALITIES.indexOf(target)<=QUALITIES.indexOf(quality))continue;
                    Map<Stat,Double> delta=new EnumMap<>(Stat.class);
                    double before=quality.isEmpty()||gemStat(oldType)==null?0:gemValue(oldType,quality,gear.tier,"ACCESSORY".equals(gear.category));
                    double after=gemValue(type,target,gear.tier,"ACCESSORY".equals(gear.category));
                    if(!Double.isFinite(before)||!Double.isFinite(after)||(swap?after<=0:after<=before))continue;
                    if(swap){delta.put(gemStat(type),after);if(gemStat(oldType)!=null&&before>0)delta.merge(gemStat(oldType),-before,Double::sum);}
                    else delta.put(gemStat(type),after-before);
                    Map<String,Integer> materials=new LinkedHashMap<>();materials.put(target+"_"+type+"_GEM",1);
                    double fee=quality.isEmpty()?0:REMOVAL[QUALITIES.indexOf(quality)];
                    if(!unlocked&&slot.costs!=null)for(Ingredient ingredient:slot.costs) {
                        if(ingredient.type.equals("COINS"))fee+=ingredient.coins;
                        else if(ingredient.type.equals("ITEM"))materials.merge(ingredient.item_id,ingredient.amount,Integer::sum);
                    }
                    out.add(new Proposal(gear,(unlocked?"Socket ":"Unlock + socket ")+target+" "+type+" ["+key+"]"+(swap?" (replaces "+quality+" "+oldType+")":""),delta,
                            new Plan(materials,fee,"Gemstone Grinder. Includes unlock/removal fees"+(quality.isEmpty()?".":"; the removed gem is given back.")),"",1,
                            g->socket(g,key,target,type,!type.equals(slot.slot_type)),quality.isEmpty()?"":quality+"_"+oldType+"_GEM"));
                }
            }
        }
    }
    public static Set<String> priceIds(List<Item> catalog) {
        Set<String> ids=new LinkedHashSet<>(List.of("ENCHANTMENT_FORTUNE_4","ENCHANTMENT_EFFICIENCY_5","SIL_EX","POLARVOID_BOOK","RECOMBOBULATOR_3000","DIVAN_POWDER_COATING","GOBLIN_OMELETTE_BLUE_CHEESE"));
        for(var table:List.of(FUEL_TANKS.keySet(),ENGINES.keySet(),MODULES.keySet()))for(String key:table)ids.add(key.toUpperCase(Locale.ROOT));
        for(int n=1;n<=5;n++)ids.add("ENCHANTMENT_PRISTINE_"+n);
        for(Reforges.Reforge r:Reforges.all())if(r.stone()!=null)ids.add(r.stone());
        ids.addAll(PetUpgrades.priceIds());
        ids.addAll(ProfileUpgrades.priceIds());
        ids.addAll(Recipes.inputIds());
        ids.addAll(MethodRates.priceIds());
        for(String type:List.of("JADE","AMBER","TOPAZ"))for(String quality:List.of("FLAWLESS","PERFECT"))ids.add(quality+"_"+type+"_GEM");
        for(String type:List.of("RUBY","SAPPHIRE","AMETHYST","JASPER"))ids.add("FLAWLESS_"+type+"_GEM"); // gem rate values
        for(Item item:catalog) {
            ids.add(item.id);
            if(item.slots!=null)for(Slot slot:item.slots)if(slot!=null&&slot.costs!=null)for(Ingredient c:slot.costs)if(c.type.equals("ITEM"))ids.add(c.item_id);
        }
        return ids;
    }
    public static List<Milestone> progress(Item gear,MiningMethod method,Stat focus) {
        if(gear.instance.isBlank())return List.of();
        List<Milestone> rows=new ArrayList<>();
        if(gear.group.equals("Tool")) {
            if(focus==Stat.MINING_FORTUNE&&!gear.enchantments.containsKey("silk_touch"))rows.add(new Milestone("Fortune",gear.enchantments.getOrDefault("fortune",0),4,"levels"));
            if(focus==Stat.MINING_SPEED)rows.add(new Milestone("Efficiency",gear.enchantments.getOrDefault("efficiency",0),10,"levels"));
            if(focus==Stat.PRISTINE&&method.gems())rows.add(new Milestone("Prismatic",gear.enchantments.getOrDefault("pristine",0),5,"levels"));
            if("DRILL".equals(gear.category)&&(focus==Stat.MINING_SPEED||focus==Stat.MINING_FORTUNE))
                rows.add(new Milestone("Polarvoid Books",number(at(gear.extra,"polarvoid"),0),5,"books"));
        }
        if(gear.slots!=null) {
            Map<String,Integer> indexes=new HashMap<>();
            for(Slot slot:gear.slots)if(slot!=null) {
                String key=slot.slot_type+"_"+(indexes.merge(slot.slot_type,1,Integer::sum)-1);
                JsonElement gems=at(gear.extra,"gems"),gem=at(gems,key);
                String quality=gem.isJsonObject()?string(at(gem,"quality"),""):string(gem,"");
                String type=string(at(gems,key+"_gem"),slot.slot_type);
                if(gemStat(type)==focus)rows.add(new Milestone(key+" ("+(quality.isEmpty()?"empty":quality)+")",QUALITIES.indexOf(quality)+1,5,"quality tiers"));
            }
        }
        return rows;
    }
}
