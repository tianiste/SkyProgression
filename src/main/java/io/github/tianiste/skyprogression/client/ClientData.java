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
    private Future<?> lookup, priceRequest, statsRequest, compareRequest, startRequest;
    /** Automatic stat ledger for {@link #statsKey}; requested lazily from the companion's /stats endpoint. */
    StatsReport stats;
    String statsKey = "", statsPending = "", statsError = "";
    Map<String, Price> prices = new HashMap<>();
    /** Every method's automatic totals for the method comparison; requested from the companion's /compare endpoint. */
    MethodComparison.Report comparison;
    String compareKey = "", comparePending = "", compareError = "";
    /** Best owned loadout and swaps for {@link #startKey}; requested from the companion's /start endpoint. */
    StartWith.Report start;
    String startKey = "", startPending = "", startError = "";
    MethodComparison.PriceMode priceMode = MethodComparison.PriceMode.INSTANT_SELL;
    boolean compareGodPotion = true;
    final Map<String, Inputs> inputs = new HashMap<>();
    private HttpClient http;
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("skyprogression-inputs.json");
    static final class Inputs {
        /** Manual values; a stat absent here uses the automatic total when one exists. */
        Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        /** Held tool key (empty = best owned tool) and zone name (empty = the method's usual zone, "-" none). */
        String tool = "", zone = "";
        /** False: best owned gear for the method; true: what the API reports as worn. */
        boolean equippedGear, godPotion = true;
        double efficiency = 65, blocksPerSecond, baseDrops = 1, coinsPerItem, coleweightPerItem;
        boolean confirmed;
        Inputs copy() {
            Inputs i=new Inputs();i.stats=new EnumMap<>(Stat.class);i.stats.putAll(stats);
            i.efficiency=efficiency;i.blocksPerSecond=blocksPerSecond;i.baseDrops=baseDrops;
            i.coinsPerItem=coinsPerItem;i.coleweightPerItem=coleweightPerItem;i.confirmed=confirmed;
            i.tool=tool;i.zone=zone;i.equippedGear=equippedGear;i.godPotion=godPotion;return i;
        }
    }
    // No filesystem, TLS initialization, or catalog parsing in the screen constructor.
    void initialize(Consumer<Runnable> dispatch,Runnable changed) {
        if(ready||initializing||initializationFailed)return;
        initializing=true;message="Preparing SkyProgression...";
        WORK.submit(()->{
            try {
                var items=Catalog.load();Icons.load();var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
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
    StatContext.Zone zone(MiningMethod method) {
        String z=inputs(method).zone;
        if(z==null||z.isEmpty())return method.defaultZone();
        try{return z.equals("-")?null:StatContext.Zone.valueOf(z);}catch(IllegalArgumentException e){return method.defaultZone();}
    }
    private String statsQuery(MiningMethod method) {
        Profile p=profile();if(p==null||demo)return "";
        Inputs i=inputs(method);StatContext.Zone z=zone(method);
        return "/stats?ign="+enc(p.player)+"&profile="+enc(p.id)+"&method="+method.name()+"&gear="+(i.equippedGear?"equipped":"best")+"&tool="+enc(i.tool==null?"":i.tool)+"&zone="+(z==null?"":z.name())
                +"&target="+method.target().name()+"&god="+i.godPotion;
    }
    private String startQuery(MiningMethod method) {
        Profile p=profile();if(p==null||demo)return "";
        Inputs i=inputs(method);StatContext.Zone z=zone(method);
        return "/start?ign="+enc(p.player)+"&profile="+enc(p.id)+"&method="+method.name()+"&tool="+enc(i.tool==null?"":i.tool)+"&zone="+(z==null?"":z.name())
                +"&target="+method.target().name()+"&god="+i.godPotion;
    }
    /** Start-with report for the current selections, or null while missing, loading or failed. */
    StartWith.Report start(MiningMethod method){String key=startQuery(method);return !key.isEmpty()&&key.equals(startKey)?start:null;}
    boolean startLoading(MiningMethod method){String key=startQuery(method);return !key.isEmpty()&&key.equals(startPending);}
    String startError(MiningMethod method){String key=startQuery(method);return !key.isEmpty()&&key.equals(startKey)?startError:"";}
    void ensureStart(MiningMethod method,Consumer<Runnable> dispatch,Runnable changed) {
        String key=startQuery(method);
        if(!ready||loading||key.isEmpty()||key.equals(startKey)||key.equals(startPending))return;
        if(startRequest!=null)startRequest.cancel(true);
        startPending=key;startError="";int requestGeneration=generation;
        startRequest=WORK.submit(()->{
            StartWith.Report result=null;String error="";
            try{result=Catalog.GSON.fromJson(fetch(key,45),StartWith.Report.class);if(result==null||result.current==null||result.bestOwned==null)throw new IllegalStateException("Empty start-with report");}
            catch(IllegalStateException e){error="Unknown endpoint".equals(e.getMessage())?"The companion is older than the mod. Restart it to use Start with.":e.getMessage();}
            catch(Exception e){error="Companion unavailable or timed out. Start with needs the companion.";}
            StartWith.Report loaded=result;String failure=error;
            dispatch.accept(()->{
                if(requestGeneration!=generation||!key.equals(startPending))return;
                startPending="";startKey=key;start=loaded;startError=failure;revision++;changed.run();
            });
        });
    }
    void refreshStart(){startKey="";startPending="";start=null;startError="";if(startRequest!=null)startRequest.cancel(true);revision++;}
    private static String enc(String value){return URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
    /** Automatic ledger for the current selections, or null while missing, loading or failed. */
    StatsReport stats(MiningMethod method){String key=statsQuery(method);return !key.isEmpty()&&key.equals(statsKey)?stats:null;}
    /** Automatic totals overlaid by the player's manual values. */
    Map<Stat,Double> effective(MiningMethod method) {
        Map<Stat,Double> result=new EnumMap<>(Stat.class);StatsReport r=stats(method);
        if(r!=null)for(var l:r.stats)result.put(l.stat,l.total);
        result.putAll(inputs(method).stats);return result;
    }
    boolean statsLoading(MiningMethod method){String key=statsQuery(method);return !key.isEmpty()&&key.equals(statsPending);}
    String statsError(MiningMethod method){String key=statsQuery(method);return !key.isEmpty()&&key.equals(statsKey)?statsError:"";}
    boolean hasStats(MiningMethod method){return inputs(method).confirmed||stats(method)!=null;}
    /** Requests the ledger when the selections changed; safe to call on every screen rebuild. */
    void ensureStats(MiningMethod method,Consumer<Runnable> dispatch,Runnable changed) {
        String key=statsQuery(method);
        if(!ready||loading||key.isEmpty()||key.equals(statsKey)||key.equals(statsPending))return;
        if(statsRequest!=null)statsRequest.cancel(true);
        statsPending=key;statsError="";int requestGeneration=generation;
        statsRequest=WORK.submit(()->{
            StatsReport result=null;String error="";
            try{result=Catalog.GSON.fromJson(fetch(key,30),StatsReport.class);if(result==null||result.stats==null)throw new IllegalStateException("Empty stat breakdown");}
            catch(IllegalStateException e){error=e.getMessage();}
            catch(Exception e){error="Companion unavailable or timed out. Automatic stats need the companion.";}
            StatsReport loaded=result;String failure=error;
            dispatch.accept(()->{
                if(requestGeneration!=generation||!key.equals(statsPending))return;
                statsPending="";statsKey=key;stats=loaded;statsError=failure;revision++;changed.run();
            });
        });
    }
    private String compareQuery(){Profile p=profile();return p==null||demo?"":"/compare?ign="+enc(p.player)+"&profile="+enc(p.id)+"&god="+compareGodPotion;}
    /** Comparison for the loaded profile, or null while missing, loading or failed. */
    MethodComparison.Report comparison(){String key=compareQuery();return !key.isEmpty()&&key.equals(compareKey)?comparison:null;}
    boolean compareLoading(){String key=compareQuery();return !key.isEmpty()&&key.equals(comparePending);}
    String compareError(){String key=compareQuery();return !key.isEmpty()&&key.equals(compareKey)?compareError:"";}
    /** Rows for every method: its automatic totals overlaid by that method's Type Stats values and efficiency. */
    List<MethodComparison.Row> comparisonRows(){
        var r=comparison();if(r==null)return List.of();
        List<MethodComparison.Row> rows=new ArrayList<>();
        for(var e:r.methods){if(e==null||e.method==null||e.stats==null)continue;Inputs i=inputs(e.method);Map<Stat,Double> stats=new EnumMap<>(Stat.class);stats.putAll(e.stats);stats.putAll(i.stats);
            rows.add(MethodComparison.row(e.method,stats,e.complete,i.efficiency,e.ability,prices,priceMode,e.tool));}
        return MethodComparison.rank(rows);
    }
    /** Effective stats of one method in the comparison (automatic totals, Type Stats overrides win); null before it loads. */
    Map<Stat,Double> comparisonStats(MiningMethod m){
        var r=comparison();var e=r==null?null:r.entry(m);if(e==null||e.stats==null)return null;
        Map<Stat,Double> stats=new EnumMap<>(Stat.class);stats.putAll(e.stats);stats.putAll(inputs(m).stats);return stats;
    }
    void ensureCompare(Consumer<Runnable> dispatch,Runnable changed) {
        String key=compareQuery();
        if(!ready||loading||key.isEmpty()||key.equals(compareKey)||key.equals(comparePending))return;
        if(compareRequest!=null)compareRequest.cancel(true);
        comparePending=key;compareError="";int requestGeneration=generation;
        compareRequest=WORK.submit(()->{
            MethodComparison.Report result=null;String error="";
            try{result=Catalog.GSON.fromJson(fetch(key,45),MethodComparison.Report.class);if(result==null||result.methods==null)throw new IllegalStateException("Empty method comparison");}
            catch(IllegalStateException e){error=e.getMessage();}
            catch(Exception e){error="Companion unavailable or timed out. The method comparison needs the companion.";}
            MethodComparison.Report loaded=result;String failure=error;
            dispatch.accept(()->{
                if(requestGeneration!=generation||!key.equals(comparePending))return;
                comparePending="";compareKey=key;comparison=loaded;compareError=failure;revision++;changed.run();
            });
        });
    }
    void refreshCompare(){compareKey="";comparePending="";comparison=null;compareError="";if(compareRequest!=null)compareRequest.cancel(true);revision++;}
    void refreshStats(){statsKey="";statsPending="";stats=null;statsError="";if(statsRequest!=null)statsRequest.cancel(true);revision++;}
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
        if(statsRequest!=null)statsRequest.cancel(true);
        if(compareRequest!=null)compareRequest.cancel(true);
        if(startRequest!=null)startRequest.cancel(true);
        start=null;startKey="";startPending="";startError="";
        stats=null;statsKey="";statsPending="";statsError="";
        comparison=null;compareKey="";comparePending="";compareError="";
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
                String failure=ProfileParser.string(ProfileParser.at(json,"error"),"");if(!failure.isBlank())throw new IllegalStateException(failure);
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
                loading=false;message=e instanceof IllegalStateException&&e.getMessage()!=null&&!e.getMessage().isBlank()?e.getMessage():"Companion unavailable or timed out. Start it, then Load again. Demo works offline.";revision++;changed.run();
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
        p.enchantingLevel = 30; p.milestones.add(new Milestone("Heart of the Mountain",7,10,"tiers")); p.milestones.add(new Milestone("Mining skill",48,60,"levels")); p.progress.put("mithril powder (unspent)",480000.0); p.progress.put("gemstone powder (unspent)",620000.0); p.progress.put("glacite powder (unspent)",110000.0);
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
