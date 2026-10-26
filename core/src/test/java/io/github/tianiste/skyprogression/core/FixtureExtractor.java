package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.*;

/**
 * Builds the committed regression fixture from the private, Git-ignored profile snapshot.
 * Run with {@code ./gradlew :core:extractFixture}. Only fields used by tests are copied;
 * identifiers (player/profile UUIDs, item and gem UUIDs, timestamps) are removed or replaced.
 */
public final class FixtureExtractor {
    static final String MEMBER = "fixture-member";
    static final Set<String> DROPPED_KEYS = Set.of("uuid", "timestamp", "originTag", "bossId", "spawnedFor",
            "uniqueId", "heldItemUuid", "purchase_ts", "skin");
    static final Set<String> ACCESSORIES = Set.of("SCAVENGER_TALISMAN", "MINE_TALISMAN", "JUNGLE_AMULET",
            "POWER_RING", "MINERAL_TALISMAN", "HASTE_ARTIFACT", "TITANIUM_RELIC");
    static final Set<String> PETS = Set.of("BAL", "MOLE", "ARMADILLO", "GLACITE_GOLEM", "SCATHA", "SILVERFISH");
    static final List<String> COLLECTIONS = List.of("COBBLESTONE", "COAL", "IRON_INGOT", "GOLD_INGOT", "DIAMOND",
            "INK_SACK:4", "EMERALD", "REDSTONE", "QUARTZ", "OBSIDIAN", "GLOWSTONE_DUST", "GRAVEL", "ICE",
            "NETHERRACK", "SAND", "ENDER_STONE", "MITHRIL_ORE", "HARD_STONE", "GEMSTONE_COLLECTION", "MYCEL",
            "SAND:1", "SULPHUR", "GLACITE", "TUNGSTEN", "UMBER");

    public static void main(String[] args) throws IOException {
        Path in = Path.of(args.length > 0 ? args[0] : "service-data/debotes-profiles.json");
        Path out = Path.of(args.length > 1 ? args[1] : "core/src/test/resources/fixtures/debotes-zucchini.json");
        if (!Files.exists(in)) { System.out.println("No private snapshot at " + in + "; nothing extracted."); return; }
        JsonObject root = JsonParser.parseString(Files.readString(in)).getAsJsonObject();
        JsonObject profile = null;
        for (JsonElement p : root.getAsJsonArray("profiles"))
            if (p.getAsJsonObject().has("selected") && p.getAsJsonObject().get("selected").getAsBoolean()) profile = p.getAsJsonObject();
        if (profile == null) profile = root.getAsJsonArray("profiles").get(0).getAsJsonObject();
        JsonObject member = profile.getAsJsonObject("members").entrySet().iterator().next().getValue().getAsJsonObject();
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(extract(profile, member)) + "\n", StandardCharsets.UTF_8);
        System.out.println("Wrote " + out);
    }

    static JsonObject extract(JsonObject profile, JsonObject member) throws IOException {
        JsonObject m = new JsonObject();
        JsonObject skillTree = new JsonObject();
        copy(member, skillTree, "skill_tree", "nodes", "mining");
        copy(member, skillTree, "skill_tree", "experience", "mining");
        copy(member, skillTree, "skill_tree", "tokens_spent", "mountain");
        copy(member, skillTree, "skill_tree", "selected_ability", "mining");
        m.add("skill_tree", skillTree);

        JsonObject core = new JsonObject();
        for (String powder : List.of("mithril", "gemstone", "glacite"))
            for (String key : List.of("powder_" + powder, "powder_spent_" + powder, "powder_" + powder + "_total"))
                copyField(member.getAsJsonObject("mining_core"), core, key);
        copyField(member.getAsJsonObject("mining_core"), core, "current_daily_effect");
        m.add("mining_core", core);

        JsonObject playerData = new JsonObject();
        JsonObject experience = new JsonObject();
        for (String skill : List.of("SKILL_MINING", "SKILL_ENCHANTING")) copyField(obj(member, "player_data", "experience"), experience, skill);
        playerData.add("experience", experience);
        JsonObject perks = new JsonObject();
        for (String perk : List.of("dwarven_training", "eager_miner", "treasures_of_the_earth", "rhinestone_infusion", "under_pressure"))
            copyField(obj(member, "player_data", "perks"), perks, perk);
        playerData.add("perks", perks);
        for (String key : List.of("active_effects", "temp_stat_buffs")) copyField(obj(member, "player_data"), playerData, key);
        m.add("player_data", playerData);

        m.add("attributes", sanitize(member.getAsJsonObject("attributes")));
        JsonObject bag = new JsonObject();
        for (String key : List.of("highest_magical_power", "selected_power", "unlocked_powers"))
            copyField(member.getAsJsonObject("accessory_bag_storage"), bag, key);
        m.add("accessory_bag_storage", bag);

        JsonObject collection = new JsonObject();
        for (String id : COLLECTIONS) copyField(member.getAsJsonObject("collection"), collection, id);
        m.add("collection", collection);
        JsonObject leveling = new JsonObject();
        copyField(member.getAsJsonObject("leveling"), leveling, "mining_fiesta_ores_mined");
        m.add("leveling", leveling);
        JsonObject glacite = new JsonObject();
        for (String key : List.of("fossils_donated", "corpses_looted", "mineshafts_entered"))
            copyField(member.getAsJsonObject("glacite_player_data"), glacite, key);
        m.add("glacite_player_data", glacite);
        JsonObject cookie = new JsonObject();
        copyField(member.getAsJsonObject("profile"), cookie, "cookie_buff_active");
        m.add("profile", cookie);

        JsonArray pets = new JsonArray();
        for (JsonElement pet : obj(member, "pets_data").getAsJsonArray("pets"))
            if (PETS.contains(pet.getAsJsonObject().get("type").getAsString())) pets.add(sanitize(pet));
        JsonObject petsData = new JsonObject();
        petsData.add("pets", pets);
        m.add("pets_data", petsData);

        JsonObject inventory = new JsonObject();
        JsonObject source = member.getAsJsonObject("inventory");
        inventory.add("inv_armor", container(source.get("inv_armor"), id -> true));
        inventory.add("equipment_contents", container(source.get("equipment_contents"), id -> true));
        inventory.add("inv_contents", container(source.get("inv_contents"),
                id -> id.contains("DRILL") || id.contains("PICKAXE") || id.contains("GAUNTLET")));
        JsonObject bags = new JsonObject();
        bags.add("talisman_bag", container(obj(source, "bag_contents").get("talisman_bag"), ACCESSORIES::contains));
        inventory.add("bag_contents", bags);
        m.add("inventory", inventory);

        JsonObject p = new JsonObject();
        p.addProperty("profile_id", "fixture-profile");
        p.addProperty("cute_name", profile.has("cute_name") ? profile.get("cute_name").getAsString() : "Profile");
        p.addProperty("selected", true);
        JsonObject members = new JsonObject();
        members.add(MEMBER, m);
        p.add("members", members);
        JsonObject root = new JsonObject();
        root.addProperty("_note", "Sanitized subset of a private profile snapshot. Regenerate with ./gradlew :core:extractFixture.");
        JsonArray profiles = new JsonArray();
        profiles.add(p);
        root.add("profiles", profiles);
        return root;
    }

    private static JsonObject obj(JsonObject parent, String... path) {
        JsonElement value = ProfileParser.at(parent, path);
        return value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static void copy(JsonObject member, JsonObject target, String section, String child, String key) {
        JsonElement value = ProfileParser.at(member, section, child, key);
        if (value.isJsonNull()) return;
        if (!target.has(child)) target.add(child, new JsonObject());
        target.getAsJsonObject(child).add(key, sanitize(value));
    }
    private static void copyField(JsonObject from, JsonObject to, String key) {
        if (from != null && from.has(key)) to.add(key, sanitize(from.get(key)));
    }
    static JsonElement sanitize(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject copy = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(e -> { if (!DROPPED_KEYS.contains(e.getKey())) copy.add(e.getKey(), sanitize(e.getValue())); });
            return copy;
        }
        if (value.isJsonArray()) {
            JsonArray copy = new JsonArray();
            value.getAsJsonArray().forEach(e -> copy.add(sanitize(e)));
            return copy;
        }
        return value.deepCopy();
    }

    /** Re-encodes an inventory, keeping only matching item ids and dropping identifying tags. */
    static JsonObject container(JsonElement container, Predicate<String> keep) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("type", 0);
        String data = ProfileParser.string(ProfileParser.at(container, "data"), "");
        if (data.isEmpty()) { result.addProperty("data", ""); return result; }
        Nbt.Compound root;
        try (var in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data))))) {
            in.readUnsignedByte(); in.readUTF();
            root = (Nbt.Compound) Nbt.read(in, 10);
        }
        Nbt.ListTag items = (Nbt.ListTag) root.values().get("i");
        List<Object> kept = new ArrayList<>();
        for (Object item : items.values()) {
            Object id = path(item, "tag", "ExtraAttributes", "id");
            if (id instanceof String s && keep.test(s)) kept.add(Nbt.strip(item));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("i", new Nbt.ListTag(10, kept));
        var bytes = new ByteArrayOutputStream();
        try (var o = new DataOutputStream(new GZIPOutputStream(bytes))) {
            o.writeByte(10); o.writeUTF(""); Nbt.write(o, new Nbt.Compound(out));
        }
        result.addProperty("data", Base64.getEncoder().encodeToString(bytes.toByteArray()));
        return result;
    }
    private static Object path(Object tag, String... keys) {
        for (String key : keys) {
            if (!(tag instanceof Nbt.Compound c)) return null;
            tag = c.values().get(key);
        }
        return tag;
    }

    /** Minimal typed NBT tree so re-encoding keeps the original tag types. */
    static final class Nbt {
        record Compound(Map<String, Object> values) {}
        record ListTag(int child, List<Object> values) {}
        record Typed(int type, Object value) {}

        static Object read(DataInputStream in, int type) throws IOException {
            return switch (type) {
                case 1 -> new Typed(1, in.readByte());
                case 2 -> new Typed(2, in.readShort());
                case 3 -> new Typed(3, in.readInt());
                case 4 -> new Typed(4, in.readLong());
                case 5 -> new Typed(5, in.readFloat());
                case 6 -> new Typed(6, in.readDouble());
                case 7 -> { byte[] b = new byte[in.readInt()]; in.readFully(b); yield new Typed(7, b); }
                case 8 -> in.readUTF();
                case 9 -> {
                    int child = in.readUnsignedByte(), n = in.readInt();
                    List<Object> list = new ArrayList<>();
                    for (int i = 0; i < n; i++) list.add(read(in, child));
                    yield new ListTag(child, list);
                }
                case 10 -> {
                    Map<String, Object> map = new LinkedHashMap<>(); int child;
                    while ((child = in.readUnsignedByte()) != 0) { String name = in.readUTF(); map.put(name, read(in, child)); }
                    yield new Compound(map);
                }
                case 11 -> { int[] a = new int[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); yield new Typed(11, a); }
                case 12 -> { long[] a = new long[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); yield new Typed(12, a); }
                default -> throw new IOException("Unsupported tag " + type);
            };
        }
        static int type(Object tag) {
            if (tag instanceof Typed t) return t.type();
            if (tag instanceof String) return 8;
            if (tag instanceof ListTag) return 9;
            return 10;
        }
        static void write(DataOutputStream out, Object tag) throws IOException {
            switch (tag) {
                case String s -> out.writeUTF(s);
                case ListTag l -> {
                    out.writeByte(l.values().isEmpty() ? 0 : l.child()); out.writeInt(l.values().size());
                    for (Object v : l.values()) write(out, v);
                }
                case Compound c -> {
                    for (var e : c.values().entrySet()) { out.writeByte(type(e.getValue())); out.writeUTF(e.getKey()); write(out, e.getValue()); }
                    out.writeByte(0);
                }
                case Typed t -> {
                    switch (t.type()) {
                        case 1 -> out.writeByte((Byte) t.value());
                        case 2 -> out.writeShort((Short) t.value());
                        case 3 -> out.writeInt((Integer) t.value());
                        case 4 -> out.writeLong((Long) t.value());
                        case 5 -> out.writeFloat((Float) t.value());
                        case 6 -> out.writeDouble((Double) t.value());
                        case 7 -> { byte[] b = (byte[]) t.value(); out.writeInt(b.length); out.write(b); }
                        case 11 -> { int[] a = (int[]) t.value(); out.writeInt(a.length); for (int v : a) out.writeInt(v); }
                        case 12 -> { long[] a = (long[]) t.value(); out.writeInt(a.length); for (long v : a) out.writeLong(v); }
                        default -> throw new IOException("Unsupported tag " + t.type());
                    }
                }
                default -> throw new IOException("Unknown tag " + tag);
            }
        }
        /** Copies a tag tree without identifying keys and without the skull texture/owner blob. */
        static Object strip(Object tag) {
            if (tag instanceof Compound c) {
                Map<String, Object> map = new LinkedHashMap<>();
                c.values().forEach((k, v) -> { if (!DROPPED_KEYS.contains(k) && !k.equals("SkullOwner")) map.put(k, strip(v)); });
                return new Compound(map);
            }
            if (tag instanceof ListTag l) return new ListTag(l.child(), l.values().stream().map(Nbt::strip).toList());
            return tag;
        }
    }
}
