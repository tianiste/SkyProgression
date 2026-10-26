package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

public final class Catalog {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static List<Item> load() {
        try (var stream = Catalog.class.getResourceAsStream("/mining-items.json")) {
            if (stream == null) throw new IllegalStateException("Missing mining catalog");
            return List.of(GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Item[].class));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
