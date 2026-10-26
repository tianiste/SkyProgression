package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.io.*;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/** Bounded reader for the legacy NBT used by Hypixel's inventory API (not Minecraft's save format). */
public final class InventoryReader {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private int nodes;
    public JsonObject read(String base64) throws IOException {
        if (base64.length() > MAX_BYTES) throw new IOException("Inventory too large");
        byte[] compressed;
        try { compressed = Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid inventory encoding"); }
        byte[] bytes;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            bytes = gzip.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Inventory too large");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != 10) throw new IOException("Expected inventory compound");
            in.readUTF(); nodes = 0;
            return tag(in, 10, 0).getAsJsonObject();
        }
    }
    private int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 200_000) throw new IOException("Invalid inventory length");
        return n;
    }
    private JsonElement tag(DataInputStream in, int type, int depth) throws IOException {
        if (depth > 32 || ++nodes > 250_000) throw new IOException("Inventory complexity limit exceeded");
        return switch (type) {
            case 1 -> new JsonPrimitive(in.readByte());
            case 2 -> new JsonPrimitive(in.readShort());
            case 3 -> new JsonPrimitive(in.readInt());
            case 4 -> new JsonPrimitive(in.readLong());
            case 5 -> new JsonPrimitive(in.readFloat());
            case 6 -> new JsonPrimitive(in.readDouble());
            case 7 -> { int n = length(in); in.skipNBytes(n); yield JsonNull.INSTANCE; }
            case 8 -> new JsonPrimitive(in.readUTF());
            case 9 -> {
                int child = in.readUnsignedByte(), n = length(in);
                JsonArray a = new JsonArray();
                for (int i = 0; i < n; i++) a.add(tag(in, child, depth + 1));
                yield a;
            }
            case 10 -> {
                JsonObject o = new JsonObject(); int child;
                while ((child = in.readUnsignedByte()) != 0) {
                    String name = in.readUTF(); o.add(name, tag(in, child, depth + 1));
                }
                yield o;
            }
            case 11 -> { int n = length(in); in.skipNBytes((long)n * 4); yield JsonNull.INSTANCE; }
            case 12 -> { int n = length(in); in.skipNBytes((long)n * 8); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Unsupported inventory tag " + type);
        };
    }
}
