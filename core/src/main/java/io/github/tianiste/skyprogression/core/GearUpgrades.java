package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
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
    public record Proposal(Item gear,String label,Map<Stat,Double> delta,Plan plan,String blocked) {}

    public static Cost cost(Plan plan,Profile profile,Map<String,Price> prices) {
        double sum=plan.fees; boolean known=true,allOwned=true;
        StringJoiner detail=new StringJoiner("; ");
        for(var e:plan.materials.entrySet()) {
            int owned=Math.min(e.getValue(),Math.max(0,profile.owned.getOrDefault(e.getKey(),0)));
            int buy=e.getValue()-owned;allOwned &= buy==0;
            detail.add(e.getKey()+": "+owned+" owned, "+buy+" to buy");
            if(buy>0) {
                Price p=prices.get(e.getKey());
                if(p==null||!p.available||p.buy<=0||!Double.isFinite(p.buy))known=false;
                else sum+=p.buy*buy;
            }
        }
        if(plan.fees>0)detail.add("Fees: "+plan.fees+" coins");
        return new Cost(known?Double.valueOf(sum):null,allOwned,detail+". "+plan.instruction);
    }
    public static List<Proposal> forItem(Item gear,MiningMethod method,Profile profile,Map<String,Price> prices) {
        if(gear.instance.isBlank())return List.of(); // Cannot infer modifiers from item ID alone.
        List<Proposal> out=new ArrayList<>();
        if(gear.group.equals("Tool")) {
            int fortune=gear.enchantments.getOrDefault("fortune",0);
            if(fortune<4 && !gear.enchantments.containsKey("silk_touch")) {
                int current=fortune>=1&&fortune<=3?fortune*10:0;
                out.add(new Proposal(gear,"Fortune "+fortune+" -> IV",Map.of(Stat.MINING_FORTUNE,(double)(45-current)),
                        new Plan(Map.of("ENCHANTMENT_FORTUNE_4",1),0,"Apply at an anvil; XP cost is not priced."),""));
            }
            int efficiency=gear.enchantments.getOrDefault("efficiency",0);
            if(efficiency<5)out.add(new Proposal(gear,"Efficiency "+efficiency+" -> V",Map.of(Stat.MINING_SPEED,(double)(110-(efficiency==0?0:efficiency*20+10))),
                    new Plan(Map.of("ENCHANTMENT_EFFICIENCY_5",1),0,"Apply at an anvil; XP cost is not priced."),""));
            if(efficiency>=5&&efficiency<10&&!gear.id.equals("STONK_PICKAXE")) for(int level=efficiency+1;level<=10;level++)
                out.add(new Proposal(gear,"Efficiency "+efficiency+" -> "+level,Map.of(Stat.MINING_SPEED,20.0*(level-efficiency)),
                        new Plan(Map.of("SIL_EX",level-efficiency),0,"Apply Silex sequentially; alternatives are not cumulative."),""));
            if(method.gems()) {
                int pristine=gear.enchantments.getOrDefault("pristine",0);
                for(int level=pristine+1;level<=5;level++) {
                    Plan direct=new Plan(Map.of("ENCHANTMENT_PRISTINE_"+level,1),0,"Prismatic book; apply at an anvil. XP cost is not priced.");
                    if(pristine>0) {
                        Plan combine=new Plan(Map.of("ENCHANTMENT_PRISTINE_"+pristine,(1<<(level-pristine))-1),0,"Combine books, then combine with the existing enchantment. XP cost is not priced.");
                        Cost a=cost(direct,profile,prices),b=cost(combine,profile,prices);
                        if(b.coins!=null&&(a.coins==null||b.coins<a.coins))direct=combine;
                    }
                    out.add(new Proposal(gear,"Prismatic "+pristine+" -> "+level,Map.of(Stat.PRISTINE,.5*(level-pristine)),direct,
                            profile.enchantingLevel<16?"Requires Enchanting 16"+(profile.enchantingLevel<0?" (API level unknown)":""):""));
                }
            }
            if("DRILL".equals(gear.category)) {
                int books=(int)number(at(gear.extra,"polarvoid"),0);
                if(books<5)for(int n=books+1;n<=5;n++) {
                    Map<Stat,Double> delta=new EnumMap<>(Stat.class);delta.put(Stat.MINING_SPEED,10.0*(n-books));
                    if(books==0)delta.put(Stat.MINING_FORTUNE,5.0);
                    out.add(new Proposal(gear,"Polarvoid Books "+books+" -> "+n,delta,new Plan(Map.of("POLARVOID_BOOK",n-books),0,"Apply to this drill; Fortune bonus applies only to the first book."),""));
                }
            }
        }
        gemstones(gear,method,out);
        return out;
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
                    // Preserve the gem type in occupied slots; swapping mixed slots needs a full build comparison.
                    if(!quality.isEmpty()&&(!type.equals(oldType)||QUALITIES.indexOf(target)<=QUALITIES.indexOf(quality)))continue;
                    Map<Stat,Double> delta=new EnumMap<>(Stat.class);
                    double before=quality.isEmpty()?0:gemValue(oldType,quality,gear.tier,"ACCESSORY".equals(gear.category));
                    double after=gemValue(type,target,gear.tier,"ACCESSORY".equals(gear.category));
                    if(!Double.isFinite(before)||!Double.isFinite(after)||after<=before)continue;
                    delta.put(gemStat(type),after-before);
                    Map<String,Integer> materials=new LinkedHashMap<>();materials.put(target+"_"+type+"_GEM",1);
                    double fee=quality.isEmpty()?0:REMOVAL[QUALITIES.indexOf(quality)];
                    if(!unlocked&&slot.costs!=null)for(Ingredient ingredient:slot.costs) {
                        if(ingredient.type.equals("COINS"))fee+=ingredient.coins;
                        else if(ingredient.type.equals("ITEM"))materials.merge(ingredient.item_id,ingredient.amount,Integer::sum);
                    }
                    out.add(new Proposal(gear,(unlocked?"Socket ":"Unlock + socket ")+target+" "+type+" ["+key+"]",delta,
                            new Plan(materials,fee,"Gemstone Grinder. Includes unlock/removal fees; no assumed resale credit."),""));
                }
            }
        }
    }
    public static Set<String> priceIds(List<Item> catalog) {
        Set<String> ids=new LinkedHashSet<>(List.of("ENCHANTMENT_FORTUNE_4","ENCHANTMENT_EFFICIENCY_5","SIL_EX","POLARVOID_BOOK"));
        for(int n=1;n<=5;n++)ids.add("ENCHANTMENT_PRISTINE_"+n);
        for(String type:List.of("JADE","AMBER","TOPAZ"))for(String quality:List.of("FLAWLESS","PERFECT"))ids.add(quality+"_"+type+"_GEM");
        for(String type:List.of("RUBY","SAPPHIRE","AMETHYST"))ids.add("FLAWLESS_"+type+"_GEM"); // MF gem rate values
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
