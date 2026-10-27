package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/** Keeps each physical copy separate and reads its displayed stat totals once, without adding modifiers twice. */
public final class GearReader {
    private static final Pattern BREAKING_POWER=Pattern.compile("^Breaking Power ([0-9]+)$");
    public static String clean(String text) { return text.replaceAll("(?i)§[0-9A-FK-ORX]", "").strip(); }
    public static Item read(JsonElement nbt, Item definition, String location, int slot) {
        Item gear = Catalog.GSON.fromJson(Catalog.GSON.toJson(definition), Item.class);
        JsonElement attributes = at(nbt,"tag","ExtraAttributes");
        gear.extra = attributes.isJsonObject() ? attributes.deepCopy().getAsJsonObject() : new JsonObject();
        gear.instance = string(at(attributes,"uuid"), location+":"+slot);
        gear.location = location+" #"+slot;
        gear.name = clean(string(at(nbt,"tag","display","Name"), definition.name));
        gear.modifier = string(at(attributes,"modifier"), "");
        gear.recombobulated = number(at(attributes,"rarity_upgrades"),0)>0;
        if (gear.recombobulated) {
            var tiers=List.of("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC","DIVINE");
            int index=tiers.indexOf(Objects.toString(gear.tier,""));
            if(index>=0)gear.tier=tiers.get(Math.min(index+1,tiers.size()-1));
        }
        JsonElement enchants=at(attributes,"enchantments");
        if(enchants.isJsonObject())enchants.getAsJsonObject().entrySet().forEach(e->gear.enchantments.put(e.getKey(),(int)number(e.getValue(),0)));
        JsonElement lore=at(nbt,"tag","display","Lore");
        if(lore.isJsonArray()) for(JsonElement line:lore.getAsJsonArray()) {
            String text=clean(string(line,""));
            gear.lore.add(text);
            // Tools show "Breaking Power 9" without a colon.
            Matcher power=BREAKING_POWER.matcher(text);
            if(power.matches())gear.stats.put(Stat.BREAKING_POWER,Double.parseDouble(power.group(1)));
            for(Stat stat:Stat.values()) {
                Matcher match=Pattern.compile("^"+Pattern.quote(stat.label)+":\\s*([+-]?[0-9,]+(?:\\.[0-9]+)?)(?![0-9.%])").matcher(text);
                if(match.find()) {
                    gear.stats.put(stat,Double.parseDouble(match.group(1).replace(",","")));
                    gear.observed=true;
                }
            }
        }
        return gear;
    }
    public static void accept(JsonElement nbt, Profile profile, List<Item> catalog, String location, int slot) {
        JsonElement attributes=at(nbt,"tag","ExtraAttributes");
        String id=string(at(attributes,"id"),"");
        if(id.isBlank())return;
        String uuid=string(at(attributes,"uuid"),"");
        if(!uuid.isBlank() && !profile.seenUuids.add(uuid))return;
        int count=Math.max(1,(int)number(at(nbt,"Count"),1));
        profile.owned.merge(id,count,Integer::sum);
        if(id.equals("ENCHANTED_BOOK")) {
            JsonElement enchants=at(attributes,"enchantments");
            if(enchants.isJsonObject()) for(var e:enchants.getAsJsonObject().entrySet()) {
                String key="ENCHANTMENT_"+e.getKey().toUpperCase(Locale.ROOT)+"_"+(int)number(e.getValue(),0);
                profile.owned.merge(key,count,Integer::sum);
            }
        }
        catalog.stream().filter(i->i.id.equals(id)).findFirst().ifPresent(i->profile.gear.add(read(nbt,i,location,slot)));
    }
}
