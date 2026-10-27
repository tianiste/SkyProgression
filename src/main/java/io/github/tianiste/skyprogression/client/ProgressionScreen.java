package io.github.tianiste.skyprogression.client;

import io.github.tianiste.skyprogression.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.concurrent.Future;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.client.Theme.*;

/**
 * Four icon tabs (Home, Gear, Upgrades, Methods) plus a More page behind the settings button. Cards hold typed rows
 * (text, stat meters, stacked source bars, item entries, a slot grid, a big number, bar-chart rows); details sit in
 * hover tooltips or expandable lines. Wrapping, fitting and tooltip splitting happen when content changes, never per frame.
 */
public final class ProgressionScreen extends Screen {
    private final ClientData data=ClientData.INSTANCE;
    private MiningMethod method=ClientData.INSTANCE.method;
    private Stat stat=Stat.MINING_FORTUNE;
    /** Gear tab stat group: "Method" (the method's stats plus the focused one), "All", or a StatBreakdown.GROUPS key. */
    private String statGroup="Method";
    /** Upgrades tab: false ranks by rate gain with the step-by-step plan; true ranks the focused stat's cheapest points. */
    private boolean byStat;
    /** Upgrades tab: false shows purchasable upgrades; true shows bonuses the API can't check, grinding and the guide stage. */
    private boolean otherView;
    private OtherUpgrades.Report other;
    /** Gear tab: key of the clicked loadout slot (empty = none); keys of expanded lines. */
    private String slot="";
    private final Set<String> expanded=new HashSet<>();
    private boolean autoLoadTried;
    private int lastRevision, tab, left, top, span, bottom, bodyTop, bodyBottom, scroll, contentHeight, analysisGeneration;
    private boolean wide, calculating;
    private EditBox username;
    private Future<?> analysis;
    private List<Overview> overview=List.of();
    private List<Upgrade> upgrades=List.of();
    private List<ProgressionEngine.Step> plan=List.of();
    /** Best method by coins/hour and each upgrade's rate gain there (same stat change on that method's stats); empty before the comparison loads. */
    private MethodComparison.Row bestRow;
    private Map<Upgrade,Double> bestGains=new IdentityHashMap<>();
    private final List<Card> cards=new ArrayList<>();
    private final List<Placed> placed=new ArrayList<>();
    private String footerState="", footerMessage="", footerKeys="";
    private int footerStateColor=MUTED, footerMessageX, footerKeysX;
    private static final int HOME=0,GEAR=1,UPGRADES=2,METHODS=3,MORE=4,FULL=2,AUTO=3;
    private static final String[] TABS={"Home","Gear","Upgrades","Methods"};

    /** One card row. Rows that show a tooltip or react to a click expose tip() / click(). */
    private interface Row { default String tip(){return null;} default Runnable click(){return null;} }
    private record Text(String text,int color,String tip,Runnable click) implements Row {}
    private record Meter(String label,String value,int valueColor,double progress,int color,String tip,Runnable click) implements Row {}
    private record Seg(String label,double amount,int color) {}
    /** Stacked bar: segments by source category over a bar worth {@code scale}. */
    private record Split(List<Seg> segs,double scale) implements Row {}
    private record Legend(List<Seg> segs) implements Row {}
    private record Pair(String left,int leftColor,String right,int rightColor,String tip,Runnable click) implements Row {}
    private record Entry(ItemStack icon,String title,int titleColor,String sub,int subColor,String right,int rightColor,String right2,int right2Color,String tip,Runnable click) implements Row {}
    private record Slot(ItemStack icon,int outline,boolean selected,String tip,Runnable click) {}
    private record Slots(List<Slot> slots) implements Row {}
    private record Big(String value,String unit,int color,String tip,Runnable click) implements Row {}
    private record Bar(ItemStack icon,String label,int labelColor,String value,double fraction,int color,String tip,Runnable click) implements Row {}
    private static final class Card {
        ItemStack icon=ItemStack.EMPTY;
        final String title;final int accent,column;
        String right="",tip;int rightColor=MUTED;
        /** Dropped on short screens. */
        boolean optional;
        final List<Row> rows=new ArrayList<>();
        Card(String title,int accent,int column){this.title=title;this.accent=accent;this.column=column;}
        Card add(Row row){rows.add(row);return this;}
        Card line(String value,int color){return add(new Text(value,color,null,null));}
        Card line(String value,int color,String tip){return add(new Text(value,color,tip,null));}
        Card pair(String left,String right,int color){return add(new Pair(left,MUTED,right,color,null,null));}
    }
    /** A row after layout: offset in the card, height, fitted/wrapped text, split tooltip and item positions. */
    private record Laid(Row row,int dy,int h,List<String> text,List<FormattedCharSequence> tip,int[] pos) {}
    private record Placed(Card card,int x,int y,int width,int height,String title,int rightX,List<FormattedCharSequence> tip,List<Laid> rows) {}

    public ProgressionScreen(){super(Component.literal("SkyProgression"));}
    private Theme.Action button(String label,int x,int y,int w,boolean selected,Runnable action){
        var b=new Theme.Action(label,x,y,w,20,selected,GOLD,action);addRenderableWidget(b);return b;
    }
    private record Control(String label,boolean selected,boolean active,Runnable action) {}
    /** Lays the controls evenly in as many rows of at least 104 px buttons as needed; returns the y below them. */
    private int controls(int y,List<Control> list){
        if(list.isEmpty())return y;
        int perRow=Math.max(1,Math.min(list.size(),(span-18+4)/108)),rows=(list.size()+perRow-1)/perRow;
        for(int r=0;r<rows;r++){int from=r*perRow,to=Math.min(list.size(),from+perRow),n=to-from,w=(span-18-4*(n-1))/n;
            for(int k=0;k<n;k++){var c=list.get(from+k);button(c.label,left+9+k*(w+4),y,k==n-1?span-18-(n-1)*(w+4):w,c.selected,c.action).active=c.active;}
            y+=24;}
        return y;
    }
    @Override protected void init(){
        String typed=username!=null?username.getValue():data.player.isEmpty()?minecraft.getUser().getName():data.player;
        data.method=method;
        clearWidgets();span=Math.min(960,width-16);left=(width-span)/2;top=Math.max(4,(height-620)/2);bottom=height-top;
        wide=span>=600;
        int y=top+6;boolean labels=span>=460,oneRow=span>=860;
        int tabW=labels?74:26,tabX=left+(span>=520?104:9);
        ItemStack[] icons={new ItemStack(Items.COMPASS),new ItemStack(Items.DIAMOND_CHESTPLATE),new ItemStack(Items.EXPERIENCE_BOTTLE),new ItemStack(Items.DIAMOND_PICKAXE)};
        for(int i=0;i<TABS.length;i++){int t=i;addRenderableWidget(new Theme.IconTab(TABS[i],icons[i],tabX+i*(tabW+3),y,tabW,22,tab==i,()->switchTab(t)));}
        button("X",left+span-28,y+1,20,false,this::onClose);
        addRenderableWidget(new Theme.IconTab("More: Type Stats, stat focus, profile, Demo and help (F1)",new ItemStack(Items.COMPARATOR),left+span-54,y,23,22,tab==MORE,()->switchTab(MORE)));
        Profile p=data.profile();
        String profile=p==null?"Profile":p.name+" ["+p.mode+"]";
        int rowY=oneRow?y+1:y+27,nameWidth,nameX;
        if(oneRow){
            int methodX=left+span-58-112,profileX=methodX-4-122;
            button(profile+" v",profileX,rowY,122,false,this::selectProfile).active=p!=null;
            button(method.label+" v",methodX,rowY,112,false,this::selectMethod);
            nameWidth=92;nameX=profileX-4-44-4-nameWidth;
        }else{
            nameWidth=Math.min(120,Math.max(60,(span-18)/3-24));nameX=left+9;
            int rest=span-18-nameWidth-48,w=(rest-8)/2,x=nameX+nameWidth+52;
            button(profile+" v",x,rowY,w,false,this::selectProfile).active=p!=null;
            button(method.label+" v",x+w+4,rowY,rest-w-4,false,this::selectMethod);
        }
        username=new EditBox(font,nameX,rowY,nameWidth,20,Component.literal("Minecraft username"));
        username.setMaxLength(16);username.setValue(typed);username.setTextColor(CYAN);username.setTooltip(Tooltip.create(Component.literal("Minecraft username. Load (or R) reads the profile from the SkyProgression API or your local companion.")));addRenderableWidget(username);
        button(data.loading?"Stop":"Load",nameX+nameWidth+4,rowY,44,false,()->{if(data.loading){data.cancel();refresh();}else data.load(username.getValue(),minecraft::execute,this::refresh);}).active=data.ready;
        y=rowY+26;
        y=controls(y,switch(tab){case GEAR->gearControls();case UPGRADES->upgradeControls();case METHODS->methodControls();case MORE->moreControls();default->List.of();});
        bodyTop=y+2;bodyBottom=bottom-28;
        button("<",left+9,bottom-24,20,false,()->scroll=Math.max(0,scroll-page()));
        button(">",left+31,bottom-24,20,false,()->scroll=Math.min(maxScroll(),scroll+page()));
        footer(p);
        data.initialize(minecraft::execute,this::refresh);
        data.ensureStats(method,minecraft::execute,this::refresh);
        if(tab!=MORE)data.ensureCompare(minecraft::execute,this::refresh);
        if(tab==GEAR)data.ensureStart(method,minecraft::execute,this::refresh);
        lastRevision=data.revision;analyze();
    }
    /** Footer text, fitted once per rebuild (the message only changes through a revision, which rebuilds). */
    private void footer(Profile p){
        footerState=data.loading?"LOADING":data.demo?"DEMO":p==null?"OFFLINE":"LOADED";
        footerStateColor=data.demo?GOLD:data.loading?CYAN:p==null?MUTED:GREEN;
        footerKeys=wide?"R Reload  |  F1 Help  |  ESC Close":"";
        footerKeysX=left+span-10-font.width(footerKeys);
        footerMessageX=left+58+font.width(footerState)+8;
        footerMessage=fit(font,data.message,(wide?footerKeysX-12:left+span-10)-footerMessageX);
    }
    private int page(){return Math.max(30,bodyBottom-bodyTop-20);}
    private void switchTab(int t){tab=t;scroll=0;refresh();}
    @Override public void tick(){
        // Load the typed player once per opening, as soon as the catalog is ready; a kept profile or Demo is left as is.
        if(!autoLoadTried&&data.ready&&data.report==null&&!data.loading&&!data.demo&&username!=null){autoLoadTried=true;data.load(username.getValue(),minecraft::execute,this::refresh);}
        if(lastRevision!=data.revision){refresh();return;}
        // The session card changes without a revision: rebuild the cards (not the widgets) when it changed, or each second while it runs.
        if(tab==HOME||tab==METHODS){var t=data.tracker;long second=System.currentTimeMillis()/1000;
            if(t.version()!=trackerVersion||t.state()==SessionTracker.State.RUNNING&&second!=trackerSecond){trackerVersion=t.version();trackerSecond=second;buildCards();}}
    }
    private int trackerVersion=-1;private long trackerSecond;
    void refresh(){if(Compat.screen(minecraft)==this)init();}
    private void typeStats(){Compat.setScreen(minecraft,new InputsScreen(this,data.inputs(method),method));}
    private void persist(){data.save(minecraft::execute,ignored->{});}
    private boolean live(){return data.profile()!=null&&!data.demo;}
    private void cyclePrice(){var modes=MethodComparison.PriceMode.values();data.priceMode=modes[(data.priceMode.ordinal()+1)%modes.length];refresh();}

    // ---- Controls ----
    /** Gear: the Breakdown and Start with selections; Refresh reloads both. */
    private List<Control> gearControls(){
        var i=data.inputs(method);boolean live=live();StatContext.Zone z=data.zone(method);
        return List.of(new Control("Stats: "+statGroup+" v",false,live,this::selectGroup),
                new Control("Gear: "+(i.equippedGear?"Equipped":"Best owned"),!i.equippedGear,live,()->{i.equippedGear=!i.equippedGear;persist();refresh();}),
                new Control("Tool: "+toolName(i.tool)+" v",false,live,this::selectTool),
                new Control("Zone: "+(z==null?"Not chosen":z.label)+" v",false,live,this::selectZone),
                new Control("God Potion: "+(i.godPotion?"On":"Off"),i.godPotion,live,()->{i.godPotion=!i.godPotion;persist();refresh();}),
                new Control("Price: "+data.priceMode.label,true,true,this::cyclePrice),
                new Control(data.statsLoading(method)||data.startLoading(method)?"Loading...":"Refresh",false,live&&!data.statsLoading(method)&&!data.startLoading(method),
                        ()->{data.refreshStats();data.refreshStart();data.loadPrices(minecraft::execute,this::refresh);refresh();}));
    }
    private List<Control> upgradeControls(){
        List<Control> list=new ArrayList<>();
        list.add(new Control(otherView?"View: Other & progress":"View: Purchases",otherView,true,()->{otherView=!otherView;scroll=0;refresh();}));
        if(otherView)return list;
        list.add(new Control(byStat?"Rank: "+stat.label+" points":"Rank: rate gain",byStat,true,()->{byStat=!byStat;scroll=0;refresh();}));
        if(byStat)list.add(new Control("Stat: "+stat.label+" v",false,true,this::selectStat));
        list.add(new Control("Price: "+data.priceMode.label,true,true,this::cyclePrice));
        list.add(new Control("Type Stats",false,data.ready,this::typeStats));
        return list;
    }
    private List<Control> methodControls(){
        boolean live=live();var t=data.tracker;boolean running=t.state()==SessionTracker.State.RUNNING||t.state()==SessionTracker.State.PAUSED;
        return List.of(new Control(running?"Session: Stop":t.state()==SessionTracker.State.STOPPED?"Session: Resume":"Session: Start",running,true,
                        ()->{long now=System.currentTimeMillis();if(running)t.stop(now);else{t.use(method);t.start(now);}sessionNote="";refresh();}),
                new Control("Reset session",false,!t.empty(),()->{t.reset();t.use(method);sessionNote="";refresh();}),
                new Control("Save session",false,t.state()==SessionTracker.State.STOPPED,this::saveSession),
                new Control("Auto-start: "+(t.autoStart?"On":"Off"),t.autoStart,true,()->{t.autoStart=!t.autoStart;refresh();}),
                new Control("HUD: "+(data.trackerHud?"On":"Off"),data.trackerHud,true,()->{data.trackerHud=!data.trackerHud;refresh();}),
                new Control("Price: "+data.priceMode.label,true,true,this::cyclePrice),
                new Control("God Potion: "+(data.compareGodPotion?"On":"Off"),data.compareGodPotion,live,()->{data.compareGodPotion=!data.compareGodPotion;scroll=0;refresh();}),
                new Control("Pick method v",false,!data.comparisonRows().isEmpty(),this::pickRanked),
                new Control(data.compareLoading()?"Loading...":"Refresh",false,live&&!data.compareLoading(),()->{data.refreshCompare();data.loadPrices(minecraft::execute,this::refresh);refresh();}));
    }
    private List<Control> moreControls(){
        return List.of(new Control("Type Stats",false,data.ready,this::typeStats),
                new Control("Stat focus: "+stat.label+" v",false,true,this::selectStat),
                new Control("Demo",data.demo,data.ready,()->{data.demo();scroll=0;refresh();}));
    }
    private void selectGroup(){
        List<String> groups=new ArrayList<>();groups.add("Method");groups.addAll(StatBreakdown.GROUPS.keySet());groups.add("All");
        List<String> labels=groups.stream().map(g->g.equals("Method")?"Method: stats that change "+method.label+" rates":g.equals("All")?"All stats":g+": "+String.join(", ",StatBreakdown.GROUPS.get(g).stream().map(s->s.label).toList())).toList();
        Compat.setScreen(minecraft,new ChoiceScreen(this,"Stat group",labels,n->{statGroup=groups.get(n);scroll=0;}));
    }
    private void pickRanked(){
        var rows=data.comparisonRows();int n=0;List<String> labels=new ArrayList<>();
        for(var r:rows)labels.add((++n)+". "+r.method().label+" - "+(r.totalCoinsPerHour()==null?"coins unknown":compact(r.totalCoinsPerHour())+" coins / hour"));
        Compat.setScreen(minecraft,new ChoiceScreen(this,"Methods by coins / hour",labels,i->{method=rows.get(i).method();scroll=0;}));
    }
    /** Tool list of the last ledger for this profile; it does not change with the selections. */
    private List<StatsReport.Tool> tools(){Profile p=data.profile();return data.stats!=null&&p!=null&&data.stats.profileId.equals(p.id)?data.stats.tools:List.of();}
    private String toolName(String key){
        if(key==null||key.isEmpty()){StatsReport r=data.stats(method);
            return r==null?"Auto":r.tool.isEmpty()?"None owned":"Auto, "+tools().stream().filter(t->t.key().equals(r.tool)).map(StatsReport.Tool::name).findFirst().orElse("best");}
        return tools().stream().filter(t->t.key().equals(key)).map(StatsReport.Tool::name).findFirst().orElse(tools().isEmpty()?"Selected":"Not found");
    }
    /** The tool held now: the stat ledger evaluates it, and Start with compares the loadout worn with it. */
    private void selectTool(){
        var list=tools();List<String> labels=new ArrayList<>();labels.add("Best owned tool for "+method.label+" (automatic)");
        for(var t:list)labels.add(t.name()+" ("+t.location()+")");
        var i=data.inputs(method);
        Compat.setScreen(minecraft,new ChoiceScreen(this,"Tool you hold",labels,n->{i.tool=n==0?"":list.get(n-1).key();scroll=0;persist();}));
    }
    private void selectZone(){
        var zones=StatContext.Zone.values();List<String> labels=new ArrayList<>();labels.add("Usual for "+method.label+" ("+method.defaultZone().label+")");
        for(var z:zones)labels.add(z.label);labels.add("Not chosen (location bonuses not counted)");
        var i=data.inputs(method);
        Compat.setScreen(minecraft,new ChoiceScreen(this,"Mining zone",labels,n->{i.zone=n==0?"":n>zones.length?"-":zones[n-1].name();scroll=0;persist();}));
    }
    private void selectMethod(){Compat.setScreen(minecraft,new ChoiceScreen(this,"Mining method",Arrays.stream(MiningMethod.values()).map(m->m.label).toList(),n->{method=MiningMethod.values()[n];scroll=0;slot="";}));}
    private void selectStat(){Compat.setScreen(minecraft,new ChoiceScreen(this,"Stat focus",Arrays.stream(Stat.values()).map(s->s.label).toList(),n->{stat=Stat.values()[n];scroll=0;}));}
    private void selectProfile(){if(data.report!=null)Compat.setScreen(minecraft,new ChoiceScreen(this,"SkyBlock profile",data.report.profiles.stream().map(p->p.name+" ["+p.mode+"]"+(p.selected?" - active":"")).toList(),n->{data.profileIndex=n;scroll=0;slot="";}));}

    // ---- Analysis (worker) ----
    private static final int PLAN_STEPS=12;
    private void analyze(){
        int generation=++analysisGeneration;if(analysis!=null)analysis.cancel(true);
        Profile p=data.profile();overview=List.of();upgrades=List.of();plan=List.of();other=null;bestRow=null;bestGains=new IdentityHashMap<>();
        boolean needed=tab==HOME||tab==UPGRADES||tab==MORE;
        if(p==null||!data.ready||!needed||!p.inventoryEnabled&&!(tab==UPGRADES&&otherView)){calculating=false;buildCards();return;}
        calculating=true;buildCards();
        var selectedMethod=method;var selectedStat=stat;int selectedTab=tab;boolean focused=byStat,others=tab==UPGRADES&&otherView;
        var effective=data.effective(method);var quotes=new HashMap<>(data.prices);
        boolean ranks=selectedTab==UPGRADES&&!others,plans=selectedTab==HOME||ranks&&!focused;
        var rows=ranks&&!focused?data.comparisonRows():List.<MethodComparison.Row>of();
        var best=rows.isEmpty()||rows.getFirst().totalCoinsPerHour()==null?null:rows.getFirst();
        var bestStats=best==null?null:data.comparisonStats(best.method());
        var bestEntry=best==null?null:data.comparison().entry(best.method());
        double bestEfficiency=best==null?0:data.inputs(best.method()).efficiency;
        StatsReport report=data.stats(method);var basis=report==null?new ProgressionEngine.RateBasis(data.inputs(method).efficiency,null)
                :new ProgressionEngine.RateBasis(data.inputs(method).efficiency,report.ability,report.petStats,report.petOptions,report.profileUpgrades,report.resale);
        boolean statsReady=data.hasStats(method);double efficiency=data.inputs(method).efficiency;
        analysis=ClientData.WORK.submit(()->{
            try {
                var base=selectedTab==MORE?data.engine.overview(p,selectedMethod,selectedStat):List.<Overview>of();
                var ranked=ranks&&(focused||statsReady)?data.engine.upgrades(p,selectedMethod,focused?selectedStat:null,effective,quotes,basis):List.<Upgrade>of();
                var steps=plans&&statsReady?data.engine.plan(p,selectedMethod,effective,quotes,basis,PLAN_STEPS):List.<ProgressionEngine.Step>of();
                var extra=others&&statsReady?OtherUpgrades.of(data.engine,p,selectedMethod,report,effective,efficiency,quotes,basis):null;
                Map<Upgrade,Double> gains=new IdentityHashMap<>();
                if(best!=null&&bestStats!=null&&best.method()!=selectedMethod)for(var u:ranked){Double g=MethodComparison.gain(best.method(),bestStats,bestEfficiency,bestEntry.ability,u.delta(),u.cooldownFactor());if(g!=null)gains.put(u,g);}
                minecraft.execute(()->{if(generation!=analysisGeneration||Compat.screen(minecraft)!=this)return;overview=base;upgrades=ranked;plan=steps;other=extra;bestRow=best;bestGains=gains;calculating=false;buildCards();});
            }catch(Exception e){minecraft.execute(()->{if(generation!=analysisGeneration||Compat.screen(minecraft)!=this)return;calculating=false;cards.clear();card("Unable to calculate",RED,0).line("Try loading the profile again. No upgrades were assumed.",TEXT);layoutCards();});}
        });
    }

    // ---- Cards ----
    private Card card(String title,int accent,int column){Card c=new Card(title,accent,column);cards.add(c);return c;}
    private void toggle(String key){if(!expanded.remove(key))expanded.add(key);buildCards();}
    private void buildCards(){
        cards.clear();Profile p=data.profile();
        if(!data.ready){card("Preparing SkyProgression",GOLD,0).line(data.message,MUTED);layoutCards();return;}
        if(p==null&&tab!=MORE){
            Card c=card(data.loading?"Loading your profile...":"Your next mining upgrade",GOLD,0);
            c.line("Choose a method and see what to improve next.",TEXT).line("Type your username at the top and press Load.",CYAN);
            c.add(new Text("Or try the offline Demo (example data)",GOLD,"Example ownership and prices, not your real profile.",()->{data.demo();scroll=0;refresh();}));
            card("Connect your profile",CYAN,1).line(data.localBackend()?"Start the local companion, then press Load.":"Press Load: profiles come from the SkyProgression API.",TEXT,"Backend: "+data.backend+"\nChange it in config/skyprogression.json (\"backend\").")
                    .line("Enable Inventory API in SkyBlock Settings for gear advice.",MUTED,"SkyBlock Menu > Settings > API Settings > Inventory API: ON.");
            updateNotice();
        }else switch(tab){case HOME->homeCards(p);case GEAR->gearCards();case UPGRADES->upgradeCards(p);case METHODS->methodCards();default->moreCards(p);}
        layoutCards();
    }
    /** The SkyBlock item's own icon (texture packs can retexture it), else a generic one for the slot. */
    private ItemStack icon(String id,String group){
        ItemStack own=Icons.of(id);if(own!=null)return own;
        return new ItemStack(switch(group==null?"":group){
            case "Tool","Drill","Pickaxe","Gauntlet" -> Items.DIAMOND_PICKAXE;
            case "Helmet" -> Items.DIAMOND_HELMET;
            case "Chestplate" -> Items.DIAMOND_CHESTPLATE;
            case "Leggings" -> Items.DIAMOND_LEGGINGS;
            case "Boots" -> Items.DIAMOND_BOOTS;
            case "Lantern" -> Items.LANTERN;
            case "Pet" -> Items.BONE;
            case "HOTM","HOTM tree" -> Items.EMERALD;
            case "Necklace" -> Items.STRING;
            case "Cloak" -> Items.LEATHER;
            case "Belt" -> Items.LEAD;
            case "Gloves","Bracelet" -> Items.RABBIT_HIDE;
            default -> Items.AMETHYST_SHARD;
        });
    }
    private ItemStack productIcon(MiningMethod m){ItemStack own=Icons.of(MethodComparison.priceId(m));return own!=null?own:new ItemStack(Items.COBBLESTONE);}

    // Home
    private void homeCards(Profile p){
        statsCard();
        rateTile();
        nextUpgradeTile(p);
        if(!data.tracker.empty())sessionCard(true);
        methodChart(true);
        updateNotice();
    }
    private void updateNotice(){
        if(!data.update.isEmpty())card("SkyProgression "+data.update+" is available",CYAN,1).line("Download it from github.com/tianiste/SkyProgression/releases.",MUTED,"Replace the old jar in your mods folder. Nothing is downloaded automatically.");
    }
    /** One bar per method stat: current vs attainable with this loadout; hover lists the top sources, click opens Gear. */
    private void statsCard(){
        Card c=card("Your stats for "+method.label,GOLD,0);
        c.tip="Bars show the current total against the best attainable with this gear, tool, pet and HOTM tree.\n* = your Type Stats value is used instead.\nClick a bar for the full breakdown on Gear.";
        StatsReport r=data.stats(method);var e=data.effective(method);var manual=data.inputs(method).stats;
        if(r==null&&e.isEmpty()){
            if(data.demo)c.line("Demo has no API data. Enter stats in Type Stats (More page) to try the rates.",TEXT);
            else{String error=data.statsError(method);c.line(!error.isEmpty()?error:data.statsLoading(method)?"Loading automatic stats...":"Load your player to see your stats.",error.isEmpty()?MUTED:RED);}
            return;
        }
        for(Stat s:method.relevantStats()){
            Double v=e.get(s);var l=r==null?null:r.ledger(s);
            if(v==null){c.add(new Meter(s.label,"?",MUTED,0,MUTED,"Neither known nor entered.",null));continue;}
            double max=l==null?v:l.attainable!=null?l.attainable:l.attainableKnown;
            boolean maxed=l!=null&&l.attainable!=null&&v>=l.attainable-1e-9;
            String value=(l!=null&&!l.complete&&!manual.containsKey(s)?"at least ":"")+amount(s,v)+(manual.containsKey(s)?" *":"");
            c.add(new Meter(s.label,value+(l==null?"":"  / "+(l.attainable==null?"≥":"")+amount(s,max)),maxed?GREEN:TEXT,max>0?v/Math.max(v,max):0,maxed?GREEN:CYAN,
                    l==null?"Manual value (Type Stats).":sourcesTip(l,5),()->switchTab(GEAR)));
        }
    }
    /** Top counted sources of a ledger, largest first, as tooltip lines. */
    private String sourcesTip(StatsReport.Ledger l,int limit){
        StringJoiner out=new StringJoiner("\n");
        out.add(l.stat.label+": "+amount(l.stat,l.total)+(l.attainable!=null?" of "+amount(l.stat,l.attainable)+" attainable":" (attainable at least "+amount(l.stat,l.attainableKnown)+")"));
        var counted=l.contributions.stream().filter(Breakdown.Contribution::counts).sorted(Comparator.comparingDouble((Breakdown.Contribution x)->-Math.abs(share(l,x)))).toList();
        for(var x:counted.subList(0,Math.min(limit,counted.size())))out.add(sourceValue(l,x)+"  "+x.source());
        if(counted.size()>limit)out.add("+ "+(counted.size()-limit)+" more on the Gear tab");
        return out.toString();
    }
    /** What one counted source adds to the total: additive % sources add their share of the flat sum. */
    private static double share(StatsReport.Ledger l,Breakdown.Contribution x){
        if(x.value()==null)return 0;
        return x.category().equals(Breakdown.ADDITIVE_PERCENT)?l.flat*x.value()/100:x.value();
    }
    private static String sourceValue(StatsReport.Ledger l,Breakdown.Contribution x){
        return x.value()==null?"?":x.category().equals(Breakdown.ADDITIVE_PERCENT)?"+"+fmt(x.value())+"%":signed(l.stat,x.value());
    }
    /** Pickaxe ability of the evaluated tool, or null before the stats load. */
    private MethodRates.Ability ability(){StatsReport r=data.stats(method);return r==null?null:r.ability;}
    /** The method's hourly rate from the effective stats, or null when it cannot be computed yet. */
    private Double perHour(){
        var e=data.effective(method);if(e.isEmpty()||!MethodRates.supported(method))return null;
        try{double v=MethodRates.perHour(method,e,data.inputs(method).efficiency,ability(),data.zone(method));return Double.isFinite(v)?v:null;}catch(IllegalArgumentException ex){return null;}
    }
    private void rateTile(){
        Card c=card("Rate now",CYAN,1);c.icon=productIcon(method);
        Double rate=perHour();
        if(rate==null){c.line(data.statsLoading(method)?"Loading automatic stats...":"Rate unavailable until stats load (or Type Stats).",MUTED);return;}
        Double each=MethodComparison.coinsPerItem(data.prices,method,data.priceMode);
        String tip=fmt(data.inputs(method).efficiency)+"% efficiency | "+MethodComparison.model(method)+"\nWindow: "+MethodComparison.window(method,ability())+"\nClick for the full rate on Methods.";
        c.add(new Big((MethodComparison.lowerBound(method)?"≥":"")+compact(rate),MethodComparison.itemName(method)+" / hour",CYAN,tip,()->switchTab(METHODS)));
        c.add(new Text(each==null?"Coins / hour unknown ("+data.priceMode.label+")":"≈ "+coins(rate*each)+" / hour, "+data.priceMode.label,each==null?MUTED:GOLD,
                each==null?"No "+data.priceMode.label+" price for "+MethodComparison.itemName(method)+".":MethodComparison.priceSource(data.prices,method,data.priceMode),null));
    }
    private void nextUpgradeTile(Profile p){
        Card c=card("Best next upgrade",GREEN,1);
        if(!p.inventoryEnabled){c.line("Enable the Inventory API for upgrade advice.",RED,"SkyBlock Menu > Settings > API Settings > Inventory API: ON, then reload after the profile cache expires.");return;}
        if(calculating){c.line("Calculating...",MUTED);return;}
        if(!data.hasStats(method)){c.line(data.statsLoading(method)?"Loading automatic stats...":"Needs your stats (load the player or use Type Stats).",MUTED);return;}
        if(plan.isEmpty()){c.line("No priced upgrade found to recommend.",MUTED,"This does not mean the account is maxed: unpriced and blocked upgrades are skipped.");return;}
        var step=plan.getFirst();var u=step.upgrade();
        c.add(new Entry(upgradeIcon(u),u.item().name,TEXT,u.current()!=null?(u.item().modification?"On: ":"Replaces: ")+u.current().name:"Step 1 of the plan",MUTED,
                "+"+fmt(u.gain())+"%",GREEN,costText(u),Objects.equals(u.cost(),0.0)?GREEN:TEXT,upgradeTip(u,false)+"\nClick for the whole plan.",()->switchTab(UPGRADES)));
    }
    /** Coins / hour by method as a bar chart; the best method is cyan, the selected one gold; clicking a row selects it. */
    private void methodChart(boolean home){
        Card c=card("Coins / hour by method",GOLD,FULL);c.right=data.priceMode.label;c.optional=home;
        c.tip="Ranked at the "+data.priceMode.label+" price ("+data.priceMode.meaning+"). Each method uses its best owned gear and tool in its usual zone, with that method's Type Stats values and efficiency.\n"
                +"Coins / hour = items / hour x the quote; a missing quote stays unknown and sorts last.\n"
                +"Windows: Coal per 30 minutes with two lobby hops; Obsidian per Mining Speed Boost cycle; gems, metals and Shafting per hour.\n"
                +"Bazaar figures are gross, before tax. Flawless gems have no NPC price, so NPC sell counts them as Rough.\n"
                +"Not counted: Hard Stone, the grotto's other gems, mineshaft loot, crafting into enchanted items. Coleweight is not shown (coefficients unverified).\n"
                +"Click a row to select that method.";
        if(data.demo){c.line("Demo has no API data. Load your player to compare methods.",MUTED);return;}
        var rows=data.comparisonRows();var r=data.comparison();
        if(r==null||rows.isEmpty()){String error=data.compareError();
            if(!error.isEmpty())c.line(error,RED,"Refresh on the Methods tab retries.");else c.line("Comparing methods...",MUTED);return;}
        c.tip+="\nProfile data from "+r.fetchedAt+(r.godPotion?", God Potion assumed.":", no God Potion.");
        double top=rows.stream().filter(x->x.totalCoinsPerHour()!=null).mapToDouble(MethodComparison.Row::totalCoinsPerHour).max().orElse(1);
        var best=rows.stream().filter(x->x.totalCoinsPerHour()!=null).findFirst().orElse(null);
        for(var row:rows){
            boolean selected=row.method()==method,isBest=row==best;var e=r.entry(row.method());
            String tip=row.method().label+(isBest?" (best)":"")+"\n"+(row.error().isEmpty()?(row.lowerBound()?"At least ":"")+fmt(row.itemsPerHour())+" "+row.item()+" / hour":"Rate unavailable: "+row.error())
                    +(row.coinsPerHour()==null?"\nCoins unknown":"\n"+coins(row.coinsPerHour())+" / hour")+"\n"+row.priceNote()+secondaryText(row)+"\nEfficiency "+fmt(row.efficiency())+"% | "+row.model()
                    +"\nTool: "+(row.tool().isEmpty()?"none readable":row.tool())+(e==null||e.zone.isEmpty()?"":" | "+e.zone)+(row.statsComplete()?"":"\nSome stat sources are unknown: a lower bound.");
            MiningMethod m=row.method();
            c.add(new Bar(productIcon(m),(selected?"» ":"")+m.label,selected?GOLD:TEXT,row.totalCoinsPerHour()==null?"?":compact(row.totalCoinsPerHour()),
                    row.totalCoinsPerHour()==null?0:row.totalCoinsPerHour()/Math.max(1,top),isBest?CYAN:selected?GOLD:DIM,tip,()->{method=m;slot="";refresh();}));
        }
    }

    // Session tracker (Methods; a short tile on Home while a session exists)
    private String sessionNote="";
    private void sessionCard(boolean home){
        var t=data.tracker;long now=System.currentTimeMillis();MiningMethod m=t.method();
        String state=switch(t.state()){case READY->"not started";case RUNNING->"running";case PAUSED->"idle, paused";case STOPPED->"stopped";};
        Card c=card(home?"Session: "+m.label:"Session tracker | "+m.label,CYAN,home?1:FULL);c.icon=productIcon(m);c.right=state;c.rightColor=t.state()==SessionTracker.State.RUNNING?GREEN:MUTED;
        String unit=MethodComparison.itemName(m);
        c.tip="Measures what you really mine and compares it with the model.\nCounted: items picked up into your inventory, the [Sacks] chat summaries (their hover lists the items) and Pristine procs. "
                +"Compacted forms count as their base items (Enchanted = 160, blocks and cubes = 160 Enchanted, gems 80 per tier).\n"
                +"Starts by itself on the first "+unit+" gain (Auto-start), pauses after "+(t.idleMs/1000)+" s without one (trackerIdleSeconds in config/skyprogression.json); the active time ends at the last gain.\n"
                +"Keeps running with /sp closed. Read only: it never clicks, mines or sends anything.";
        if(t.empty()){
            c.line(t.autoStart?"Starts when you mine "+unit+", or press Session: Start.":"Press Session: Start, then mine.",TEXT);
            if(!home)c.line("Counts pickups, [Sacks] summaries and Pristine procs; compares with the model below.",MUTED);
            return;
        }
        if(m!=method)c.line("Tracking "+m.label+" (selected: "+method.label+"). Reset to track "+method.label+".",GOLD);
        var x=t.measure(now,data.prices,data.priceMode);var p=data.predicted(m);
        long elapsed=t.elapsedMs(now),active=t.activeMs(now);
        String measured=Double.isFinite(x.productPerHour())?fmt(Math.round(x.productPerHour()*100)/100.0):"-";
        boolean modelKnown=p!=null&&p.perHour()!=null;
        String model=!modelKnown?"unknown":(MethodComparison.lowerBound(m)?"≥":"")+fmt(Math.round(p.perHour()*100)/100.0);
        String modelTip=!modelKnown?"The model needs your stats (Load or Type Stats)"+(MethodRates.supported(m)?".":"; this method has no rate model."):
                "Model at "+fmt(p.efficiency())+"% efficiency, with your current stats, tool and zone (same as Rate now).";
        c.add(new Pair(unit+" / hour, measured",MUTED,measured,CYAN,"Counted "+fmt(Math.round(x.product()*1000)/1000.0)+" "+unit+" over "+SessionWatcher.clock(active)+" active time.",null));
        c.add(new Pair(unit+" / hour, model",MUTED,model,TEXT,modelTip,null));
        if(modelKnown&&p.perHour()>0&&Double.isFinite(x.productPerHour())){
            double ratio=x.productPerHour()/p.perHour();
            c.add(new Pair("Measured / model",MUTED,fmt(Math.round(ratio*1000)/10.0)+"%",ratio>=.9&&ratio<=1.1?GREEN:GOLD,
                    "The model gives this rate at about "+fmt(Math.round(p.efficiency()*ratio*10)/10.0)+"% efficiency (share of the active time spent breaking blocks; above 100% means the model is too low).",null));
        }
        String coinsMeasured=x.coinsPerHour()==null?(x.activeHours()>0&&x.product()>0?"no price":"-"):(x.atLeast()?"≥ ":"")+coins(x.coinsPerHour());
        c.add(new Pair("Coins / hour, measured",MUTED,coinsMeasured,x.coinsPerHour()==null?MUTED:GOLD,
                data.priceMode.label+" price of every counted item"+(x.unpriced().isEmpty()?".":"; no price: "+String.join(", ",x.unpriced())+" (left out)."),null));
        if(!home)c.add(new Pair("Coins / hour, model",MUTED,p==null||p.coinsPerHour()==null?"unknown":coins(p.coinsPerHour()),TEXT,
                "Model product plus its modelled secondary drops at the "+data.priceMode.label+" price. The measurement also counts drops the model leaves out (such as Hard Stone on gem routes).",null));
        c.add(new Pair("Active / elapsed",MUTED,SessionWatcher.clock(active)+" / "+SessionWatcher.clock(elapsed)+(elapsed>0?"  ("+Math.round(100.0*active/elapsed)+"% uptime)":""),TEXT,
                "Active: time mining, from the first gain (back-dated by a sack summary's window) to the last one before an idle pause.",null));
        if(home)return;
        if(m.gems())c.add(new Pair("Pristine procs",MUTED,t.pristineProcs()+" ("+fmt(t.pristineFlawed())+" Flawed)"+(x.activeHours()>0?", "+fmt(Math.round(t.pristineProcs()/x.activeHours()))+" / hour":""),TEXT,
                "From PRISTINE! chat messages. Their Flawed gems arrive through the inventory or sacks and are counted there, not twice.",null));
        StringJoiner counted=new StringJoiner("\n");
        t.raw().forEach((id,n)->{var item=t.byId(id);counted.add(fmt(n)+" x "+(item==null?id:item.name()));});
        var src=t.bySource();long inv=src.getOrDefault(SessionTracker.Source.INVENTORY,0L),sack=src.getOrDefault(SessionTracker.Source.SACK,0L);
        if(inv+sack>0)counted.add("Base items: "+Math.round(100.0*sack/(inv+sack))+"% from sack summaries, "+Math.round(100.0*inv/(inv+sack))+"% from the inventory");
        for(var f:x.families())c.add(new Pair((f.main()?"":"+ ")+f.name(),MUTED,fmt(Math.round(f.amount()*1000)/1000.0)+(f.coinsEach()==null?" (no price)":""),f.main()?TEXT:MUTED,counted.toString(),null));
        if(x.families().isEmpty())c.line("Nothing counted yet.",MUTED);
        if(!sessionNote.isEmpty())c.line(sessionNote,sessionNote.startsWith("Saved")?GREEN:sessionNote.startsWith("Saving")?MUTED:RED);
    }
    /** Writes the stopped session with its measured and model values and the stats it was compared with (off the game thread). */
    private void saveSession(){
        var t=data.tracker;long now=System.currentTimeMillis();MiningMethod m=t.method();
        var x=t.measure(now,data.prices,data.priceMode);var p=data.predicted(m);
        var json=new com.google.gson.JsonObject();
        json.addProperty("method",m.name());json.addProperty("savedAt",java.time.Instant.ofEpochMilli(now).toString());
        json.addProperty("startedAt",java.time.Instant.ofEpochMilli(t.startedAt()).toString());
        json.addProperty("elapsedSeconds",t.elapsedMs(now)/1000.0);json.addProperty("activeSeconds",t.activeMs(now)/1000.0);json.addProperty("idleSeconds",t.idleMs/1000.0);
        json.addProperty("mod",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("skyprogression").map(c->c.getMetadata().getVersion().getFriendlyString()).orElse(""));
        var counts=new com.google.gson.JsonObject();t.raw().forEach(counts::addProperty);json.add("counts",counts);
        var sources=new com.google.gson.JsonObject();t.bySource().forEach((k,v)->sources.addProperty(k.name().toLowerCase(Locale.ROOT),v));json.add("baseItemsBySource",sources);
        json.addProperty("pristineProcs",t.pristineProcs());json.addProperty("pristineFlawed",t.pristineFlawed());
        var measured=new com.google.gson.JsonObject();measured.addProperty("unit",MethodComparison.itemName(m));measured.addProperty("product",x.product());
        if(Double.isFinite(x.productPerHour()))measured.addProperty("productPerHour",x.productPerHour());
        if(x.coinsPerHour()!=null)measured.addProperty("coinsPerHour",x.coinsPerHour());
        measured.addProperty("coinsAtLeast",x.atLeast());var unpriced=new com.google.gson.JsonArray();x.unpriced().forEach(unpriced::add);measured.add("unpriced",unpriced);
        json.add("measured",measured);
        var model=new com.google.gson.JsonObject();
        if(p!=null){if(p.perHour()!=null)model.addProperty("productPerHour",p.perHour());if(p.coinsPerHour()!=null)model.addProperty("coinsPerHour",p.coinsPerHour());model.addProperty("efficiency",p.efficiency());}
        model.addProperty("priceMode",data.priceMode.name());model.addProperty("model",MethodComparison.model(m));json.add("model",model);
        var stats=new com.google.gson.JsonObject();data.effective(m).forEach((s,v)->stats.addProperty(s.name(),v));
        var snapshot=new com.google.gson.JsonObject();snapshot.add("stats",stats);
        var typed=new com.google.gson.JsonArray();data.inputs(m).stats.keySet().forEach(s->typed.add(s.name()));snapshot.add("typedStats",typed);
        Profile pr=data.profile();StatsReport r=data.stats(m);StatContext.Zone z=data.zone(m);
        snapshot.addProperty("player",pr==null?"":pr.player);snapshot.addProperty("profileId",pr==null?"":pr.id);snapshot.addProperty("demo",data.demo);
        snapshot.addProperty("profileFetchedAt",r==null?"":r.fetchedAt);snapshot.addProperty("tool",r==null?"":r.tool);snapshot.addProperty("zone",z==null?"":z.name());
        snapshot.addProperty("ability",r==null||r.ability==null?"":abilityText(r.ability));json.add("snapshot",snapshot);
        sessionNote="Saving...";buildCards();
        data.saveSession(json,minecraft::execute,n->{sessionNote=n<0?"Could not save the session.":"Saved as session "+n+" in config/skyprogression-sessions.json.";if(Compat.screen(minecraft)==this)buildCards();});
    }

    // Gear
    private void gearCards(){
        if(data.demo){card("Gear",GOLD,0).line("Demo has no API data. Load your player for your loadout and stat breakdown.",TEXT);return;}
        loadoutCard();
        statCards();
    }
    /** Hourly rate of the method for one loadout's totals, or null when it cannot be computed. */
    private Double rateOf(StartWith.Scenario s,double efficiency){
        if(s==null||s.stats==null)return null;
        try{double v=MethodRates.perHour(method,s.stats,efficiency,s.ability);return Double.isFinite(v)?v:null;}catch(IllegalArgumentException e){return null;}
    }
    private String rateText(Double perHour,Double each){
        if(perHour==null)return "rate unavailable";
        return (MethodComparison.lowerBound(method)?"at least ":"")+fmt(perHour)+" "+MethodComparison.itemName(method)+" / hour, "+(each==null?"coins unknown":coins(perHour*each)+" / hour");
    }
    private static String gainPercent(Double before,Double after){
        return before==null||after==null||before<=0?"?":(after>=before?"+":"")+fmt((after/before-1)*100)+"%";
    }
    private static String changeText(Double before,Double after,Double each){
        if(before==null||after==null)return "rate change unknown";
        return gainPercent(before,after)+(each==null?", coins unknown":", "+(after>=before?"+":"-")+coins(Math.abs(after-before)*each)+" / hour");
    }
    private static final List<String> SLOT_ORDER=List.of("Helmet","Chestplate","Leggings","Boots","Necklace","Cloak","Belt","Gloves","Bracelet");
    private void loadoutCard(){
        StartWith.Report r=data.start(method);
        Card c=card("Use this loadout",GOLD,0);
        if(r==null){String error=data.startError(method);
            c.line(!error.isEmpty()?error:"Finding your best owned gear...",error.isEmpty()?MUTED:RED,error.isEmpty()?"Comparing what you own with what you wear.":"Press Refresh to retry.");
            return;}
        var i=data.inputs(method);double eff=i.efficiency;Double each=MethodComparison.coinsPerItem(data.prices,method,data.priceMode);
        Double before=rateOf(r.current,eff),after=rateOf(r.bestOwned,eff);
        StatContext.Zone zone=data.zone(method);
        c.tip="Best owned for "+method.label+": one copy per slot from all readable storage, one tool, one pet, one accessory per family.\n"
                +StatBreakdown.treeLabel(r.hotmTree)+(r.hotmTrees>1?" of "+r.hotmTrees+" (Loadouts): best for "+method.label+"; the API does not show which tree is active.":"")
                +(r.otherAccessories>0?"\n"+r.otherAccessories+" other accessories add no "+method.label+" stats.":"")
                +"\n"+fmt(eff)+"% efficiency | "+(zone==null?"no zone":zone.label)+" | God Potion "+(r.godPotion?"on":"off")+" | "+data.priceMode.label+" price"+(each==null?" unknown":"");
        // Gold outline: the tool; cyan: a piece to fetch; gray: already worn.
        List<StartWith.Piece> pieces=new ArrayList<>(r.best);
        pieces.sort(Comparator.comparingInt(x->{int n=SLOT_ORDER.indexOf(x.slot());return n>=0?n:x.slot().equals("Pet")?SLOT_ORDER.size()+1:x.slot().equals("Accessory")?SLOT_ORDER.size()+2:SLOT_ORDER.size();}));
        List<Slot> slots=new ArrayList<>();
        for(var piece:pieces){boolean tool=isTool(piece,r);
            slots.add(new Slot(icon(piece.id(),tool?"Tool":piece.slot()),tool?GOLD:piece.worn()?EDGE:CYAN,piece.key().equals(slot),
                    piece.name()+"\n"+piece.slot()+(piece.detail().isEmpty()?"":", "+piece.detail())+"\n"+(piece.worn()?"Wearing it":"Stored: "+piece.location()),()->{slot=slot.equals(piece.key())?"":piece.key();buildCards();}));}
        if(slots.isEmpty())c.line(r.inventoryKnown?"No readable mining gear.":"Ownership unknown.",MUTED);
        else c.add(new Slots(slots));
        if(!r.inventoryKnown)c.line("Inventory API off: only the pet can be compared.",RED,"Ownership of armor, equipment, tools and accessories is unknown.");
        if(!r.petsKnown)c.line("Pet data unavailable.",RED);
        String rates="Now: "+rateText(before,each)+"\nBest owned: "+rateText(after,each)+(r.swaps.isEmpty()?"":"\nTotal: "+changeText(before,after,each))
                +(r.current.complete&&r.bestOwned.complete?"":"\nSome stat sources are unknown, so these rates are lower bounds.")
                +(i.stats.isEmpty()?"":"\nType Stats values are not applied here: gear is compared by its automatic totals.");
        if(r.swaps.isEmpty())c.line(r.inventoryKnown?"✔ Wearing the best you own":"No swaps found in the readable data.",GREEN,rates);
        else c.line(r.swaps.size()+" swap"+(r.swaps.size()==1?"":"s")+" needed, "+gainPercent(before,after)+" rate",GOLD,rates+"\nEach swap is rated alone on what you wear now; together they give the total, which can differ from the sum.");
        // Detail of the clicked slot.
        var chosen=pieces.stream().filter(x->x.key().equals(slot)).findFirst().orElse(null);
        if(chosen!=null){
            var sw=r.swaps.stream().filter(x->x.to().key().equals(chosen.key())).findFirst().orElse(null);
            String swap=sw==null?(chosen.worn()?"Already worn":"No swap needed"):gainPercent(before,rateOf(sw.after(),eff));
            c.add(new Entry(icon(chosen.id(),isTool(chosen,r)?"Tool":chosen.slot()),chosen.name(),GOLD,chosen.worn()?"Wearing it":"Stored: "+chosen.location()+(chosen.detail().isEmpty()?"":" ("+chosen.detail()+")"),CYAN,
                    swap,sw==null?MUTED:GREEN,"",MUTED,sw==null?null:swapTip(sw,r,before,eff,each),null));
        }else if(!slots.isEmpty())c.line("Click a slot for where it is stored.",MUTED);
        // Swaps, largest rate gain first; those without a computable rate last.
        record Ranked(StartWith.Swap swap,Double rate){}
        List<Ranked> ranked=new ArrayList<>();for(var sw:r.swaps)ranked.add(new Ranked(sw,rateOf(sw.after(),eff)));
        ranked.sort(Comparator.comparing((Ranked x)->x.rate()==null?1:0).thenComparing(x->x.rate()==null?0:-x.rate()));
        for(var x:ranked){var sw=x.swap();
            c.add(new Entry(icon(sw.to().id(),sw.slot()),sw.slot()+": "+sw.to().name(),TEXT,sw.slot().equals("HOTM tree")?"Switch in Loadouts":"From "+sw.to().location(),MUTED,
                    gainPercent(before,x.rate()),GREEN,"",MUTED,swapTip(sw,r,before,eff,each),()->{slot=sw.to().key();buildCards();}));}
        Card used=card("Stats use",MUTED,0);used.optional=true;
        StatsReport s=data.stats(method);
        if(s!=null){StringJoiner tip=new StringJoiner("\n");for(var piece:s.loadout)tip.add(piece.name()+"  ("+piece.location()+")");
            tip.add(s.accessories+" accessories from the accessory bag, one per family.");
            if(s.hotmTrees>1)tip.add(StatBreakdown.treeLabel(s.hotmTree)+" of "+s.hotmTrees+": best for "+method.label+" (the API does not show the active tree).");
            if(s.bestGear)tip.add("Picked from all readable storage; equip these to reach the totals. Gear: Equipped shows what you wear now.");
            tip.add("Attainable keeps this gear, tool, pet and HOTM tree with each source at its own maximum, plus missing attributes, essence perks, collections and consumables. It is not a maxed account.");
            used.line((s.bestGear?"Best owned gear":"Gear worn now")+", "+s.loadout.size()+" pieces, "+s.accessories+" accessories",TEXT,tip.toString());}
        bestMethodLines(used,r);
        if(!r.notes.isEmpty())used.line("Notes ("+r.notes.size()+")",MUTED,String.join("\n",r.notes));
    }
    private static boolean isTool(StartWith.Piece piece,StartWith.Report r){
        return !SLOT_ORDER.contains(piece.slot())&&!piece.slot().equals("Pet")&&!piece.slot().equals("Accessory")||r.bestOwned!=null&&piece.key().equals(r.bestOwned.tool);
    }
    private String swapTip(StartWith.Swap sw,StartWith.Report r,Double before,double eff,Double each){
        var from=sw.from();var to=sw.to();Double rate=rateOf(sw.after(),eff);
        StringJoiner tip=new StringJoiner("\n");
        tip.add("Fetch from: "+to.location()+(to.detail().isEmpty()?"":" ("+to.detail()+")"));
        tip.add(switch(sw.slot()){
            case "Accessory"->"Put it in your accessory bag"+(from==null?".":", in place of "+from.name()+" (same family).");
            case "HOTM tree"->"Switch to it in Loadouts (the API does not show the active tree).";
            default->from==null?"The slot is empty now.":"Replaces "+from.name()+(from.detail().isEmpty()?"":" ("+from.detail()+")")+".";
        });
        StringJoiner delta=new StringJoiner(", ");
        for(Stat st:method.relevantStats()){double d=stat(sw.after().stats,st)-stat(r.current.stats,st);if(Math.abs(d)>1e-9)delta.add((d>0?"+":"")+fmt(d)+" "+st.label);}
        if(delta.length()>0)tip.add(delta.toString());
        if(!Objects.equals(sw.after().ability,r.current.ability))tip.add("Tool ability: "+abilityText(sw.after().ability));
        tip.add("Rate: "+changeText(before,rate,each));
        tip.add(rateText(rate,each));
        return tip.toString();
    }
    private static String abilityText(MethodRates.Ability a){
        return a==null?"none shown":a.name()+(a.cooldown()==null?"":", "+fmt(a.cooldown())+"s cooldown");
    }
    /** Best method by coins / hour with the gear worn now (Start with) and with the best owned gear (comparison). */
    private void bestMethodLines(Card c,StartWith.Report r){
        var mode=data.priceMode;List<MethodComparison.Row> worn=new ArrayList<>();
        for(var e:r.worn){if(e==null||e.method==null||e.stats==null)continue;var in=data.inputs(e.method);Map<Stat,Double> stats=new EnumMap<>(Stat.class);stats.putAll(e.stats);stats.putAll(in.stats);
            worn.add(MethodComparison.row(e.method,stats,e.complete,in.efficiency,e.ability,data.prices,mode,e.tool));}
        var wornTop=MethodComparison.rank(worn).stream().filter(x->x.totalCoinsPerHour()!=null).findFirst().orElse(null);
        var bestTop=data.comparisonRows().stream().filter(x->x.totalCoinsPerHour()!=null).findFirst().orElse(null);
        String tip="At the "+mode.label+" price, each method in its usual zone with its Type Stats values and efficiency. Full ranking on Methods."
                +(data.comparison()!=null&&data.comparison().godPotion!=r.godPotion?"\nGod Potion is "+(r.godPotion?"on":"off")+" here but "+(data.comparison().godPotion?"on":"off")+" on Methods.":"");
        c.line(wornTop==null?"Gear worn now: no method has a known price.":"Gear worn now: best at "+wornTop.method().label+", "+compact(wornTop.totalCoinsPerHour())+" / h",wornTop!=null&&wornTop.method()==method?GREEN:TEXT,tip);
        if(data.comparison()==null)c.line(data.compareError().isEmpty()?"Best owned gear: comparing methods...":"Best owned gear: "+data.compareError(),MUTED);
        else c.line(bestTop==null?"Best owned gear: no method has a known price.":"Best owned gear: best at "+bestTop.method().label+", "+compact(bestTop.totalCoinsPerHour())+" / h",bestTop!=null&&bestTop.method()==method?GREEN:TEXT,tip);
    }
    private void statCards(){
        StatsReport r=data.stats(method);
        if(r==null){String error=data.statsError(method);
            if(!error.isEmpty())card("Stat breakdown unavailable",RED,1).line(error,TEXT).line("Press Refresh to retry. Manual values in Type Stats still work.",MUTED);
            else card("Calculating stat breakdown...",CYAN,1).line("Calculating from your profile.",MUTED);
            return;}
        // Method: only the method's stats (plus the focused one); the rest still feed Upgrades and Rates.
        List<Stat> shown=statGroup.equals("All")?Arrays.asList(Stat.values()):StatBreakdown.GROUPS.getOrDefault(statGroup,method.relevantStats());
        boolean byMethod=statGroup.equals("Method");
        var ledgers=new ArrayList<>(r.stats.stream().filter(l->shown.contains(l.stat)||byMethod&&l.stat==stat).toList());
        ledgers.sort(Comparator.comparing(l->byMethod&&l.stat!=stat&&!method.relevantStats().contains(l.stat)));
        if(ledgers.isEmpty())card("No stats in this group",MUTED,1).line("This profile has no stats in this group.",TEXT);
        for(var l:ledgers)statCard(r,l);
    }
    private static String bucket(String category){
        return switch(category){case "Armor","Equipment","Tool","Accessory","Gear","Gemstone"->"Gear";case "HOTM","Powder"->"HOTM";case "Skill"->"Skill";case "Pet"->"Pet";default->"Other";};
    }
    private static int bucketColor(String bucket){return switch(bucket){case "Gear"->GOLD;case "HOTM"->CYAN;case "Skill"->GREEN;case "Pet"->PURPLE;default->MUTED;};}
    /** One stat: a bar split by source category, a legend, the top sources, and the hidden sources behind one expandable line. */
    private void statCard(StatsReport r,StatsReport.Ledger l){
        var manual=data.inputs(method).stats;Double override=manual.get(l.stat);
        double max=l.attainable!=null?l.attainable:l.attainableKnown;boolean maxed=l.attainable!=null&&l.total>=l.attainable-1e-9;
        Card c=card(l.stat.label,l.stat==stat||method.relevantStats().contains(l.stat)?GOLD:CYAN,AUTO);
        c.right=(l.complete?"":"≥")+amount(l.stat,l.total)+" / "+(l.attainable==null?"≥":"")+amount(l.stat,max)+(maxed?" ✔":"");c.rightColor=maxed?GREEN:TEXT;
        StringJoiner tip=new StringJoiner("\n");
        tip.add(l.stat==Stat.PICKAXE_ABILITY_COOLDOWN?"Reductions multiply: 1 - product of (1 - each). Sum of the parts: "+amount(l.stat,l.flat)
                :"Flat "+amount(l.stat,l.flat)+(l.multiplier!=1?" x "+fmt(l.multiplier)+" from additive % buffs":""));
        if(l.attainable==null&&l.unknownMaxima>0)tip.add("Attainable unknown: "+l.unknownMaxima+" counted source"+(l.unknownMaxima==1?"":"s")+" without a known maximum (such as gear stat lines); at least "+amount(l.stat,l.attainableKnown)+".");
        if(r.bestGear&&l.equipped!=null)tip.add("Wearing now: "+amount(l.stat,l.equipped));
        if(!r.bestGear&&l.bestOwned!=null)tip.add("Best owned gear: "+amount(l.stat,l.bestOwned));
        l.notes.forEach(tip::add);
        c.tip=tip.toString();
        // Segments: what each category adds to the total; the cooldown's multiplicative parts are scaled to the total.
        Map<String,Double> buckets=new LinkedHashMap<>();for(String b:List.of("Gear","HOTM","Skill","Pet","Other"))buckets.put(b,0.0);
        var counted=l.contributions.stream().filter(Breakdown.Contribution::counts).toList();
        double sum=0;for(var x:counted){double v=share(l,x);buckets.merge(bucket(x.category()),v,Double::sum);sum+=v;}
        double factor=l.stat==Stat.PICKAXE_ABILITY_COOLDOWN&&sum>0?l.total/sum:1;
        List<Seg> segs=new ArrayList<>();
        buckets.forEach((b,v)->{if(Math.abs(v)>1e-9)segs.add(new Seg(b+" "+(l.stat==Stat.PICKAXE_ABILITY_COOLDOWN?fmt(v*factor)+"%":amount(l.stat,v*factor)),v*factor,bucketColor(b)));});
        c.add(new Split(segs,Math.max(l.total,max)));
        if(!segs.isEmpty())c.add(new Legend(segs));
        if(override!=null)c.line("Manual value in use: "+fmt(override),GOLD,"Clear it in Type Stats (More page) to use the automatic total.");
        if(!l.complete)c.line("Lower bound: some counted sources are unknown.",RED);
        if(l.stat==Stat.BREAKING_POWER&&method.breakingPower()!=null&&l.total<method.breakingPower())c.line(method.label+" needs Breaking Power "+method.breakingPower()+": not met with this tool.",RED);
        if(r.tool.isEmpty())c.line("No readable mining tool, so tool stats are missing.",RED);
        var sorted=counted.stream().sorted(Comparator.comparingDouble((Breakdown.Contribution x)->-Math.abs(share(l,x)))).toList();
        var unknown=l.contributions.stream().filter(x->x.state()==Breakdown.State.COUNTED&&x.value()==null).toList();
        String allKey="all:"+l.stat;boolean all=expanded.contains(allKey);
        int limit=all?sorted.size():Math.min(4,sorted.size());
        for(var x:sorted.subList(0,limit))c.add(sourceRow(l,x));
        for(var x:unknown)c.add(new Pair(x.source(),TEXT,"?",RED,x.category()+"\n"+x.note(),null));
        if(sorted.size()>4)c.add(new Text(all?"- show fewer":"+ "+(sorted.size()-4)+" more sources",MUTED,null,()->toggle(allKey)));
        // Situational, temporary, missing and not-in-API sources, collapsed by default.
        var hidden=l.contributions.stream().filter(x->x.state()!=Breakdown.State.COUNTED).toList();
        if(!hidden.isEmpty()){
            Map<Breakdown.State,Integer> counts=new EnumMap<>(Breakdown.State.class);for(var x:hidden)counts.merge(x.state(),1,Integer::sum);
            StringJoiner label=new StringJoiner(", ");counts.forEach((s,n)->label.add(n+" "+stateLabel(s)));
            String key="hidden:"+l.stat;boolean open=expanded.contains(key);
            c.add(new Text((open?"- ":"+ ")+label,MUTED,"Not part of the total. Click to "+(open?"hide":"list")+" them.",()->toggle(key)));
            if(open)for(var x:hidden){
                int color=switch(x.state()){case SITUATIONAL->GOLD;case MISSING->RED;case TEMPORARY->CYAN;default->MUTED;};
                String value=x.value()==null?"?":sourceValue(l,x);
                String max2=x.maximum()==null?"":" / "+(x.category().equals(Breakdown.ADDITIVE_PERCENT)?fmt(x.maximum())+"%":amount(l.stat,x.maximum()));
                c.add(new Pair(x.source(),color,value+max2,TEXT,stateLabel(x.state())+" | "+x.category()+(x.condition().isEmpty()?"":"\n"+x.condition())+"\n"+x.note(),null));
            }
        }
    }
    private Pair sourceRow(StatsReport.Ledger l,Breakdown.Contribution x){
        String value=sourceValue(l,x);boolean known=x.maximum()!=null;
        boolean maxed=known&&x.maximum()>0&&x.value()>=x.maximum()-1e-9;
        String max=!known?"":x.category().equals(Breakdown.ADDITIVE_PERCENT)?fmt(x.maximum())+"%":amount(l.stat,x.maximum());
        String right=maxed?value+" ✔":known&&x.maximum()>x.value()+1e-9?value+" / "+max:value;
        String tip=x.category()+(x.confidence()==Breakdown.Confidence.MANUAL?" (assumed)":"")+(known?"\nMaximum: "+max:"\nMaximum unknown")+(x.condition().isEmpty()?"":"\n"+x.condition())+(x.note().isEmpty()?"":"\n"+x.note());
        return new Pair(x.source(),TEXT,right,maxed?GREEN:CYAN,tip,null);
    }
    private static String stateLabel(Breakdown.State state){
        return switch(state){case COUNTED->"counted";case SITUATIONAL->"situational";case MISSING->"missing";case TEMPORARY->"temporary";case UNTRACKABLE->"not in API";};
    }

    // Upgrades
    private ItemStack upgradeIcon(Upgrade u){
        return icon(u.item().modification&&u.current()!=null?u.current().id:u.item().id,u.current()!=null?u.current().group:u.item().group);
    }
    private static String changes(Upgrade u){
        StringJoiner changes=new StringJoiner(" / ");u.delta().forEach((s,v)->{if(v!=0)changes.add((v>0?"+":"")+fmt(v)+" "+s.label);});
        if(u.cooldownFactor()!=1)changes.add("ability cooldown x"+fmt(u.cooldownFactor()));
        return changes.toString();
    }
    /** Every detail of one upgrade, for its tooltip. */
    private String upgradeTip(Upgrade u,boolean focused){
        StringJoiner tip=new StringJoiner("\n");
        tip.add(u.item().name);
        if(u.current()!=null)tip.add((u.item().modification?"On: ":"Replaces: ")+u.current().name);
        String changes=changes(u);if(!changes.isEmpty())tip.add(changes);
        tip.add(u.cost()==null?("GRIND".equals(u.item().category)?"Grinding (no coin price)":"Price unknown"):coins(u.cost())+" | "+coins(u.costPerUnit())+(focused?" / point":" / 1%"));
        if(!u.resaleNote().isEmpty()&&u.cost()!=null)tip.add(u.net()!=null?"Net "+coins(u.net())+" after selling the replaced item for "+coins(u.resale())+" ("+u.resaleNote()+")":u.resaleNote()+"; net cost unknown");
        if(u.rateBefore()!=null)tip.add("Rate: +"+fmt(u.gain())+"% | "+fmt(u.rateBefore())+" -> "+fmt(u.rateAfter())+" "+MethodRates.unit(method,ability()));
        else if(!focused)tip.add("Estimated drops: +"+fmt(u.gain())+"%");
        if(u.rateBefore()!=null&&u.rateAfter()!=null){String cross=crossMethod(u);if(!cross.isEmpty())tip.add(cross);}
        if(u.owned())tip.add("HOTM".equals(u.item().group)?"Covered by your unspent powder and tokens":u.item().modification?"Materials already owned":"Item already owned");
        if(!u.blocked().isEmpty())tip.add(u.blocked());
        var quote=data.prices.get(u.item().id);
        if(quote!=null&&!u.item().modification)tip.add(quote.source);
        if(quote==null||u.item().modification||u.item().source.matches("^(Cheaper|Buying|No buy|Price unknown).*"))tip.add(u.item().source);
        return tip.toString();
    }
    /** The upgrade's coins/hour here, and its rate gain and coins/hour in the best method (by coins / hour). */
    private String crossMethod(Upgrade u){
        Double each=MethodComparison.coinsPerItem(data.prices,method,data.priceMode);
        double hours=MethodRates.windowHours(method,ability());
        String here=each==null?"coins unknown":"+"+compact((u.rateAfter()-u.rateBefore())/hours*each)+" coins / hour";
        if(bestRow==null||bestRow.method()==method)return each==null?"":"Here: "+here+" ("+data.priceMode.label+")";
        Double g=bestGains.get(u);
        return "Here: +"+fmt(u.gain())+"%, "+here+"\nIn "+bestRow.method().label+" (best by coins / hour): "+(g==null||g<=0?"no rate change":"+"+fmt(g*100)+"%, +"+compact(g*bestRow.coinsPerHour())+" coins / hour");
    }
    private String costText(Upgrade u){return u.cost()==null?("GRIND".equals(u.item().category)?"Grinding":"Price unknown"):u.cost()==0?"Free":coins(u.cost());}
    private void upgradeCards(Profile p){
        if(otherView){otherCards(p);return;}
        boolean focused=byStat;
        if(!p.inventoryEnabled){card("Inventory unavailable",RED,0).line("Gear recommendations are paused. Enable Inventory API in SkyBlock Settings, then reload after the profile cache expires.",TEXT);return;}
        if(calculating){card("Calculating upgrades...",CYAN,0).line("You can switch tabs or close this screen while calculations run.",MUTED);return;}
        if(!focused&&!data.hasStats(method)){card(data.statsLoading(method)?"Loading automatic stats...":"Enter your effective stats",GOLD,0).line("Automatic method stats come from your loaded profile. Type Stats adds or overrides values.",TEXT)
                .line("Rank: stat points works without them and ranks coins per stat point.",CYAN);return;}
        boolean rated=!focused&&upgrades.stream().anyMatch(u->u.rateBefore()!=null);
        String model=focused?stat.label+": cheapest points first.":rated?"Model: rate gain (automatic "+method.label+" rate, "+MethodRates.unit(method,ability())+"). Mining Speed counts only when it changes ticks; ability cooldown counts through uptime."
                :ProgressionEngine.rateModel(method)?"Model: drop multiplier (rate unavailable for these stats). Fortune x Spread x Pristine.":"Model: drop multiplier (no rate model for "+method.label+" yet).";
        String buy="Owned materials are used first. Supported socket unlock and removal fees are included.\nCosts are cash outlay; net cost subtracts the replaced item's sale quote when one exists; modifiers are not transferred.";
        if(!focused){
            Card head=card("Step-by-step plan",GOLD,FULL);
            head.tip="Take the best value, apply it, recompute everything, repeat. Unpriced and blocked upgrades are skipped.\n"+model+"\n"+buy;
            if(plan.isEmpty())head.line("No priced upgrade available to plan with.",MUTED);
            else{var last=plan.getLast();
                head.right="+"+fmt(last.totalGain())+"% "+(rated?"rate":"drops")+" for "+coins(last.totalCost());head.rightColor=GREEN;
                String resale=last.totalResale()>0||last.unpricedResale()>0?"Selling what the steps replace: "+coins(last.totalResale())+" back, net "+coins(last.totalCost()-last.totalResale())
                        +(last.unpricedResale()>0?" ("+last.unpricedResale()+" replaced item"+(last.unpricedResale()>1?"s":"")+" without a sale price)":""):"";
                if(!resale.isEmpty())head.line(resale,CYAN);
                if(bestRow!=null)head.line(bestRow.method()==method?method.label+" is your best method by coins / hour.":"Best method by coins / hour: "+bestRow.method().label+" (hover an upgrade for its value there).",MUTED);
                int n=0;
                for(var step:plan){var u=step.upgrade();n++;
                    Card c=card("",GOLD,n%2==1?0:1);
                    c.add(new Entry(upgradeIcon(u),n+". "+u.item().name+(u.current()!=null&&u.item().modification?" ("+u.current().name+")":""),TEXT,
                            "Total +"+fmt(step.totalGain())+"% for "+coins(step.totalCost()),MUTED,"+"+fmt(u.gain())+"%",GREEN,costText(u),Objects.equals(u.cost(),0.0)?GREEN:TEXT,
                            upgradeTip(u,false)+(step.rate()!=null?"\nAfter this step: "+fmt(step.rate())+" "+MethodRates.unit(method,ability()):""),null));
                }
                if(n%2==1)card("",GOLD,1).line(plan.size()==PLAN_STEPS?"First "+PLAN_STEPS+" steps shown.":"End of the plan.",MUTED);
            }
        }
        Card list=card(focused?stat.label+" | cheapest points first":"All upgrades, best value first",GOLD,FULL);
        list.right=upgrades.size()+" found";
        var known=upgrades.stream().filter(u->u.cost()!=null&&Double.isFinite(u.costPerUnit())&&u.blocked().isEmpty()).limit(4).toList();
        StringJoiner eff=new StringJoiner("\n");for(var u:known)eff.add(coins(u.costPerUnit())+(focused?" / point":" / 1%")+"  "+u.item().name);
        list.tip=model+"\nEach card compares to your current setup; they are alternatives, not a combined shopping list.\n"+buy+(known.isEmpty()?"":"\nBest cost efficiency:\n"+eff);
        if(upgrades.isEmpty()){list.line("No supported upgrades found. This does not mean the account is maxed.",MUTED);return;}
        int rank=0;
        for(var u:upgrades){rank++;
            Card c=card("",u.blocked().isEmpty()?GOLD:RED,rank%2==1?0:1);
            String gain=focused?(u.cost()==null||!Double.isFinite(u.costPerUnit())?"":coins(u.costPerUnit())+" / pt"):"+"+fmt(u.gain())+"%";
            String changes=changes(u);
            c.add(new Entry(upgradeIcon(u),"#"+rank+"  "+u.item().name,u.blocked().isEmpty()?TEXT:RED,!u.blocked().isEmpty()?u.blocked():!changes.isEmpty()?changes:u.current()!=null?"Replaces: "+u.current().name:"",!u.blocked().isEmpty()?RED:CYAN,
                    gain,GREEN,u.owned()?"Owned":costText(u),u.owned()?GREEN:u.cost()==null?MUTED:TEXT,upgradeTip(u,focused),null));
        }
    }

    /** Upgrades, other view: the guide stage, bonuses the API can't check, and progress to grind, each ranked by rate gain here. */
    private void otherCards(Profile p){
        if(calculating){card("Calculating...",CYAN,0).line("You can switch tabs or close this screen while calculations run.",MUTED);return;}
        if(!data.hasStats(method)){card(data.statsLoading(method)?"Loading automatic stats...":"Enter your effective stats",GOLD,0).line("Gains use your automatic "+method.label+" stats. Type Stats adds or overrides values.",TEXT);return;}
        var r=other;if(r==null){card("Nothing to show",MUTED,0).line("Load your profile for bonuses and grinding steps.",MUTED);return;}
        String model=r.rated()?"Gain: rate gain in "+method.label+" ("+MethodRates.unit(method,ability())+"), the same model as Purchases.":"Gain: drop multiplier (rate unavailable for these stats).";
        var s=r.stage();
        Card stage=card("Guide stage: "+s.name(),PURPLE,FULL);
        stage.tip="Placed on the supplied early / mid / end mining checklist from its stage headings only (HOTM tier, Divan armor, drill, Pendant), for orientation.\nIt is not a fixed optimal order, and its unticked rows say nothing about your profile. Your next steps are the ranked lists below and on Purchases.";
        stage.line("From: "+String.join(", ",s.because()),TEXT).line(s.next(),MUTED);
        String key="stage:"+s.name();boolean open=expanded.contains(key);
        stage.add(new Text((open?"- ":"+ ")+"Checklist items ("+s.nextItems().size()+")",CYAN,"\"(you own one)\" means the API shows that item somewhere; nothing else is checked.",()->toggle(key)));
        if(open)for(String item:s.nextItems())stage.line("  "+item,item.endsWith("(you own one)")?GREEN:MUTED);

        Card head=card("Can't check: skip if you already have it",CYAN,FULL);head.right=r.unchecked().size()+" bonuses";
        head.tip="Permanent bonuses the profile API never shows (or whose key has never been observed). A missing field is not proof you lack them.\nPrice: Coflnet buy quote for the whole amount; unknown stays unknown.\n"+model;
        if(r.unchecked().isEmpty())head.line("No such bonus changes "+method.label+".",MUTED);
        else if(r.hidden()>0)head.line(r.hidden()+" other bonus"+(r.hidden()>1?"es don't":" doesn't")+" change "+method.label+".",MUTED);
        int n=0;
        for(var e:r.unchecked()){n++;
            Card c=card("",CYAN,n%2==1?0:1);
            c.add(new Entry(otherIcon(e),e.name(),TEXT,e.effect(),MUTED,"+"+fmt(e.gain())+"%",GREEN,e.cost()==null?"Price unknown":coins(e.cost()),e.cost()==null?MUTED:TEXT,otherTip(e,model),null));
        }
        if(n%2==1)card("",CYAN,1).line("Can't check: skip any you already have.",MUTED);

        Card grind=card("Progress you have to grind",GREEN,FULL);grind.right="by rate gain";
        grind.tip="HOTM perks, tokens and tiers, Mining skill, collections, pet levels, Compact and forge-only items. Ranked by the gain alone: coins are not part of the ranking.\n"+model;
        if(!p.inventoryEnabled)grind.line("Inventory API off: Compact and forge-only items need your gear.",RED);
        if(r.grinding().isEmpty())grind.line("Nothing to grind that changes "+method.label+".",MUTED);
        n=0;
        for(var e:r.grinding()){n++;
            Card c=card("",GREEN,n%2==1?0:1);
            c.add(new Entry(otherIcon(e),"#"+n+"  "+e.name(),TEXT,e.distance(),e.distance().equals("Ready")?GREEN:CYAN,e.gain()==null?"not modelled":"+"+fmt(e.gain())+"%",e.gain()==null?MUTED:GREEN,
                    "Grinding",MUTED,otherTip(e,model),null));
        }
    }
    private ItemStack otherIcon(OtherUpgrades.Entry e){
        String id=e.id().startsWith("forge:")?e.id().substring(6):e.id();
        return icon(id,switch(e.group()){case "Skill","Collection"->"HOTM";default->e.group();});
    }
    private String otherTip(OtherUpgrades.Entry e,String model){
        StringJoiner tip=new StringJoiner("\n");
        tip.add(e.name());
        if(!e.effect().isEmpty())tip.add("Effect: "+e.effect());
        tip.add(e.gain()==null?"Gain: not modelled":"Gain: +"+fmt(e.gain())+"% in "+method.label);
        if(!e.requirement().isEmpty())tip.add("Needs: "+e.requirement());
        if(!e.distance().isEmpty())tip.add(e.distance());
        if(!e.how().isEmpty())tip.add("How: "+e.how());
        boolean forge=e.id().startsWith("forge:");
        if(e.cost()!=null)tip.add((forge?"Missing materials: ":"Price: ")+coins(e.cost()));
        else if(forge)tip.add("Missing materials: price unknown");
        if(!e.note().isEmpty())tip.add(e.note());
        tip.add(model);
        return tip.toString();
    }

    // Methods
    private void methodCards(){
        sessionCard(false);
        methodChart(false);
        rateDetail();
        sameChangeCard();
        manualCard();
    }
    private void assumptions(Card c,List<String> list){
        if(list.isEmpty())return;String key="assume:"+method;boolean open=expanded.contains(key);
        c.add(new Text((open?"- ":"+ ")+"Assumptions ("+list.size()+")",MUTED,null,()->toggle(key)));
        if(open)for(String a:list)c.line("  "+a,MUTED);
    }
    private void coinPairs(Card c,double perHour){
        for(var mode:MethodComparison.PriceMode.values()){Double each=MethodComparison.coinsPerItem(data.prices,method,mode);
            String source=MethodComparison.priceSource(data.prices,method,mode);
            c.add(new Pair("Coins / hour, "+mode.label,MUTED,each==null?"unknown":coins(perHour*each),each==null?MUTED:mode==data.priceMode?GOLD:TEXT,each==null?null:fmt(each)+" each"+(source.isEmpty()?"":"\n"+source),null));}
    }
    /** The selected method's automatic rate: big number, coins at each price, ticks and the next threshold; assumptions expand. */
    private void rateDetail(){
        var i=data.inputs(method);StatsReport r=data.stats(method);
        Card c=card(method.label+" | automatic rate",GOLD,0);c.icon=productIcon(method);
        if(!MethodRates.supported(method)){c.line("No rate model for this method.",MUTED);return;}
        if(r==null&&!data.demo&&!i.stats.containsKey(Stat.MINING_SPEED)){c.line(data.statsLoading(method)?"Loading automatic stats...":"Load your profile (or enter stats in Type Stats) for the automatic rate.",TEXT);return;}
        var entry=data.comparison()==null?null:data.comparison().entry(method);
        String tool=entry!=null&&!entry.tool.isEmpty()?entry.tool:toolName(i.tool);StatContext.Zone zone=data.zone(method);
        try{
            var gem=MethodRates.gem(method);var b=MethodRates.block(method);
            if(b==null){
                var rate=MethodRates.gems(method,data.effective(method),i.efficiency,r==null?null:r.ability,zone);
                c.tip=gem.name()+" (block strength "+fmt(gem.strength())+")\nGuide reference ("+gem.guideNote()+", "+fmt(gem.guideEfficiency())+"% efficiency): "+fmt(gem.guideFlawlessPerHour())+" Flawless / hour";
                c.add(new Big(fmt(rate.flawlessPerHour()),"Flawless "+gem.name().split(" ")[0]+" / hour",CYAN,null,null));
                coinPairs(c,rate.flawlessPerHour());
                c.pair("Efficiency",fmt(i.efficiency)+"%",TEXT).pair("Blocks / hour",fmt(rate.blocksPerHour()),TEXT).pair("Rough / block",fmt(rate.roughPerBlock()),TEXT);
                c.add(new Pair("Mining Speed",MUTED,fmt(rate.speed())+": "+rate.ticks()+" ticks",TEXT,rate.uptime()>0?rate.boostedTicks()+" ticks during the ability ("+fmt(rate.uptime()*100)+"% uptime)":null,null));
                double next=MethodRates.nextTickSpeed(rate.speed(),gem.strength());
                if(Double.isFinite(next))c.add(new Pair("Next tick",MUTED,fmt(next)+" Mining Speed",GOLD,MethodRates.ticks(next,gem.strength())+" ticks there; speed between thresholds adds nothing.",null));
                c.pair("Tool | zone",tool+" | "+(zone==null?"none":zone.label),TEXT);
                if(rate.kept()<1)c.pair("Heat / Cold",fmt(Math.round(rate.kept()*1000)/10.0)+"% of the rate kept",MUTED);
                secondaryPairs(c,rate.flawlessPerHour(),zone);
                assumptions(c,rate.assumptions());
            }else{
                var rate=MethodRates.blocks(method,data.effective(method),i.efficiency,r==null?null:r.ability,zone);
                String window=rate.windowHours()==1?"hour":b.abilityCycle()?"ability cycle ("+fmt(rate.windowHours()*3600)+"s)":b.window();
                c.tip=(b.strength()>0?b.name()+" (block strength "+fmt(b.strength())+"; instamine above "+fmt(b.instamineFactor()*b.strength())+" Mining Speed, "+fmt(5*b.instamineFactor()*b.strength())+" airborne)"
                        :b.name()+" (block strength not listed on the wiki; assumed instamined)")+"\nGuide reference ("+b.guideNote()+")"
                        +(rate.windowHours()!=1?"\nWindow: "+MethodComparison.window(method,r==null?null:r.ability):"");
                c.add(new Big(compact(rate.perWindow()),b.unit()+" / "+window,CYAN,fmt(rate.perWindow())+" "+b.unit()+" per "+window,null));
                if(rate.windowHours()!=1)c.pair("Per hour",fmt(rate.perHour()),TEXT);
                coinPairs(c,rate.perHour());
                c.pair("Efficiency",fmt(i.efficiency)+"%",TEXT).pair("Blocks / hour",fmt(rate.blocksPerHour()),TEXT).pair(b.unit()+" / block",fmt(rate.dropsPerBlock()),TEXT);
                c.add(new Pair("Mining Speed",MUTED,fmt(rate.speed())+": "+ticksText(rate.groundTicks())+" / "+ticksText(rate.airTicks())+" air",TEXT,
                        "On the ground and airborne."+(rate.uptime()>0?"\nDuring the ability "+ticksText(rate.boostedGroundTicks())+" / "+ticksText(rate.boostedAirTicks())+" ("+fmt(rate.uptime()*100)+"% uptime)":""),null));
                double nextGround=b.strength()>0?MethodRates.nextTickSpeed(rate.speed(),b.strength(),b.instamineFactor()):Double.NaN;
                if(Double.isFinite(nextGround))c.add(new Pair("Next threshold",MUTED,fmt(nextGround)+" on the ground",GOLD,"Speed between thresholds adds nothing.",null));
                else if(rate.airTicks()>1&&b.strength()>0)c.add(new Pair("Next threshold",MUTED,fmt(5*MethodRates.nextTickSpeed(rate.speed()/5,b.strength(),b.instamineFactor())-4)+" airborne",GOLD,"Speed between thresholds adds nothing.",null));
                var ab=ability();
                if(ab!=null&&ab.speedBoost()&&rate.boostedGroundTicks()>1&&b.strength()>0)c.pair("Instamine in ability",fmt(Math.floor(b.instamineFactor()*b.strength()/(1+ab.boostPercent()/100))+1)+" Mining Speed",MUTED);
                c.pair("Tool | zone",tool+" | "+(zone==null?"none":zone.label),TEXT);
                if(rate.kept()<1)c.pair("Heat / Cold",fmt(Math.round(rate.kept()*1000)/10.0)+"% of the rate kept",MUTED);
                secondaryPairs(c,rate.perHour(),zone);
                assumptions(c,rate.assumptions());
            }
        }catch(IllegalArgumentException e){c.line(e.getMessage(),RED);}
        var row=data.comparisonRows().stream().filter(x->x.method()==method).findFirst().orElse(null);
        if(row!=null)c.line("Drops: "+row.drops(),MUTED,"Window: "+row.window()+"\n"+row.priceNote()+(row.statsComplete()?"":"\nSome counted stat sources are unknown, so the rate is a lower bound."));
    }
    /** Secondary drops of the selected method: amount / hour and coins at the chosen price; source and assumptions in the tooltip. */
    private void secondaryPairs(Card c,double mainPerHour,StatContext.Zone zone){
        var i=data.inputs(method);StatsReport r=data.stats(method);double sum=0;boolean any=false;
        for(var d:MethodRates.secondary(method,data.effective(method),i.efficiency,r==null?null:r.ability,zone)){
            var v=MethodComparison.value(d,data.prices,data.priceMode);
            String amount=Double.isFinite(d.perHour())?fmt(Math.round(d.perHour()*10)/10.0)+" "+d.unit()+" / h":"amount unknown";
            String value=v.coinsPerHour()==null?(Double.isFinite(d.perHour())?"coins unknown":"not counted"):(v.atLeast()?"at least ":"")+coins(v.coinsPerHour())+" / h";
            if(v.coinsPerHour()!=null){sum+=v.coinsPerHour();any=true;}
            c.add(new Pair("+ "+d.name(),MUTED,amount+" | "+value,v.coinsPerHour()==null?MUTED:TEXT,d.source()+(v.note().isEmpty()?"":"\n"+v.note())
                    +(v.coinsPerUnit()==null?"":"\n"+fmt(Math.round(v.coinsPerUnit()))+" coins per "+d.unit()+", "+data.priceMode.label),null));
        }
        Double each=MethodComparison.coinsPerItem(data.prices,method,data.priceMode);
        if(any&&each!=null)c.pair("Total coins / hour, "+data.priceMode.label,coins(mainPerHour*each+sum),GOLD);
    }
    private static String secondaryText(MethodComparison.Row row){
        StringBuilder b=new StringBuilder();
        for(var v:row.secondary())b.append("\n+ ").append(v.drop().name()).append(": ").append(v.coinsPerHour()==null?(Double.isFinite(v.drop().perHour())?"coins unknown":"amount unknown"):(v.atLeast()?"at least ":"")+coins(v.coinsPerHour())+" / hour");
        if(!row.secondary().isEmpty()&&row.totalCoinsPerHour()!=null)b.append("\nTotal ").append(coins(row.totalCoinsPerHour())).append(" / hour");
        return b.toString();
    }
    private static String ticksText(int t){return t==1?"instamine":t+" ticks";}
    private void sameChangeCard(){
        var r=data.comparison();var rows=data.comparisonRows();if(r==null||rows.isEmpty())return;
        Card same=card("Same change, different value",CYAN,1);
        same.tip="+50 Mining Fortune / +100 Mining Speed, on each method's own stats: rate gain and coins / hour.";
        same.add(new Pair("Method",MUTED,"+50 Fortune / +100 Speed",MUTED,null,null));
        for(var row:rows){var stats=data.comparisonStats(row.method());var e=r.entry(row.method());if(stats==null||e==null)continue;double eff=data.inputs(row.method()).efficiency;
            Double f=MethodComparison.gain(row.method(),stats,eff,e.ability,Map.of(Stat.MINING_FORTUNE,50.0),1),sp=MethodComparison.gain(row.method(),stats,eff,e.ability,Map.of(Stat.MINING_SPEED,100.0),1);
            same.add(new Pair(row.method().label,row.method()==method?GREEN:TEXT,shortGain(f)+" / "+shortGain(sp),row.method()==method?GREEN:MUTED,gainText(f,row)+" / "+gainText(sp,row),null));}
    }
    private static String shortGain(Double g){return g==null?"?":g<=0?"+0%":"+"+fmt(g*100)+"%";}
    private static String gainText(Double g,MethodComparison.Row row){
        if(g==null)return "?";if(g<=0)return "+0%";
        return "+"+fmt(g*100)+"%"+(row.coinsPerHour()==null?"":" ("+compact(g*row.coinsPerHour())+"/h)");
    }
    /** Manual scenario from Type Stats (measured blocks/sec, sale price, item weight). */
    private void manualCard(){
        var i=data.inputs(method);var stats=data.effective(method);Card c=card("Manual rate scenario",MUTED,1);c.optional=true;
        c.tip="Uses Type Stats: effective stats, measured active blocks/sec, base drops, sale price and item weight.\nGems use rough-equivalent units; match your sale price and weight to them.\nSpread assumes extra blocks are available. Fuel, taxes, route, abilities and shaft loot are excluded. Shafting estimates Glacite only.";
        if(i.blocksPerSecond<=0){c.line("Enter measured blocks/sec in Type Stats (More page).",MUTED,"Default mining efficiency: "+fmt(method.defaultEfficiency())+"%");return;}
        try{var r=ProgressionEngine.rate(method,stats,i.efficiency,i.blocksPerSecond,i.baseDrops,i.coinsPerItem,i.coleweightPerItem);
            c.pair("Uptime",fmt(i.efficiency)+"%",TEXT).pair("Items / hour",fmt(r.itemsPerHour()),CYAN)
                    .pair("Coins / hour",i.coinsPerItem>0?coins(r.coinsPerHour()):"enter sale price",i.coinsPerItem>0?GOLD:MUTED)
                    .pair("Coleweight / hour",i.coleweightPerItem>0?fmt(r.coleweightPerHour()):"enter item weight",i.coleweightPerItem>0?GREEN:MUTED);
        }catch(IllegalArgumentException e){c.line(e.getMessage(),RED);}
    }

    // More
    private void moreCards(Profile p){
        if(p!=null){
            Card basic=card("Heart of the Mountain & skills",GOLD,0);
            if(p.milestones.isEmpty())basic.line("Skill progress is not available for this profile.",MUTED);
            for(var m:p.milestones)basic.add(new Meter(m.name(),fmt(m.current())+" / "+fmt(m.maximum()),m.current()>=m.maximum()?GREEN:TEXT,m.maximum()>0?m.current()/m.maximum():0,m.current()>=m.maximum()?GREEN:CYAN,null,null));
            Card powder=card("Powder",CYAN,0);powder.tip="Unspent powder, as shown in game. Targets depend on your HOTM build.";
            boolean hasPowder=false;
            for(var e:p.progress.entrySet())if(e.getKey().contains("powder")){powder.pair(e.getKey(),fmt(e.getValue()),CYAN);hasPowder=true;}
            if(!hasPowder)powder.line("Powder data unavailable.",MUTED);
            gearAudit(p);
            Card details=card("Profile progression",GOLD,0);for(var e:p.progress.entrySet())details.pair(e.getKey(),fmt(e.getValue()),TEXT);
            if(!p.notes.isEmpty()){Card notes=card("Profile notes",CYAN,1);for(String note:p.notes)notes.line(note,MUTED);}
        }
        card("Help",GOLD,0).line("Tabs: Home (summary), Gear (loadout + stat sources), Upgrades (plan), Methods (rates).",TEXT)
                .line("Hover bars, rows and card titles for details; click + lines to expand.",MUTED)
                .line("Keys: R reload, F1 this page, 1-4 tabs, PgUp/PgDn or the wheel to scroll, ESC close.",MUTED);
        card("Manual checks",GOLD,0).line("Check HOTM perks and powder, pet level/item, reforges, drill modules/fuel, collections, attributes and temporary buffs.",TEXT).line("Stored gear is not necessarily equipped or usable.",MUTED);
        card("Inventory API help",CYAN,1).line("SkyBlock Menu > Settings > API Settings > Inventory API: ON.",TEXT).line("Profiles cache for five minutes; prices for fifteen. Unknown prices never mean free.",MUTED);
        card("Progression scope",CYAN,1).line("Every method has a rate model; Methods ranks them by coins per hour. None is validated against a measured session yet.",MUTED);
    }
    /** Base-item bars per slot for the focused stat, with supported enchant and gemstone progress (old Overview). */
    private void gearAudit(Profile p){
        Card c=card("Gear audit | "+stat.label,GOLD,1);
        c.tip="Base-item bars compare the supported catalog. Modifier bars track supported enchants and gemstones. Change the stat with Stat focus.";
        if(!p.inventoryEnabled){c.line("Inventory API off: gear audit paused.",RED);return;}
        if(calculating){c.line("Calculating...",MUTED);return;}
        if(overview.isEmpty()){c.line("No supported gear for this stat. Choose another stat focus.",MUTED);return;}
        for(var row:overview){
            Item current=row.current();
            StringJoiner tip=new StringJoiner("\n");
            if(row.value()<row.maximum())tip.add("Target: "+row.target().name);
            if(current!=null){tip.add("Item "+(current.observed?"lore":"base")+": "+fmt(Model.stat(current.stats,stat))+" "+stat.label);if(!current.location.isBlank())tip.add("Found in "+current.location);
                for(var m:GearUpgrades.progress(current,method,stat))tip.add(m.name()+"  "+fmt(m.current())+" / "+fmt(m.maximum()));}
            c.add(new Entry(icon(current!=null?current.id:row.target().id,row.group()),row.group()+": "+(current==null?"Missing":current.name),current==null?RED:TEXT,
                    row.value()<row.maximum()?"Target: "+row.target().name:"Best base item",MUTED,fmt(row.value())+" / "+fmt(row.maximum()),row.value()>=row.maximum()?GREEN:CYAN,"",MUTED,tip.toString(),null));
        }
    }

    // ---- Layout and drawing ----
    private List<FormattedCharSequence> wrapTip(String tip){
        if(tip==null||tip.isEmpty())return null;
        List<FormattedCharSequence> out=new ArrayList<>();int w=Math.max(160,Math.min(300,width/2));
        for(String part:pretty(tip).split("\n"))out.addAll(font.split(Component.literal(part),w));
        return out;
    }
    /** Word wrap of one text, run on content changes only. */
    private List<String> wrap(String text,int w){
        List<String> out=new ArrayList<>();String rest=text;int guard=0;
        while(!rest.isEmpty()&&guard++<100){String part=font.plainSubstrByWidth(rest,Math.max(20,w));if(part.isEmpty())break;
            if(part.length()<rest.length()&&part.lastIndexOf(' ')>0)part=part.substring(0,part.lastIndexOf(' '));
            rest=rest.substring(part.length()).stripLeading();out.add(part);}
        return out;
    }
    private Laid lay(Row row,int dy,int rw){
        var tip=wrapTip(row.tip());
        return switch(row){
            case Text t->{var lines=wrap(pretty(t.text()),rw);yield new Laid(row,dy,lines.size()*11+1,lines,tip,null);}
            case Meter m->new Laid(row,dy,21,List.of(fit(font,pretty(m.label()),rw-font.width(m.value())-6)),tip,null);
            case Split s->new Laid(row,dy,10,List.of(),tip,null);
            case Legend l->{int[] pos=new int[l.segs().size()*2];int x=0,y=0;
                for(int k=0;k<l.segs().size();k++){int w=10+font.width(l.segs().get(k).label());if(x>0&&x+w>rw){x=0;y+=11;}pos[2*k]=x;pos[2*k+1]=y;x+=w+8;}
                yield new Laid(row,dy,y+13,List.of(),tip,pos);}
            case Pair p->new Laid(row,dy,11,List.of(fit(font,pretty(p.left()),rw-font.width(p.right())-6)),tip,null);
            case Entry e->{int rightW=Math.max(font.width(e.right()),font.width(e.right2()));
                yield new Laid(row,dy,26,List.of(fit(font,pretty(e.title()),rw-22-rightW-6),fit(font,pretty(e.sub()),rw-22-rightW-6)),tip,null);}
            case Slots s->{int per=Math.max(1,(rw+4)/26);int[] pos=new int[s.slots().size()*2];
                for(int k=0;k<s.slots().size();k++){pos[2*k]=(k%per)*26;pos[2*k+1]=(k/per)*26;}
                yield new Laid(row,dy,((s.slots().size()+per-1)/per)*26+2,List.of(),tip,pos);}
            case Big b->new Laid(row,dy,22,List.of(fit(font,b.unit(),rw-2*font.width(b.value())-8)),tip,null);
            case Bar b->new Laid(row,dy,18,List.of(fit(font,b.label(),labelWidth(rw)-22)),tip,null);
            default->new Laid(row,dy,0,List.of(),tip,null);
        };
    }
    private static int labelWidth(int rw){return Math.min(110,Math.max(60,rw/4));}
    private void layoutCards(){slotTips.clear();
        placed.clear();int gap=8,columns=wide?2:1,w=columns==2?(span-34)/2:span-20,full=span-26;
        boolean shortScreen=height<300;
        int[] ys={0,0};
        for(Card c:cards){
            if(c.optional&&shortScreen)continue;
            boolean isFull=c.column==FULL&&columns==2;int col=columns==2&&!isFull?(c.column==AUTO?(ys[0]<=ys[1]?0:1):c.column):0,cw=isFull?full:columns==2?w:full;
            int rw=cw-18,dy=c.title.isEmpty()?5:27;List<Laid> rows=new ArrayList<>();
            for(Row row:c.rows){Laid l=lay(row,dy,rw);rows.add(l);dy+=l.h()+2;}
            int h=dy+3;
            int inset=c.icon.isEmpty()?9:29,rightW=c.right.isEmpty()?0:font.width(c.right)+9;
            String shown=pretty(c.title),title=fit(font,shown,cw-inset-9-rightW);
            int y=isFull?Math.max(ys[0],ys[1]):ys[col];
            placed.add(new Placed(c,left+10+col*(w+gap),y,cw,h,title,left+10+col*(w+gap)+cw-rightW,wrapTip(c.tip!=null?c.tip:title.equals(shown)?null:shown),rows));
            if(isFull){ys[0]=ys[1]=y+h+gap;}else ys[col]=y+h+gap;
        }
        contentHeight=Math.max(ys[0],ys[1]);scroll=Math.clamp(scroll,0,maxScroll());
    }
    private int maxScroll(){return Math.max(0,contentHeight-Math.max(1,bodyBottom-bodyTop));}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(x>=left&&x<=left+span&&y>=bodyTop&&y<=bodyBottom){scroll=Math.clamp(scroll-(int)(vertical*32),0,maxScroll());return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    /** Click or tooltip target under the mouse: a row, or one slot of a slot grid. */
    private record Hit(Runnable click,List<FormattedCharSequence> tip) {}
    private Hit hit(double mx,double my){
        if(mx<left||mx>left+span||my<bodyTop||my>=bodyBottom)return null;
        for(Placed box:placed){int y=bodyTop+box.y-scroll;
            if(mx<box.x||mx>=box.x+box.width||my<y||my>=y+box.height)continue;
            if(my<y+21&&!box.card.title.isEmpty())return box.tip==null?null:new Hit(null,box.tip);
            int x0=box.x+9;
            for(Laid l:box.rows){int ry=y+l.dy();if(my<ry||my>=ry+l.h())continue;
                if(l.row() instanceof Slots s){for(int k=0;k<s.slots().size();k++){int sx=x0+l.pos()[2*k],sy=ry+l.pos()[2*k+1];
                    if(mx>=sx&&mx<sx+22&&my>=sy&&my<sy+22){var slot=s.slots().get(k);return new Hit(slot.click(),wrapTipCached(slot));}}return null;}
                return new Hit(l.row().click(),l.tip());}
            return null;
        }
        return null;
    }
    private final Map<Slot,List<FormattedCharSequence>> slotTips=new IdentityHashMap<>();
    private List<FormattedCharSequence> wrapTipCached(Slot slot){return slotTips.computeIfAbsent(slot,s->wrapTip(s.tip()));}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(super.mouseClicked(event,doubleClick))return true;
        if(event.button()==0){Hit h=hit(event.x(),event.y());if(h!=null&&h.click()!=null){h.click().run();return true;}}
        return false;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(username!=null&&username.isFocused())return super.keyPressed(event);
        int key=event.key();
        if(key==82){if(!data.loading)data.load(username.getValue(),minecraft::execute,this::refresh);return true;}
        if(key==290){switchTab(MORE);return true;}
        if(key>=49&&key<=52){switchTab(key-49);return true;}
        if(key==267){scroll=Math.min(maxScroll(),scroll+page());return true;}
        if(key==266){scroll=Math.max(0,scroll-page());return true;}
        return super.keyPressed(event);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,0xD908090D);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        // Draw frame before widgets; do not request Minecraft's full-screen blur pass.
        panel(g,left,top,span,bottom-top,BACK);
        if(span>=520)g.text(font,"SkyProgression",left+10,top+13,GOLD,true);
        if(bodyBottom>bodyTop){
            g.enableScissor(left+4,bodyTop,left+span-4,bodyBottom);
            for(Placed box:placed){int y=bodyTop+box.y-scroll;if(y+box.height<bodyTop||y>bodyBottom)continue;drawCard(g,box,y,mx,my);}
            g.disableScissor();
            if(maxScroll()>0){int track=bodyBottom-bodyTop,thumb=Math.max(12,(int)((double)track*track/contentHeight));g.fill(left+span-6,bodyTop,left+span-3,bodyBottom,RAISED);int sy=bodyTop+(int)((track-thumb)*(double)scroll/maxScroll());g.fill(left+span-6,sy,left+span-3,sy+thumb,GOLD);}
        }
        g.text(font,footerState,left+58,bottom-18,footerStateColor,false);
        g.text(font,footerMessage,footerMessageX,bottom-18,data.demo?GOLD:MUTED,false);
        if(!footerKeys.isEmpty())g.text(font,footerKeys,footerKeysX,bottom-18,0xFF6B675F,false);
        if(my>=bottom-22&&my<bottom-6&&mx>=footerMessageX&&mx<=footerMessageX+font.width(footerMessage)&&!footerMessage.equals(data.message))g.setTooltipForNextFrame(font,Component.literal(data.message),mx,my);
        Hit h=hit(mx,my);if(h!=null&&h.tip()!=null)g.setTooltipForNextFrame(font,h.tip(),mx,my);
        super.extractRenderState(g,mx,my,delta);
    }
    private void drawCard(GuiGraphicsExtractor g,Placed box,int y,int mx,int my){
        Card c=box.card;
        panel(g,box.x,y,box.width,box.height,PANEL);
        if(!c.title.isEmpty()){
            g.fill(box.x+1,y+1,box.x+box.width-1,y+21,RAISED);g.fill(box.x+1,y+1,box.x+3,y+21,c.accent);
            int inset=c.icon.isEmpty()?9:29;if(!c.icon.isEmpty())g.fakeItem(c.icon,box.x+8,y+3);
            g.text(font,box.title,box.x+inset,y+7,c.accent,false);
            if(!c.right.isEmpty())g.text(font,c.right,box.rightX,y+7,c.rightColor,false);
            if(c.tip!=null)g.fill(box.x+box.width-4,y+3,box.x+box.width-2,y+5,MUTED);
        }else g.fill(box.x,y+1,box.x+2,y+box.height-1,c.accent);
        int x0=box.x+9,rw=box.width-18;
        for(Laid l:box.rows){int ry=y+l.dy();if(ry+l.h()<bodyTop||ry>bodyBottom)continue;
            boolean hover=mx>=x0-4&&mx<x0+rw+4&&my>=ry&&my<ry+l.h()&&my>=bodyTop&&my<bodyBottom;
            drawRow(g,l,x0,ry,rw,hover);}
    }
    private void drawRow(GuiGraphicsExtractor g,Laid l,int x0,int ry,int rw,boolean hover){
        switch(l.row()){
            case Text t->{int color=hover&&t.click()!=null?CYAN:t.color();for(int k=0;k<l.text().size();k++)g.text(font,l.text().get(k),x0,ry+k*11,color,false);}
            case Meter m->{if(hover&&m.click()!=null)g.fill(x0-4,ry-2,x0+rw+4,ry+l.h()-1,RAISED);
                g.text(font,l.text().getFirst(),x0,ry,TEXT,false);g.text(font,m.value(),x0+rw-font.width(m.value()),ry,m.valueColor(),false);Theme.bar(g,x0,ry+11,rw,m.progress(),m.color());}
            case Split s->{g.fill(x0,ry,x0+rw,ry+8,BACK);g.outline(x0-1,ry-1,rw+2,10,EDGE);
                if(s.scale()>0){double acc=0;int prev=x0;for(var seg:s.segs()){acc+=Math.max(0,seg.amount());int to=x0+(int)Math.round(Math.min(1,acc/s.scale())*rw);if(to>prev)g.fill(prev,ry,to,ry+8,seg.color());prev=to;}
                    if(prev>x0)g.fill(x0,ry,prev,ry+1,0x40FFFFFF);}}
            case Legend lg->{for(int k=0;k<lg.segs().size();k++){var seg=lg.segs().get(k);int x=x0+l.pos()[2*k],y=ry+l.pos()[2*k+1];
                g.fill(x,y+1,x+6,y+7,seg.color());g.text(font,seg.label(),x+10,y,MUTED,false);}}
            case Pair p->{if(hover&&p.tip()!=null)g.fill(x0-4,ry-1,x0+rw+4,ry+l.h(),RAISED);
                g.text(font,l.text().getFirst(),x0,ry+1,p.leftColor(),false);g.text(font,p.right(),x0+rw-font.width(p.right()),ry+1,p.rightColor(),false);}
            case Entry e->{if(hover&&(e.click()!=null||e.tip()!=null))g.fill(x0-4,ry,x0+rw+4,ry+l.h(),RAISED);
                g.fakeItem(e.icon(),x0,ry+5);
                g.text(font,l.text().get(0),x0+22,ry+3,e.titleColor(),false);g.text(font,l.text().get(1),x0+22,ry+14,e.subColor(),false);
                g.text(font,e.right(),x0+rw-font.width(e.right()),ry+3,e.rightColor(),false);g.text(font,e.right2(),x0+rw-font.width(e.right2()),ry+14,e.right2Color(),false);}
            case Slots s->{for(int k=0;k<s.slots().size();k++){var slot=s.slots().get(k);int x=x0+l.pos()[2*k],y=ry+l.pos()[2*k+1];
                g.fill(x,y,x+22,y+22,slot.selected()?RAISED:BACK);g.outline(x,y,22,22,slot.outline());if(slot.selected())g.outline(x-1,y-1,24,24,slot.outline());
                g.fakeItem(slot.icon(),x+3,y+3);}}
            case Big b->{if(hover&&b.click()!=null)g.fill(x0-4,ry-1,x0+rw+4,ry+l.h(),RAISED);
                g.pose().pushMatrix();g.pose().translate(x0,ry+2);g.pose().scale(2f,2f);g.text(font,b.value(),0,0,b.color(),false);g.pose().popMatrix();
                g.text(font,l.text().getFirst(),x0+2*font.width(b.value())+6,ry+10,MUTED,false);}
            case Bar b->{if(hover)g.fill(x0-4,ry,x0+rw+4,ry+l.h(),RAISED);
                g.fakeItem(b.icon(),x0,ry+1);int lw=labelWidth(rw);g.text(font,l.text().getFirst(),x0+20,ry+5,b.labelColor(),false);
                int vw=font.width(b.value()),bx=x0+lw,bw=Math.max(10,rw-lw-vw-8);
                g.fill(bx,ry+4,bx+bw,ry+14,BACK);int filled=(int)(Math.clamp(b.fraction(),0,1)*bw);if(filled>0){g.fill(bx,ry+4,bx+filled,ry+14,b.color());g.fill(bx,ry+4,bx+filled,ry+5,0x40FFFFFF);}
                g.text(font,b.value(),x0+rw-vw,ry+5,b.color()==DIM?TEXT:b.color(),false);}
            default->{}
        }
    }
    @Override public void removed(){analysisGeneration++;if(analysis!=null)analysis.cancel(true);}
    @Override public boolean isPauseScreen(){return false;}
    /** A stat amount with its unit: "13.6%" for percentage stats (the cooldown shown as a reduction), plain otherwise. */
    static String amount(Stat s,double v){return s==Stat.PICKAXE_ABILITY_COOLDOWN?"-"+fmt(v)+"%":s.percent?fmt(v)+"%":fmt(v);}
    static String signed(Stat s,double v){return s==Stat.PICKAXE_ABILITY_COOLDOWN?"-"+fmt(v)+"%":(v>=0?"+":"")+fmt(v)+(s.percent?"%":"");}
    static String fmt(double n){return String.format(Locale.US,"%,.2f",n).replaceAll("\\.?0+$","");}
    static String compact(double n){return n>=1_000_000?fmt(n/1_000_000)+"M":n>=1_000?fmt(n/1_000)+"K":fmt(n);}
    static String coins(double n){return compact(n)+" coins";}
}
