package io.github.tianiste.skyprogression.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import io.github.tianiste.skyprogression.core.LiveGear;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.regex.*;

/**
 * Live gear (docs/CALCULATIONS.md "Live gear"): what the game client shows of the player's gear, handed to ClientData as
 * an immutable {@link LiveGear} snapshot whenever it changes. Twice a second it compares the armor and inventory stacks by
 * identity and converts only changed gear stacks (custom data, name, lore) to the inventory API's NBT shape; parsing and
 * every calculation happen on ClientData's worker. The equipment and pets menus are read while open; the tab list's
 * Profile and Pet lines once a second; pet summon messages from chat. Read only, and only in SkyBlock (the SkyBlock menu
 * item is in the inventory).
 */
final class LiveGearWatcher {
    private LiveGearWatcher() {}
    private static final ClientData DATA = ClientData.INSTANCE;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    /** Last lore line of gear: "LEGENDARY DRILL", "a MYTHIC DUNGEON HELMET a" (recombobulated). */
    private static final Pattern GEAR = Pattern.compile("(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|VERY SPECIAL|SPECIAL)(?: DUNGEON)? "
            + "(HELMET|CHESTPLATE|LEGGINGS|BOOTS|NECKLACE|CLOAK|BELT|GLOVES|BRACELET|DRILL|PICKAXE|GAUNTLET|ACCESSORY)\\b");
    private static final Pattern LEVEL = Pattern.compile("^\\[Lvl (\\d+)] ([A-Za-z][A-Za-z ']*[A-Za-z])");
    private static final Pattern SUMMONED = Pattern.compile("^You summoned your ([A-Za-z][A-Za-z ']*[A-Za-z])!?");
    private static final Pattern DESPAWNED = Pattern.compile("^You despawned your ([A-Za-z][A-Za-z ']*[A-Za-z])!?");
    private static final Pattern AUTOPET = Pattern.compile("^Autopet equipped your \\[Lvl (\\d+)] ([A-Za-z][A-Za-z ']*[A-Za-z])");
    private static final Pattern PROFILE = Pattern.compile("^Profile: ([A-Za-z]+)");
    /** Custom data keys that change while the item is used (fuel, counters); they do not change its stats. */
    private static final Pattern VOLATILE = Pattern.compile("fuel|compact_blocks|_xp$|kills|mined|cultivating|runs|timestamp", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> COLOR_TIER = Map.of("white", "COMMON", "green", "UNCOMMON", "blue", "RARE",
            "dark_purple", "EPIC", "gold", "LEGENDARY", "light_purple", "MYTHIC");
    private static final Map<Character, String> CODE_TIER = Map.of('f', "COMMON", 'a', "UNCOMMON", '9', "RARE", '5', "EPIC", '6', "LEGENDARY", 'd', "MYTHIC");

    /** A watched slot: the stack it was read from, its NBT (null = not gear) and its stable signature. */
    private record Read(ItemStack stack, JsonObject nbt, String signature) {}
    private static final Read[] armor = new Read[4], inventory = new Read[36];
    private static final Map<String, LiveGear.Part> equipment = new HashMap<>();
    private static final Map<String, String> equipmentSignature = new HashMap<>();
    private static final Map<String, LiveGear.Pet> pets = new HashMap<>();
    private static String profile = "", lastSignature = "";
    private static int toolSlot = -1, version, ticks;
    private static long toolAt, armorAt, inventoryAt;
    private static Player watched;

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LiveGearWatcher::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) chat(message); });
    }

    private static void tick(Minecraft client) {
        Player player = client.player;
        if (player == null || ++ticks % 10 != 0) return;
        if (player != watched) { Arrays.fill(armor, null); Arrays.fill(inventory, null); watched = player; }
        Inventory inv = player.getInventory();
        if (!inSkyBlock(inv)) return;
        long now = System.currentTimeMillis();
        if (ticks % 20 == 0) tabList(client, now);
        boolean armorChanged = false, inventoryChanged = false;
        for (int i = 0; i < 4; i++) armorChanged |= update(armor, i, player.getItemBySlot(ARMOR[i]));
        var main = inv.getNonEquipmentItems();
        for (int i = 0; i < inventory.length && i < main.size(); i++) inventoryChanged |= update(inventory, i, main.get(i));
        if (armorChanged) armorAt = now;
        if (inventoryChanged) inventoryAt = now;
        int selected = inv.getSelectedSlot();
        Read held = selected >= 0 && selected < 9 ? inventory[selected] : null;
        if (held != null && held.nbt() != null && tool(held.nbt()) && toolSlot != selected) { toolSlot = selected; toolAt = now; }
        if (Compat.screen(client) instanceof AbstractContainerScreen<?> screen) menu(screen, now);
        publish(now);
    }

    /** Re-reads a slot when its stack object changed; true when its gear signature changed. */
    private static boolean update(Read[] reads, int index, ItemStack stack) {
        Read old = reads[index];
        if (old != null && old.stack() == stack) return false;
        JsonObject nbt = gear(stack) ? nbt(stack) : null;
        Read read = new Read(stack, nbt, nbt == null ? "" : signature(nbt));
        reads[index] = read;
        return old == null || !old.signature().equals(read.signature());
    }

    private static void publish(long now) {
        StringBuilder sig = new StringBuilder(profile).append('|').append(toolSlot);
        for (Read r : armor) sig.append('|').append(r == null ? "" : r.signature());
        for (Read r : inventory) sig.append('|').append(r == null ? "" : r.signature());
        sig.append('|').append(equipmentSignature.getOrDefault(profile, "")).append('|').append(pets.get(profile));
        String s = sig.toString();
        if (s.equals(lastSignature)) return;
        lastSignature = s;
        LiveGear snapshot = new LiveGear(part(armor, armorAt), part(inventory, inventoryAt), equipment.get(profile), toolSlot, toolAt, pets.get(profile), ++version);
        DATA.live(snapshot, profile);
    }
    private static LiveGear.Part part(Read[] reads, long at) {
        List<JsonObject> slots = new ArrayList<>();
        for (Read r : reads) slots.add(r == null ? null : r.nbt());
        return new LiveGear.Part(Collections.unmodifiableList(slots), at == 0 ? System.currentTimeMillis() : at);
    }

    /** The equipment menu ("Your Equipment and Stats") and the pets menu ("Pets", "Pets (1/2)"), read while open. */
    private static void menu(AbstractContainerScreen<?> screen, long now) {
        String title = screen.getTitle().getString();
        boolean equipmentMenu = title.contains("Equipment"), petsMenu = title.matches("^Pets(?: \\(\\d+/\\d+\\))?$");
        if (!equipmentMenu && !petsMenu) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (var slot : screen.getMenu().slots) if (!(slot.container instanceof Inventory)) stacks.add(slot.getItem());
        if (stacks.stream().allMatch(ItemStack::isEmpty)) return; // Contents not received yet.
        if (equipmentMenu) {
            List<JsonObject> slots = new ArrayList<>();StringBuilder sig = new StringBuilder();
            for (ItemStack stack : stacks) {
                JsonObject nbt = gear(stack) ? nbt(stack) : null;
                slots.add(nbt);
                if (nbt != null) sig.append(signature(nbt)).append('|');
            }
            if (!sig.toString().equals(equipmentSignature.get(profile))) {
                equipment.put(profile, new LiveGear.Part(Collections.unmodifiableList(slots), now));
                equipmentSignature.put(profile, sig.toString());
            }
            return;
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || lore(stack).stream().noneMatch(l -> l.contains("Click to despawn"))) continue;
            LiveGear.Pet pet = menuPet(stack, now);
            if (pet != null && !samePet(pet, pets.get(profile))) pets.put(profile, pet);
            return;
        }
    }
    private static LiveGear.Pet menuPet(ItemStack stack, long now) {
        String name = strip(stack.getHoverName().getString());
        Matcher m = LEVEL.matcher(name);
        boolean named = m.find();
        int level = named ? Integer.parseInt(m.group(1)) : 0;
        String type = named ? type(m.group(2)) : "", tier = named ? tier(stack.getHoverName(), m.group(2)) : "";
        String held = "";
        for (String line : lore(stack)) if (line.startsWith("Held Item: ")) held = LiveGear.petItemId(line.substring(11));
        // petInfo (custom data, a JSON string) is exact when present.
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        String info = custom == null ? "" : custom.copyTag().getStringOr("petInfo", "");
        if (!info.isEmpty()) try {
            JsonObject json = JsonParser.parseString(info).getAsJsonObject();
            if (json.has("type")) type = json.get("type").getAsString();
            if (json.has("tier")) tier = json.get("tier").getAsString();
            if (json.has("heldItem")) held = json.get("heldItem").getAsString();
        } catch (Exception ignored) { /* Fall back to the name and lore. */ }
        return type.isEmpty() ? null : new LiveGear.Pet(type, tier, level, held, now, "pets menu");
    }

    /** Tab list: "Profile: Name" (which profile the live gear belongs to) and the line after "Pet:". */
    private static void tabList(Minecraft client, long now) {
        var connection = client.getConnection();
        if (connection == null) return;
        List<PlayerInfo> entries = new ArrayList<>(connection.getListedOnlinePlayers());
        entries.sort(Comparator.comparingInt((PlayerInfo p) -> -p.getTabListOrder()).thenComparing(p -> p.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        boolean petNext = false;
        for (PlayerInfo info : entries) {
            Component display = info.getTabListDisplayName();
            if (display == null) continue;
            String text = strip(display.getString());
            Matcher p = PROFILE.matcher(text);
            if (p.find()) profile = p.group(1).toLowerCase(Locale.ROOT);
            if (petNext) {
                petNext = false;
                Matcher m = LEVEL.matcher(text);
                if (m.find()) {
                    LiveGear.Pet pet = new LiveGear.Pet(type(m.group(2)), tier(display, m.group(2)), Integer.parseInt(m.group(1)), null, now, "tab list");
                    if (!samePet(pet, pets.get(profile))) pets.put(profile, merge(pet, pets.get(profile)));
                }
            }
            if (text.equals("Pet:")) petNext = true;
        }
    }

    private static void chat(Component message) {
        String text = strip(message.getString());
        long now = System.currentTimeMillis();
        Matcher m;
        if ((m = AUTOPET.matcher(text)).find())
            pets.put(profile, merge(new LiveGear.Pet(type(m.group(2)), tier(message, m.group(2)), Integer.parseInt(m.group(1)), null, now, "chat"), pets.get(profile)));
        else if ((m = SUMMONED.matcher(text)).find())
            pets.put(profile, merge(new LiveGear.Pet(type(m.group(1)), tier(message, m.group(1)), 0, null, now, "chat"), pets.get(profile)));
        else if (DESPAWNED.matcher(text).find()) pets.put(profile, new LiveGear.Pet("", "", 0, "", now, "chat"));
    }
    /** Keeps the pet item (and level) of the same pet seen earlier, e.g. in the pets menu, when the new source lacks them. */
    private static LiveGear.Pet merge(LiveGear.Pet pet, LiveGear.Pet old) {
        if (old == null || !old.type().equals(pet.type()) || !pet.tier().isEmpty() && !old.tier().isEmpty() && !old.tier().equals(pet.tier())) return pet;
        return new LiveGear.Pet(pet.type(), pet.tier().isEmpty() ? old.tier() : pet.tier(), pet.level() > 0 ? pet.level() : old.level(),
                pet.heldItem() == null ? old.heldItem() : pet.heldItem(), pet.at(), pet.from());
    }
    private static boolean samePet(LiveGear.Pet a, LiveGear.Pet b) {
        return b != null && a.type().equals(b.type()) && (a.tier().isEmpty() || a.tier().equals(b.tier())) && a.level() == b.level()
                && (a.heldItem() == null || a.heldItem().equals(b.heldItem()));
    }

    // ---- Stack reading ----
    private static boolean inSkyBlock(Inventory inv) {
        for (ItemStack stack : inv.getNonEquipmentItems()) if ("SKYBLOCK_MENU".equals(id(stack))) return true;
        return false;
    }
    private static String id(ItemStack stack) {
        var custom = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
        return custom == null ? "" : custom.copyTag().getStringOr("id", "");
    }
    /** Gear: a SkyBlock item whose rarity line names a gear slot (cheap check before converting it). */
    private static boolean gear(ItemStack stack) {
        if (stack.isEmpty() || id(stack).isEmpty()) return false;
        List<String> lore = lore(stack);
        for (int i = lore.size() - 1; i >= 0 && i >= lore.size() - 3; i--) if (GEAR.matcher(lore.get(i)).find()) return true;
        return false;
    }
    private static boolean tool(JsonObject nbt) {
        JsonElement lore = nbt.getAsJsonObject("tag").getAsJsonObject("display").get("Lore");
        for (JsonElement line : lore.getAsJsonArray()) {
            Matcher m = GEAR.matcher(line.getAsString());
            if (m.find() && Set.of("DRILL", "PICKAXE", "GAUNTLET").contains(m.group(2))) return true;
        }
        return false;
    }
    /** {"tag":{"ExtraAttributes":custom data,"display":{"Name":..,"Lore":[..]}},"Count":n}, as the inventory API stores items. */
    private static JsonObject nbt(ItemStack stack) {
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        JsonElement extra = custom == null ? new JsonObject() : NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, custom.copyTag());
        JsonObject display = new JsonObject();
        display.addProperty("Name", stack.getHoverName().getString());
        JsonArray lore = new JsonArray();
        lore(stack).forEach(lore::add);
        display.add("Lore", lore);
        JsonObject tag = new JsonObject();
        tag.add("ExtraAttributes", extra.isJsonObject() ? extra : new JsonObject());
        tag.add("display", display);
        JsonObject nbt = new JsonObject();
        nbt.add("tag", tag);
        nbt.addProperty("Count", stack.getCount());
        return nbt;
    }
    /** ID, UUID and every custom data key that changes the item's stats; fuel and counters are left out. */
    private static String signature(JsonObject nbt) {
        JsonObject extra = nbt.getAsJsonObject("tag").getAsJsonObject("ExtraAttributes");
        StringBuilder out = new StringBuilder();
        new TreeMap<>(extra.asMap()).forEach((key, value) -> { if (!VOLATILE.matcher(key).find()) out.append(key).append('=').append(value).append(';'); });
        return out.toString();
    }
    private static List<String> lore(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<String> lines = new ArrayList<>();
        for (Component line : lore.lines()) lines.add(strip(line.getString()));
        return lines;
    }
    /** "Glacite Golem" -> GLACITE_GOLEM. */
    private static String type(String name) { return name.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_"); }
    /** Rarity from the colour of the pet's name (style colour or a legacy colour code before it); "" when unknown. */
    private static String tier(Component text, String name) {
        String[] found = {""};
        text.visit((Style style, String part) -> {
            int at = part.indexOf(name);
            if (at < 0) return Optional.empty();
            int code = part.lastIndexOf('§', at);
            if (code >= 0 && code + 1 < part.length()) found[0] = CODE_TIER.getOrDefault(Character.toLowerCase(part.charAt(code + 1)), "");
            else if (style.getColor() != null) found[0] = COLOR_TIER.getOrDefault(style.getColor().serialize(), "");
            return Optional.of(Boolean.TRUE);
        }, Style.EMPTY);
        return found[0];
    }
    private static String strip(String s) { return s.replaceAll("§.", "").strip(); }
}
