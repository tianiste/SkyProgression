package io.github.tianiste.skyprogression.client;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.github.tianiste.skyprogression.core.*;
import net.fabricmc.loader.api.FabricLoader;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static io.github.tianiste.skyprogression.core.Model.*;

final class ClientData {
    static final ClientData INSTANCE = new ClientData();
    static final ExecutorService WORK = Executors.newVirtualThreadPerTaskExecutor();
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("SkyProgression settings").factory());
    List<Item> catalog = List.of();
    ProgressionEngine engine;
    Report report;
    String message = "Load your player, or use Demo to try the interface.";
    String player = "";
    int profileIndex, revision;
    boolean loading, demo, ready, initializing, initializationFailed;
    private int generation;
    private Future<?> lookup, priceRequest;
    Map<String, Price> prices = new HashMap<>();
    final Map<String, Inputs> inputs = new HashMap<>();
    private HttpClient http;
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("skyprogression-inputs.json");
    static final class Inputs {
        Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        double efficiency = 65, blocksPerSecond, baseDrops = 1, coinsPerItem, coleweightPerItem;
        boolean confirmed;
        Inputs copy() {
            Inputs i=new Inputs();i.stats=new EnumMap<>(Stat.class);i.stats.putAll(stats);
            i.efficiency=efficiency;i.blocksPerSecond=blocksPerSecond;i.baseDrops=baseDrops;
            i.coinsPerItem=coinsPerItem;i.coleweightPerItem=coleweightPerItem;i.confirmed=confirmed;return i;
        }
    }
    // No filesystem, TLS initialization, or catalog parsing in the screen constructor.
    void initialize(Consumer<Runnable> dispatch,Runnable changed) {
        if(ready||initializing||initializationFailed)return;
        initializing=true;message="Preparing SkyProgression...";
        WORK.submit(()->{
            try {
                var items=Catalog.load();var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                Map<String,Inputs> saved=new HashMap<>();String warning="";
                try {
                    if(Files.exists(file))try(var stream=Files.newInputStream(file)) {
                        byte[] bytes=stream.readNBytes(1024*1024+1);
                        if(bytes.length>1024*1024)throw new IllegalArgumentException("Settings too large");
                        Map<String,Inputs> parsed=Catalog.GSON.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),new TypeToken<Map<String,Inputs>>(){}.getType());
                        if(parsed!=null)parsed.forEach((key,value)->{if(value!=null&&value.stats!=null&&value.stats.values().stream().allMatch(v->v!=null&&Double.isFinite(v)&&v>=0))saved.put(key,value);});
                    }
                }catch(Exception e){warning="Saved inputs could not be read. Re-enter them in Type Stats.";}
                String notice=warning;
                dispatch.accept(()->{catalog=items;engine=new ProgressionEngine(items);http=client;saved.forEach(inputs::putIfAbsent);ready=true;initializing=false;message=notice.isEmpty()?"Load your player, or try the offline Demo.":notice;revision++;changed.run();});
            }catch(Exception e){dispatch.accept(()->{initializing=false;initializationFailed=true;message="Could not initialize SkyProgression. Restart Minecraft to retry.";revision++;changed.run();});}
        });
    }
    Profile profile() { return report == null || report.profiles.isEmpty() ? null : report.profiles.get(Math.clamp(profileIndex, 0, report.profiles.size()-1)); }
    Inputs inputs(MiningMethod method) {
        Profile p = profile(); String key = (demo ? "demo" : p == null ? "none" : p.id) + ":" + method.name();
        return inputs.computeIfAbsent(key, ignored -> { Inputs i = new Inputs(); i.efficiency = method.defaultEfficiency(); return i; });
    }
    void save(Consumer<Runnable> dispatch,Consumer<Boolean> complete) {
        Map<String,Inputs> snapshot=new HashMap<>();inputs.forEach((key,value)->snapshot.put(key,value.copy()));
        DISK.submit(()->{
            boolean success=false;
            try {
                Files.createDirectories(file.getParent());
                Path temporary=file.resolveSibling(file.getFileName()+".tmp");
                Files.writeString(temporary,Catalog.GSON.toJson(snapshot));
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);success=true;
            }catch(Exception ignored){}
            boolean saved=success;dispatch.accept(()->{revision++;complete.accept(saved);});
        });
    }
    void cancel() {
        generation++;revision++;loading=false;
        if(lookup!=null)lookup.cancel(true);
        if(priceRequest!=null)priceRequest.cancel(true);
        message="Lookup cancelled. You can load again or use Demo.";
    }
    void load(String ign, Consumer<Runnable> dispatch, Runnable changed) {
        if(!ready||loading)return;
        if(!ign.matches("[A-Za-z0-9_]{1,16}")){message="Enter a valid Minecraft username.";revision++;changed.run();return;}
        cancel();int requestGeneration=generation;
        loading=true;message="Loading "+ign+"...";report=null;demo=false;prices=new HashMap<>();revision++;changed.run();
        lookup=WORK.submit(()->{
            try {
                JsonObject json=fetch("/report?ign="+ign,25).getAsJsonObject();
                if(json.has("error"))throw new IllegalStateException(json.get("error").getAsString());
                Report r=Catalog.GSON.fromJson(json,Report.class);
                if(r==null||r.profiles==null||r.profiles.isEmpty())throw new IllegalStateException("No profiles found");
                dispatch.accept(()->{
                    if(requestGeneration!=generation)return;
                    loading=false;report=r;player=ign;profileIndex=0;
                    for(int n=0;n<r.profiles.size();n++)if(r.profiles.get(n).selected)profileIndex=n;
                    message="Profile loaded. Prices loading...";loadPrices(dispatch,changed);revision++;changed.run();
                });
            }catch(Exception e){dispatch.accept(()->{
                if(requestGeneration!=generation)return;
                loading=false;message=e instanceof IllegalStateException?e.getMessage():"Companion unavailable or timed out. Start it, then Load again. Demo works offline.";revision++;changed.run();
            });}
        });
    }
    void loadPrices(Consumer<Runnable> dispatch,Runnable changed) {
        int requestGeneration=generation;
        priceRequest=WORK.submit(()->{
            Map<String,Price> quotes;
            try {quotes=Catalog.GSON.fromJson(fetch("/prices",90),new TypeToken<Map<String,Price>>(){}.getType());}
            catch(Exception e){quotes=Map.of();}
            Map<String,Price> result=quotes==null?Map.of():quotes;
            dispatch.accept(()->{
                if(demo||requestGeneration!=generation)return;
                prices=result;message=result.isEmpty()?"Prices unavailable. Costs remain unknown.":"Loaded "+result.size()+" price quotes. Socket fees included; no resale credit.";revision++;changed.run();
            });
        });
    }
    private JsonElement fetch(String path,int timeout) throws Exception {
        var response=BoundedHttp.get(http,URI.create("http://localhost:18475"+path),Duration.ofSeconds(timeout),4*1024*1024);
        JsonElement json=JsonParser.parseString(response.body());
        if(response.statusCode()!=200)throw new IllegalStateException(ProfileParser.string(ProfileParser.at(json,"error"),"Service error"));
        return json;
    }
    void demo() {
        if (!ready) return;
        cancel();
        demo = true; profileIndex = 0; report = new Report();
        Profile p = new Profile(); p.player = "Demo"; p.name = "Example only"; p.id = "demo"; p.inventoryEnabled = true; p.hotm = 7;
        for (String id : List.of("DIVAN_HELMET","DIVAN_CHESTPLATE","DIVAN_LEGGINGS","DIVAN_BOOTS","TITANIUM_DRILL_4","MINERAL_TALISMAN","MITHRIL_NECKLACE")) p.owned.put(id,1);
        p.enchantingLevel = 30; p.milestones.add(new Milestone("Heart of the Mountain",7,10,"tiers")); p.milestones.add(new Milestone("Mining skill",48,60,"levels")); p.progress.put("mithril powder (spent + available)",4800000.0); p.progress.put("gemstone powder (spent + available)",6200000.0); p.progress.put("glacite powder (spent + available)",1100000.0);
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
