package io.github.tianiste.skyprogression.client;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.github.tianiste.skyprogression.core.*;
import net.fabricmc.loader.api.FabricLoader;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static io.github.tianiste.skyprogression.core.Model.*;

final class ClientData {
    static final ClientData INSTANCE = new ClientData();
    final List<Item> catalog = Catalog.load();
    final ProgressionEngine engine = new ProgressionEngine(catalog);
    Report report;
    String message = "Load your player, or use Demo to try the interface.";
    String player = "";
    int profileIndex;
    boolean loading, demo;
    private int generation;
    Map<String, Price> prices = new HashMap<>();
    final Map<String, Inputs> inputs = new HashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("skyprogression-inputs.json");
    static final class Inputs {
        Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        double efficiency = 65, blocksPerSecond, baseDrops = 1, coinsPerItem, coleweightPerItem;
        boolean confirmed;
    }
    ClientData() {
        try {
            if (Files.exists(file)) {
                Map<String, Inputs> saved = Catalog.GSON.fromJson(Files.readString(file), new TypeToken<Map<String, Inputs>>(){}.getType());
                if (saved != null) inputs.putAll(saved);
            }
        } catch (Exception e) { message = "Saved rate inputs could not be read. Re-enter them in Type Stats."; }
    }
    Profile profile() { return report == null || report.profiles.isEmpty() ? null : report.profiles.get(Math.clamp(profileIndex, 0, report.profiles.size()-1)); }
    Inputs inputs(MiningMethod method) {
        Profile p = profile(); String key = (demo ? "demo" : p == null ? "none" : p.id) + ":" + method.name();
        return inputs.computeIfAbsent(key, ignored -> { Inputs i = new Inputs(); i.efficiency = method.defaultEfficiency(); return i; });
    }
    void save() throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temporary, Catalog.GSON.toJson(inputs));
        Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
    }
    void load(String ign, java.util.function.Consumer<Runnable> dispatch, Runnable changed) {
        if (loading) return;
        if (!ign.matches("[A-Za-z0-9_]{1,16}")) { message = "Enter a valid Minecraft username."; changed.run(); return; }
        generation++;
        loading = true; message = "Loading " + ign + "..."; report = null; demo = false;
        prices = new HashMap<>(); changed.run();
        CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject json = fetch("/report?ign=" + ign, 25).getAsJsonObject();
                if (json.has("error")) throw new IllegalStateException(json.get("error").getAsString());
                Report r = Catalog.GSON.fromJson(json, Report.class);
                if (r.profiles.isEmpty()) throw new IllegalStateException("No profiles found");
                return r;
            } catch (Exception e) { throw new CompletionException(e); }
        }).whenComplete((r, error) -> dispatch.accept(() -> {
            loading = false;
            if (error != null) {
                Throwable cause = error.getCause();
                message = cause instanceof IllegalStateException ? cause.getMessage() : "Companion service unavailable. Start it on this computer, then Load again. Demo works offline.";
            } else {
                report = r; player = ign; profileIndex = 0;
                for (int n=0; n<r.profiles.size(); n++) if (r.profiles.get(n).selected) profileIndex = n;
                message = "Profile loaded. Prices loading..."; loadPrices(dispatch, changed);
            }
            changed.run();
        }));
    }
    void loadPrices(java.util.function.Consumer<Runnable> dispatch, Runnable changed) {
        int requestGeneration = generation;
        CompletableFuture.supplyAsync(() -> {
            try { return Catalog.GSON.<Map<String, Price>>fromJson(fetch("/prices", 240), new TypeToken<Map<String, Price>>(){}.getType()); }
            catch (Exception e) { return Map.<String, Price>of(); }
        }).thenAccept(p -> dispatch.accept(() -> {
            if (demo || generation != requestGeneration) return;
            prices = p; message = p.isEmpty() ? "Prices unavailable. Costs remain unknown." : "Loaded " + p.size() + " price quotes. Supported socket fees included; no resale credit.";
            changed.run();
        }));
    }
    private JsonElement fetch(String path, int timeout) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18475" + path)).timeout(Duration.ofSeconds(timeout)).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            byte[] bytes = stream.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024) throw new IllegalStateException("Service response is too large");
            JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IllegalStateException(ProfileParser.string(ProfileParser.at(json,"error"),"Service error"));
            return json;
        }
    }
    void demo() {
        if (loading) return;
        generation++;
        demo = true; profileIndex = 0; report = new Report();
        Profile p = new Profile(); p.player = "Demo"; p.name = "Example only"; p.id = "demo"; p.inventoryEnabled = true; p.hotm = 7;
        for (String id : List.of("DIVAN_HELMET","DIVAN_CHESTPLATE","DIVAN_LEGGINGS","DIVAN_BOOTS","TITANIUM_DRILL_4","MINERAL_TALISMAN","MITHRIL_NECKLACE")) p.owned.put(id,1);
        p.enchantingLevel = 30;
        Item drill = catalog.stream().filter(i -> i.id.equals("TITANIUM_DRILL_4")).findFirst().orElseThrow();
        p.gear.add(GearReader.read(JsonParser.parseString("""
            {"tag":{"ExtraAttributes":{"uuid":"demo-drill","enchantments":{"fortune":3,"efficiency":7,"pristine":3},
            "gems":{"JADE_0":"FLAWLESS","AMBER_0":"FINE"}},
            "display":{"Name":"Demo Titanium Drill","Lore":["Mining Fortune: +200","Mining Speed: +2,000","Pristine: +1.5"]}}}
            """), drill, "Demo inventory", 0));
        p.owned.put("SIL_EX", 1);
        p.owned.put("GEMSTONE_CHAMBER", 1);
        p.notes.add("DEMO DATA. Example ownership and prices, not your real profile.");
        report.profiles.add(p); prices = new HashMap<>();
        int n = 1; for (String id : GearUpgrades.priceIds(catalog)) prices.put(id, new Price(250_000.0 * n++, "Demo price"));
        message = "DEMO - example inventory and prices. Load your player for real data.";
    }
}
