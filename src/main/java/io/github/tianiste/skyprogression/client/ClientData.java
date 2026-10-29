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
    /** Hosted SkyProgression API (Cloudflare Worker, github.com/tianiste/SkyProgression-API); empty = the local companion. */
    static final String HOSTED_BACKEND = "https://api.skyprogression.dev";
    static final String COMPANION_BACKEND = "http://localhost:18475";
    List<Item> catalog = List.of();
    ProgressionEngine engine;
    Report report;
    String message = "Load your player, or use Demo to try the interface.";
    String player = "";
    int profileIndex, revision;
    boolean loading, demo, ready, initializing, initializationFailed;
    private int generation;
    private Future<?> lookup, priceRequest, statsRequest, compareRequest, startRequest;
    /** Backend base URL: the hosted API or the local companion (config/skyprogression.json "backend"). */
    String backend = HOSTED_BACKEND.isEmpty() ? COMPANION_BACKEND : HOSTED_BACKEND;
    /** Decoded profiles of the loaded player; the reports below are calculated from it on worker threads. */
    private volatile PlayerData playerData;
    private boolean profileRefreshing;
    /** Live gear from the game client (LiveGearWatcher) and the tab list's profile name ("" = not shown); null before any read. */
    private volatile LiveGear live;
    private String liveProfile = "";
    /** Current mayor and minister perks from the price bundle; null while unknown. */
    private volatile List<StatBreakdown.MayorPerk> mayor;
    /** Automatic stat ledger for {@link #statsKey}, calculated in the mod from the loaded profile. */
    StatsReport stats;
    String statsKey = "", statsPending = "", statsError = "";
    Map<String, Price> prices = new HashMap<>();
    /** Every method's automatic totals for the method comparison. */
    MethodComparison.Report comparison;
    String compareKey = "", comparePending = "", compareError = "";
    /** Best owned loadout and swaps for {@link #startKey}. */
    StartWith.Report start;
    String startKey = "", startPending = "", startError = "";
    MethodComparison.PriceMode priceMode = MethodComparison.PriceMode.INSTANT_SELL;
    /** How upgrade purchases are priced (Upgrades tab); instant-buy by default. */
    Model.CostMode costMode = Model.CostMode.INSTANT_BUY;
    boolean compareGodPotion = true;
    /** Method selected in /sp; kept while the screen is closed, so the session tracker follows it. */
    MiningMethod method = MiningMethod.MF_GEMS;
    /** Measured session of the selected method (fed by SessionWatcher on the game thread); hud shows its one-line summary. */
    final SessionTracker tracker = new SessionTracker(method);
    boolean trackerHud;
    private final Path sessions = FabricLoader.getInstance().getConfigDir().resolve("skyprogression-sessions.json");
    /** trackerIdleSeconds from config/skyprogression.json; 0 = the default. */
    private volatile long configIdleMs;
    final Map<String, Inputs> inputs = new HashMap<>();
    private HttpClient http;
    /** Newer release tag from GitHub ("" = none known); checked once per launch, never downloaded. */
    volatile String update = "";
    private static boolean updateChecked;
    private Set<String> catalogIds = Set.of();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("skyprogression-inputs.json");
    private final Path config = FabricLoader.getInstance().getConfigDir().resolve("skyprogression.json");
    /** How long the report waits for missing sale quotes before showing without them (they keep loading). */
    private static final long RESALE_WAIT_MS = 1500;
    private record Quote(long at, Price price) {}
    private final Map<Resale.Query, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<Resale.Query, CompletableFuture<Price>> quoting = new ConcurrentHashMap<>();
    private final Semaphore quoteSlots = new Semaphore(4);
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
                var items=Catalog.load();Icons.load();var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                Map<String,Inputs> saved=new HashMap<>();String warning="";
                try {
                    if(Files.exists(file))try(var stream=Files.newInputStream(file)) {
                        byte[] bytes=stream.readNBytes(1024*1024+1);
                        if(bytes.length>1024*1024)throw new IllegalArgumentException("Settings too large");
                        Map<String,Inputs> parsed=Catalog.GSON.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),new TypeToken<Map<String,Inputs>>(){}.getType());
                        if(parsed!=null)parsed.forEach((key,value)->{if(value!=null&&value.stats!=null&&value.stats.values().stream().allMatch(v->v!=null&&Double.isFinite(v)&&v>=0))saved.put(key,value);});
                    }
                }catch(Exception e){warning="Saved inputs could not be read. Re-enter them in Type Stats.";}
                String base=backend;
                try{base=readBackend();}catch(Exception e){warning="config/skyprogression.json has an invalid backend URL; using "+base+".";}
                Set<String> ids=new HashSet<>();items.forEach(i->ids.add(i.id));
                String notice=warning,chosen=base;
                dispatch.accept(()->{if(configIdleMs>0)tracker.idleMs=configIdleMs;catalog=items;catalogIds=ids;engine=new ProgressionEngine(items);http=client;backend=chosen;saved.forEach(inputs::putIfAbsent);ready=true;initializing=false;message=notice.isEmpty()?"Load your player, or try the offline Demo.":notice;revision++;changed.run();checkForUpdate(client,dispatch,changed);});
            }catch(Exception e){dispatch.accept(()->{initializing=false;initializationFailed=true;message="Could not initialize SkyProgression. Restart Minecraft to retry.";revision++;changed.run();});}
        });
    }
    /** Newest GitHub release of this mod with a jar for this Minecraft version, once per launch, off the game thread; any failure is silently ignored. */
    private void checkForUpdate(HttpClient client,Consumer<Runnable> dispatch,Runnable changed){
        if(updateChecked)return;updateChecked=true;
        String current=FabricLoader.getInstance().getModContainer("skyprogression").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("");
        String game=FabricLoader.getInstance().getModContainer("minecraft").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("");
        WORK.submit(()->{
            try{
                var request=HttpRequest.newBuilder(URI.create("https://api.github.com/repos/tianiste/SkyProgression/releases?per_page=10")).timeout(Duration.ofSeconds(5))
                        .header("Accept","application/vnd.github+json").header("User-Agent","SkyProgression/"+current).GET().build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
                byte[] bytes;try(var body=response.body()){if(response.statusCode()!=200)return;bytes=body.readNBytes(256*1024+1);}
                if(bytes.length>256*1024)return;
                // Releases come newest first; offer the first newer one that ships a jar for this game version.
                for(var element:JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
                    var release=element.getAsJsonObject();
                    if(release.get("draft").getAsBoolean()||release.get("prerelease").getAsBoolean())continue;
                    String tag=release.get("tag_name").getAsString().replaceFirst("^v","");
                    if(!newer(tag,current))continue;
                    for(var asset:release.getAsJsonArray("assets"))if(jarFor(asset.getAsJsonObject().get("name").getAsString(),game)){
                        dispatch.accept(()->{update=tag;revision++;changed.run();});return;
                    }
                }
            }catch(Exception ignored){/* No notice without a clear answer. */}
        });
    }
    /** True when a release asset is a mod jar for this game version: skyprogression-X+26.2.jar, or a range such as +26.1.1-26.1.2. */
    static boolean jarFor(String asset,String game){
        var match=java.util.regex.Pattern.compile("skyprogression-[^+]+\\+([0-9.]+)(?:-([0-9.]+))?\\.jar").matcher(asset);
        if(game.isEmpty()||!match.matches())return false;
        String low=match.group(1),high=match.group(2)==null?low:match.group(2);
        return !newer(low,game)&&!newer(game,high);
    }
    /** True when dotted version a is above b ("0.4.1" > "0.4.0"); anything unparsable is not newer. */
    static boolean newer(String a,String b){
        String[] x=a.split("[.+-]"),y=b.split("[.+-]");
        try{for(int i=0;i<3;i++){int u=i<x.length?Integer.parseInt(x[i]):0,v=i<y.length?Integer.parseInt(y[i]):0;if(u!=v)return u>v;}}catch(NumberFormatException e){return false;}
        return false;
    }
    /** "backend" from config/skyprogression.json (empty = default); the file is created with an empty value when missing. */
    private String readBackend() throws Exception {
        String fallback=HOSTED_BACKEND.isEmpty()?COMPANION_BACKEND:HOSTED_BACKEND;
        if(!Files.exists(config)){
            Files.createDirectories(config.getParent());
            Files.writeString(config,"{\n  \"backend\": \"\"\n}\n");
            return fallback;
        }
        byte[] bytes;try(var stream=Files.newInputStream(config)){bytes=stream.readNBytes(64*1024+1);}
        if(bytes.length>64*1024)throw new IllegalArgumentException("Config too large");
        JsonElement root=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        JsonElement idle=ProfileParser.at(root,"trackerIdleSeconds");
        if(idle!=null&&idle.isJsonPrimitive()&&idle.getAsJsonPrimitive().isNumber()){double seconds=idle.getAsDouble();if(seconds>=10&&seconds<=3600)configIdleMs=(long)(seconds*1000);}
        String value=ProfileParser.string(ProfileParser.at(root,"backend"),"").strip();
        if(value.isEmpty())return fallback;
        while(value.endsWith("/"))value=value.substring(0,value.length()-1);
        if(!value.matches("https?://[A-Za-z0-9.\\-\\[\\]:]+(/[A-Za-z0-9._~\\-/]*)?"))throw new IllegalArgumentException("Invalid backend URL");
        return value;
    }
    /** True when the backend is this machine (the companion), for messages. */
    boolean localBackend(){return backend.matches("http://(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d+)?(/.*)?");}
    private String backendName(){return localBackend()?"the local companion":"the SkyProgression API";}
    private String unreachable(){return "Could not reach "+backendName()+". "+(localBackend()?"Start the companion, then try again.":"Check your connection, then try again.");}
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
    /** A new live gear snapshot (game thread): the reports that use it recalculate on their next ensure call. */
    void live(LiveGear snapshot,String profileName){live=snapshot;liveProfile=profileName;revision++;}
    /**
     * Live gear for a loaded profile: only the game user's own profile, the one the tab list names (or, when it shows
     * none, the profile the API marks as selected); null otherwise, so other players and profiles stay API-only.
     */
    LiveGear liveFor(Profile p){
        LiveGear g=live;PlayerData d=playerData;
        if(g==null||demo||p==null||d==null)return null;
        var user=net.minecraft.client.Minecraft.getInstance().getUser();
        if(user==null||user.getProfileId()==null||!d.uuid.equals(user.getProfileId().toString().replace("-","").toLowerCase(Locale.ROOT)))return null;
        return (liveProfile.isEmpty()?p.selected:p.name.equalsIgnoreCase(liveProfile))?g:null;
    }
    private String liveKey(){LiveGear g=liveFor(profile());return g==null?"":"&live="+g.version;}
    // Keys name every selection a report depends on; a report is shown only while its key matches the current selections.
    private String statsQuery(MiningMethod method) {
        Profile p=profile();if(p==null||demo)return "";
        Inputs i=inputs(method);StatContext.Zone z=zone(method);
        return "/stats?ign="+enc(p.player)+"&profile="+enc(p.id)+"&method="+method.name()+"&gear="+(i.equippedGear?"equipped":"best")+"&tool="+enc(i.tool==null?"":i.tool)+"&zone="+(z==null?"":z.name())
                +"&target="+method.target().name()+"&god="+i.godPotion+liveKey();
    }
    private String startQuery(MiningMethod method) {
        Profile p=profile();if(p==null||demo)return "";
        Inputs i=inputs(method);StatContext.Zone z=zone(method);
        return "/start?ign="+enc(p.player)+"&profile="+enc(p.id)+"&method="+method.name()+"&tool="+enc(i.tool==null?"":i.tool)+"&zone="+(z==null?"":z.name())
                +"&target="+method.target().name()+"&god="+i.godPotion+liveKey();
    }
    /** Start-with report for the current selections, or null while missing, loading or failed. */
    StartWith.Report start(MiningMethod method){String key=startQuery(method);return !key.isEmpty()&&key.equals(startKey)?start:null;}
    boolean startLoading(MiningMethod method){String key=startQuery(method);return !key.isEmpty()&&key.equals(startPending);}
    String startError(MiningMethod method){String key=startQuery(method);return !key.isEmpty()&&key.equals(startKey)?startError:"";}
    void ensureStart(MiningMethod method,Consumer<Runnable> dispatch,Runnable changed) {
        String key=startQuery(method);
        if(!ready||loading||key.isEmpty()||key.equals(startKey)||key.equals(startPending))return;
        PlayerData d=playerData;if(d==null)return;
        refreshProfileIfStale(dispatch);
        if(startRequest!=null)startRequest.cancel(true);
        startPending=key;startError="";int requestGeneration=generation;
        String profileId=profile().id,tool=inputs(method).tool==null?"":inputs(method).tool;StatContext context=new StatContext(zone(method),method.target(),inputs(method).godPotion);LiveGear g=liveFor(profile());
        startRequest=WORK.submit(()->{
            StartWith.Report result=null;String error="";
            try{result=d.start(profileId,method,tool,context,g);}
            catch(NoSuchElementException e){error=e.getMessage();}
            catch(java.util.concurrent.CancellationException e){return;}
            catch(Exception e){error="Could not work out the best owned loadout for this profile.";}
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
    /** Calculates the ledger when the selections changed; safe to call on every screen rebuild. */
    void ensureStats(MiningMethod method,Consumer<Runnable> dispatch,Runnable changed) {
        String key=statsQuery(method);
        if(!ready||loading||key.isEmpty()||key.equals(statsKey)||key.equals(statsPending))return;
        PlayerData d=playerData;if(d==null)return;
        refreshProfileIfStale(dispatch);
        if(statsRequest!=null)statsRequest.cancel(true);
        statsPending=key;statsError="";int requestGeneration=generation;
        Inputs i=inputs(method);String profileId=profile().id,tool=i.tool==null?"":i.tool;boolean best=!i.equippedGear;
        StatContext context=new StatContext(zone(method),method.target(),i.godPotion);List<StatBreakdown.MayorPerk> perks=mayor;String base=backend;LiveGear g=liveFor(profile());
        statsRequest=WORK.submit(()->{
            StatsReport result=null;String error="";Map<String,CompletableFuture<Price>> late=Map.of();
            try{result=d.stats(profileId,method,tool,best,context,perks,g);late=resale(base,result.resaleQueries,result.resale);result.resalePending=!late.isEmpty();}
            catch(NoSuchElementException e){error=e.getMessage();}
            catch(java.util.concurrent.CancellationException e){return;}
            catch(Exception e){error="Could not calculate the stat breakdown for this profile.";}
            StatsReport loaded=result;String failure=error;Map<String,CompletableFuture<Price>> pending=late;
            dispatch.accept(()->{
                if(requestGeneration!=generation||!key.equals(statsPending))return;
                statsPending="";statsKey=key;stats=loaded;statsError=failure;revision++;changed.run();
                if(loaded!=null&&!pending.isEmpty())completeResale(loaded,pending,dispatch,changed);
            });
        });
    }
    /**
     * Sale quotes for the evaluated copies (modifier-aware lowest BIN through the backend). Waits at most
     * {@link #RESALE_WAIT_MS} in total; returns the lookups still running, which keep filling the quote cache.
     */
    private Map<String,CompletableFuture<Price>> resale(String base,Map<String,Resale.Query> queries,Map<String,Price> out) {
        Map<String,CompletableFuture<Price>> jobs=new LinkedHashMap<>();
        for(var e:queries.entrySet()){
            Resale.Query q=e.getValue();
            if(!q.tag().matches("[A-Z0-9_]{1,64}")||!q.tag().startsWith("PET_")&&!catalogIds.contains(q.tag()))continue;
            Quote hit=quotes.get(q);
            if(hit!=null&&System.currentTimeMillis()-hit.at()<900_000){if(hit.price()!=null)out.put(e.getKey(),hit.price());continue;}
            jobs.put(e.getKey(),quoting.computeIfAbsent(q,key->CompletableFuture.supplyAsync(()->quote(base,key),WORK).whenComplete((p,t)->quoting.remove(key))));
        }
        long deadline=System.nanoTime()+RESALE_WAIT_MS*1_000_000;
        Map<String,CompletableFuture<Price>> late=new LinkedHashMap<>();
        for(var e:jobs.entrySet()){
            try{Price p=e.getValue().get(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);if(p!=null)out.put(e.getKey(),p);}
            catch(TimeoutException t){late.put(e.getKey(),e.getValue());}
            catch(Exception ignored){/* No quote: the upgrade says there is no sale price. */}
        }
        return late;
    }
    private Price quote(String base,Resale.Query q) {
        StringBuilder url=new StringBuilder(base).append("/resale?tag=").append(q.tag());
        q.filters().forEach((name,value)->url.append('&').append(enc(name)).append('=').append(enc(value)));
        Price price=null;
        try{
            quoteSlots.acquire();
            try{JsonElement json=fetch(url.toString(),20,256*1024).getAsJsonObject().get("price");if(json!=null&&json.isJsonObject())price=Catalog.GSON.fromJson(json,Price.class);}
            finally{quoteSlots.release();}
            quotes.put(q,new Quote(System.currentTimeMillis(),price));
        }catch(Exception ignored){/* Unknown this time; asked again on the next report. */}
        return price;
    }
    /** Swaps the late sale quotes into the shown report once they have all arrived. */
    private void completeResale(StatsReport report,Map<String,CompletableFuture<Price>> pending,Consumer<Runnable> dispatch,Runnable changed) {
        CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).orTimeout(45,TimeUnit.SECONDS).whenComplete((ignored,t)->{
            Map<String,Price> found=new HashMap<>();
            pending.forEach((key,job)->{Price p=job.getNow(null);if(p!=null)found.put(key,p);});
            dispatch.accept(()->{
                if(stats!=report)return;
                // A new map, so a calculation still reading the old one is not disturbed.
                Map<String,Price> merged=new HashMap<>(report.resale);merged.putAll(found);
                report.resale=merged;report.resalePending=false;revision++;changed.run();
            });
        });
    }
    private String compareQuery(){Profile p=profile();return p==null||demo?"":"/compare?ign="+enc(p.player)+"&profile="+enc(p.id)+"&god="+compareGodPotion+liveKey();}
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
        PlayerData d=playerData;if(d==null)return;
        refreshProfileIfStale(dispatch);
        if(compareRequest!=null)compareRequest.cancel(true);
        comparePending=key;compareError="";int requestGeneration=generation;
        String profileId=profile().id;boolean god=compareGodPotion;LiveGear g=liveFor(profile());
        compareRequest=WORK.submit(()->{
            MethodComparison.Report result=null;String error="";
            try{result=d.compare(profileId,god,g);}
            catch(NoSuchElementException e){error=e.getMessage();}
            catch(java.util.concurrent.CancellationException e){return;}
            catch(Exception e){error="Could not compare methods for this profile.";}
            MethodComparison.Report loaded=result;String failure=error;
            dispatch.accept(()->{
                if(requestGeneration!=generation||!key.equals(comparePending))return;
                comparePending="";compareKey=key;comparison=loaded;compareError=failure;revision++;changed.run();
            });
        });
    }
    void refreshCompare(){compareKey="";comparePending="";comparison=null;compareError="";if(compareRequest!=null)compareRequest.cancel(true);revision++;}
    void refreshStats(){statsKey="";statsPending="";stats=null;statsError="";if(statsRequest!=null)statsRequest.cancel(true);revision++;}
    /**
     * Profiles older than five minutes are refetched in the background; the reports keep using the decoded copy until the
     * new one arrives, and the next calculation uses it (an explicit Load always waits for fresh data).
     */
    private void refreshProfileIfStale(Consumer<Runnable> dispatch) {
        PlayerData d=playerData;
        if(d==null||profileRefreshing||System.currentTimeMillis()-d.fetchedAtMillis<300_000)return;
        profileRefreshing=true;int requestGeneration=generation;String base=backend;
        WORK.submit(()->{
            PlayerData fresh=null;
            try{fresh=fetchProfile(base,d.player,d.uuid,false);}catch(Exception ignored){/* Keep the decoded copy; retried on a later calculation. */}
            PlayerData loaded=fresh;
            dispatch.accept(()->{profileRefreshing=false;if(loaded!=null&&requestGeneration==generation&&playerData==d)playerData=loaded;});
        });
    }
    private PlayerData fetchProfile(String base,String name,String uuid,boolean fresh) throws Exception {
        var response=BoundedHttp.get(http,URI.create(base+"/profile?uuid="+uuid+(fresh?"&fresh=1":"")),Duration.ofSeconds(40),24L*1024*1024);
        if(response.statusCode()!=200)throw new IllegalStateException(errorMessage(response));
        String fetchedAt=response.headers().firstValue("X-Fetched-At").orElse(java.time.Instant.now().toString());
        try{return new PlayerData(name,uuid,response.body(),fetchedAt);}
        catch(IllegalArgumentException|JsonParseException e){throw new IllegalStateException("Hypixel could not return this profile.");}
    }
    /**
     * The model's rate for the method with the current stats, efficiency, tool ability and zone: product per hour (as on Rate now) and
     * coins per hour with the modelled secondary drops (null = unknown). Null when the rate cannot be computed. Cached per revision.
     */
    record Prediction(Double perHour,Double coinsPerHour,double efficiency) {}
    private Prediction prediction;private int predictionRevision=-1;private MiningMethod predictionMethod;private MethodComparison.PriceMode predictionMode;
    Prediction predicted(MiningMethod m){
        if(predictionRevision==revision&&predictionMethod==m&&predictionMode==priceMode)return prediction;
        Prediction p=null;
        var e=effective(m);double efficiency=inputs(m).efficiency;
        if(!e.isEmpty()&&MethodRates.supported(m))try{
            StatsReport r=stats(m);var ability=r==null?null:r.ability;StatContext.Zone z=zone(m);
            double main=MethodRates.perHour(m,e,efficiency,ability,z);
            Double each=MethodComparison.coinsPerItem(prices,m,priceMode);
            Double coins=null;
            if(each!=null&&Double.isFinite(main)){double sum=main*each;
                for(var d:MethodRates.secondary(m,e,efficiency,ability,z)){var v=MethodComparison.value(d,prices,priceMode);if(v.coinsPerHour()!=null)sum+=v.coinsPerHour();}
                coins=sum;}
            p=new Prediction(Double.isFinite(main)?main:null,coins,efficiency);
        }catch(IllegalArgumentException ignored){/* No rate with these stats. */}
        prediction=p;predictionRevision=revision;predictionMethod=m;predictionMode=priceMode;return p;
    }
    /**
     * Appends a finished session to config/skyprogression-sessions.json on the disk worker (read, append, write a temporary file,
     * move); complete gets the number of saved sessions, or -1 when it failed.
     */
    void saveSession(JsonObject session,Consumer<Runnable> dispatch,Consumer<Integer> complete){
        DISK.submit(()->{
            int count=-1;
            try{
                JsonArray all=new JsonArray();
                if(Files.exists(sessions))try(var stream=Files.newInputStream(sessions)){
                    byte[] bytes=stream.readNBytes(8*1024*1024+1);
                    if(bytes.length>8*1024*1024)throw new IllegalStateException("Session file too large");
                    JsonElement old=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                    // Never overwrite a file that is not a list of sessions.
                    if(!old.isJsonArray())throw new IllegalStateException("Not a session list");
                    all=old.getAsJsonArray();
                }
                all.add(session);
                Files.createDirectories(sessions.getParent());
                Path temporary=sessions.resolveSibling(sessions.getFileName()+".tmp");
                Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(all));
                Files.move(temporary,sessions,StandardCopyOption.REPLACE_EXISTING);count=all.size();
            }catch(Exception ignored){}
            int saved=count;dispatch.accept(()->complete.accept(saved));
        });
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
        generation++;revision++;loading=false;profileRefreshing=false;
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
        cancel();int requestGeneration=generation;String base=backend;
        // Your own name needs no lookup: the game session already has its UUID.
        var user=net.minecraft.client.Minecraft.getInstance().getUser();
        String own=user!=null&&ign.equalsIgnoreCase(user.getName())&&user.getProfileId()!=null?user.getProfileId().toString().replace("-",""):"";
        loading=true;message="Loading "+ign+"...";report=null;playerData=null;demo=false;prices=new HashMap<>();revision++;changed.run();
        lookup=WORK.submit(()->{
            try {
                String[] identity=own.isEmpty()?identity(base,ign):new String[]{own,ign};
                PlayerData d=fetchProfile(base,identity[1],identity[0],true);
                if(d.report.profiles.isEmpty())throw new IllegalStateException("No SkyBlock profiles found for "+ign+".");
                // Decode the selected profile's items now, so the first report does not wait for it.
                try{d.member("");}catch(Exception ignored){}
                dispatch.accept(()->{
                    if(requestGeneration!=generation)return;
                    loading=false;playerData=d;report=d.report;player=ign;profileIndex=0;
                    for(int n=0;n<d.report.profiles.size();n++)if(d.report.profiles.get(n).selected)profileIndex=n;
                    message="Profile loaded. Prices loading...";loadPrices(dispatch,changed);revision++;changed.run();
                });
            }catch(Exception e){dispatch.accept(()->{
                if(requestGeneration!=generation)return;
                loading=false;message=(e instanceof IllegalStateException&&e.getMessage()!=null&&!e.getMessage().isBlank()?e.getMessage():unreachable())+" Demo works offline.";revision++;changed.run();
            });}
        });
    }
    /**
     * UUID and name for a Minecraft name: asked of Mojang directly (Mojang refuses Cloudflare's addresses, so the hosted
     * API cannot do it reliably), with the backend's /uuid as the fallback when Mojang cannot be reached.
     */
    private String[] identity(String base,String ign) throws Exception {
        for(String url:List.of("https://api.minecraftservices.com/minecraft/profile/lookup/name/"+ign,"https://api.mojang.com/users/profiles/minecraft/"+ign)){
            try{
                var response=BoundedHttp.get(http,URI.create(url),Duration.ofSeconds(10),64*1024);
                if(response.statusCode()==404||response.statusCode()==204)throw new IllegalStateException("Minecraft player not found.");
                if(response.statusCode()!=200)continue;
                JsonObject json=JsonParser.parseString(response.body()).getAsJsonObject();
                String uuid=ProfileParser.string(json.get("id"),"");
                if(uuid.matches("[0-9a-fA-F]{32}"))return new String[]{uuid.toLowerCase(Locale.ROOT),ProfileParser.string(json.get("name"),ign)};
            }catch(IllegalStateException e){throw e;}
            catch(Exception ignored){/* Try the next host, then the backend. */}
        }
        JsonObject json=fetch(base+"/uuid?name="+ign,15,64*1024).getAsJsonObject();
        String uuid=ProfileParser.string(json.get("id"),"");
        if(!uuid.matches("[0-9a-fA-F]{32}"))throw new IllegalStateException("Minecraft player not found.");
        return new String[]{uuid,ProfileParser.string(json.get("name"),ign)};
    }
    void loadPrices(Consumer<Runnable> dispatch,Runnable changed) {
        int requestGeneration=generation;String base=backend;
        priceRequest=WORK.submit(()->{
            Map<String,Price> loaded=Map.of();List<StatBreakdown.MayorPerk> perks=null;boolean complete=false;String error="";
            try {
                JsonObject bundle=fetch(base+"/prices",60,8*1024*1024).getAsJsonObject();
                Map<String,Price> parsed=Catalog.GSON.fromJson(bundle.get("prices"),new TypeToken<Map<String,Price>>(){}.getType());
                if(parsed!=null)loaded=parsed;
                JsonElement m=bundle.get("mayor");
                if(m!=null&&m.isJsonArray())perks=List.copyOf(Catalog.GSON.<List<StatBreakdown.MayorPerk>>fromJson(m,new TypeToken<List<StatBreakdown.MayorPerk>>(){}.getType()));
                complete=!bundle.has("complete")||bundle.get("complete").getAsBoolean();
            }catch(IllegalStateException e){error=e.getMessage();}
            catch(Exception e){error=unreachable();}
            Map<String,Price> result=loaded;List<StatBreakdown.MayorPerk> mayorPerks=perks;boolean full=complete;String failure=error;
            dispatch.accept(()->{
                if(demo||requestGeneration!=generation)return;
                prices=result;
                // The ledger lists the mayor's temporary perks: recalculate it once they are known.
                if(mayor==null&&mayorPerks!=null){mayor=mayorPerks;refreshStats();}else if(mayorPerks!=null)mayor=mayorPerks;
                message=result.isEmpty()?"Prices unavailable"+(failure.isEmpty()?"":" ("+failure+")")+". Costs remain unknown."
                        :"Loaded "+result.size()+" price quotes"+(full?"":" (the server is still filling in the rest)")+". Socket fees included.";
                revision++;changed.run();
            });
        });
    }
    /** GET a JSON answer from the backend; a non-200 answer becomes an IllegalStateException with a readable message. */
    private JsonElement fetch(String url,int timeout,long maxBytes) throws Exception {
        var response=BoundedHttp.get(http,URI.create(url),Duration.ofSeconds(timeout),maxBytes);
        if(response.statusCode()!=200)throw new IllegalStateException(errorMessage(response));
        return JsonParser.parseString(response.body());
    }
    private String errorMessage(HttpResponse<String> response) {
        String error="";
        try{error=ProfileParser.string(ProfileParser.at(JsonParser.parseString(response.body()),"error"),"");}catch(Exception ignored){}
        if(response.statusCode()==404&&"Unknown endpoint".equals(error))return "The companion is older than the mod. Restart it.";
        if(!error.isBlank())return error;
        return response.statusCode()==429?"Rate limited. Wait a minute, then try again.":backendName().substring(0,1).toUpperCase(Locale.ROOT)+backendName().substring(1)+" answered with error "+response.statusCode()+".";
    }
    void demo() {
        if (!ready) return;
        cancel();
        demo = true; profileIndex = 0; report = new Report(); playerData = null;
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
