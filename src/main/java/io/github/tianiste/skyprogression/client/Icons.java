package io.github.tianiste.skyprogression.client;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkyBlock item icons: the item's base material, leather colour or head texture from the bundled table
 * (generated from the official items resource), plus its SkyBlock ID in custom_data, which is what
 * texture-pack mods use to apply SkyBlock textures. The table is read off the render thread by {@link #load()}.
 */
final class Icons {
    private Icons() {}
    /** id: custom_data id when it differs from the key (pets are "PET"); pet: the petInfo JSON of a pet. */
    private record Entry(String material, int durability, String color, String texture, String id, String pet) {}
    private static volatile Map<String, Entry> table = Map.of();
    private static final Map<String, ItemStack> STACKS = new ConcurrentHashMap<>();
    /** Bukkit 1.8 material names that differ from current item IDs; key "NAME:durability" wins over "NAME". */
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("SKULL_ITEM:0", "skeleton_skull"), Map.entry("SKULL_ITEM:2", "zombie_head"), Map.entry("SKULL_ITEM:3", "player_head"),
            Map.entry("SKULL_ITEM:4", "creeper_head"), Map.entry("SKULL_ITEM", "player_head"),
            Map.entry("BANNER:15", "white_banner"), Map.entry("CARPET:14", "red_carpet"), Map.entry("STAINED_CLAY:14", "red_terracotta"),
            Map.entry("STAINED_GLASS:14", "red_stained_glass"), Map.entry("RED_ROSE:3", "azure_bluet"),
            Map.entry("COOKED_FISH", "cooked_cod"), Map.entry("RAW_FISH", "cod"), Map.entry("FIREWORK", "firework_rocket"),
            Map.entry("GOLD_BARDING", "golden_horse_armor"), Map.entry("IRON_BARDING", "iron_horse_armor"), Map.entry("LEAVES", "oak_leaves"),
            Map.entry("WATER_LILY", "lily_pad"), Map.entry("WOOD_PICKAXE", "wooden_pickaxe"), Map.entry("YELLOW_FLOWER", "dandelion"),
            Map.entry("GOLD_HELMET", "golden_helmet"), Map.entry("GOLD_CHESTPLATE", "golden_chestplate"), Map.entry("GOLD_LEGGINGS", "golden_leggings"),
            Map.entry("GOLD_BOOTS", "golden_boots"), Map.entry("GOLD_PICKAXE", "golden_pickaxe"));

    /** Reads the bundled table; call from a background thread. Missing or broken data leaves the generic icons. */
    static void load() {
        try (var stream = Icons.class.getResourceAsStream("/skyprogression-item-icons.json")) {
            if (stream == null) return;
            JsonObject items = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("items");
            Map<String, Entry> loaded = new HashMap<>();
            for (var e : items.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                loaded.put(e.getKey(), new Entry(o.get("m").getAsString(), o.has("d") ? o.get("d").getAsInt() : 0,
                        o.has("c") ? o.get("c").getAsString() : "", o.has("t") ? o.get("t").getAsString() : "",
                        o.has("i") ? o.get("i").getAsString() : e.getKey(), o.has("p") ? o.get("p").getAsString() : ""));
            }
            table = Map.copyOf(loaded);
        } catch (Exception ignored) { /* Generic icons remain. */ }
    }

    /** Icon for a SkyBlock item ID or a vanilla "minecraft:" item, or null when the item is not known. */
    static ItemStack of(String id) {
        if (id == null || id.isEmpty()) return null;
        if (id.startsWith("minecraft:")) {
            Identifier key = Identifier.tryParse(id);
            Optional<Item> item = key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
            return item.isEmpty() ? null : STACKS.computeIfAbsent(id, k -> new ItemStack(item.get()));
        }
        if (!table.containsKey(id)) return null;
        return STACKS.computeIfAbsent(id, Icons::build);
    }

    private static ItemStack build(String id) {
        Entry e = table.get(id);
        String name = LEGACY.getOrDefault(e.material + ":" + e.durability, LEGACY.getOrDefault(e.material, e.material.toLowerCase(Locale.ROOT)));
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(name)).orElse(Items.PAPER);
        ItemStack stack = new ItemStack(item);
        CompoundTag data = new CompoundTag();
        data.putString("id", e.id);
        if (!e.pet.isEmpty()) data.putString("petInfo", e.pet);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        if (item == Items.ENCHANTED_BOOK) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (!e.color.isEmpty()) {
            String[] rgb = e.color.split(",");
            try { stack.set(DataComponents.DYED_COLOR, new DyedItemColor(Integer.parseInt(rgb[0].strip()) << 16 | Integer.parseInt(rgb[1].strip()) << 8 | Integer.parseInt(rgb[2].strip()))); }
            catch (RuntimeException ignored) { /* Undyed leather. */ }
        }
        if (!e.texture.isEmpty() && item == Items.PLAYER_HEAD) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + e.texture + "\"}}}";
            var properties = com.google.common.collect.LinkedHashMultimap.<String, Property>create();
            properties.put("textures", new Property("textures", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
            UUID uuid = UUID.nameUUIDFromBytes(e.texture.getBytes(StandardCharsets.UTF_8));
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(uuid, "", new PropertyMap(properties))));
        }
        return stack;
    }
}
