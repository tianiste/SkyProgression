package io.github.tianiste.skyprogression.client;

import io.github.tianiste.skyprogression.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

public final class ProgressionScreen extends Screen {
    private final ClientData data = ClientData.INSTANCE;
    private MiningMethod method = MiningMethod.MF_GEMS;
    private Stat stat = Stat.MINING_FORTUNE;
    private int tab, page;
    private EditBox username;
    private final List<Row> rows = new ArrayList<>();
    private static final String[] TABS = {"Overview", "Upgrades", "Stats", "Rates", "Other"};
    private static final int WHITE=0xFFE5ECF4, MUTED=0xFF9AAABD, GOLD=0xFFFFCE66, GREEN=0xFF83DBA4;
    private record Row(String text, int color, double progress) {}
    public ProgressionScreen() { super(Component.literal("SkyProgression")); }
    private void button(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x,y,w,20).build());
    }
    @Override protected void init() {
        String typed = username != null ? username.getValue() : data.player.isEmpty() ? minecraft.getUser().getName() : data.player;
        clearWidgets();
        int left=14, span=width-28;
        username = new EditBox(font,left,34,Math.max(70,span-160),20,Component.literal("Minecraft username"));
        username.setMaxLength(16); username.setValue(typed); addRenderableWidget(username);
        button(data.loading ? "Loading" : "Load",width-168,34,48,() -> data.load(username.getValue(), minecraft::execute, this::refresh));
        button("Demo",width-116,34,48,() -> {data.demo(); page=0;refresh();});
        button("Close",width-64,34,50,this::onClose);
        button(method.label + " v",left,58,(span-4)/2,() -> selectMethod());
        Profile p=data.profile();
        button(p==null?"Profile":p.name+" v",left+(span+4)/2,58,(span-4)/2,() -> selectProfile());
        int tw=(span-16)/5;
        for(int i=0;i<TABS.length;i++) {int selected=i; button((tab==i?"> ":"")+TABS[i],left+i*(tw+4),82,tw,() -> {tab=selected;page=0;refresh();});}
        button(stat.label+" v",left,106,Math.min(180,span-108),() -> selectStat());
        button("Type Stats",width-114,106,100,() -> minecraft.setScreen(new InputsScreen(this,data.inputs(method),method)));
        button("<",left,height-28,28,() -> {page=Math.max(0,page-1);});
        button(">",left+32,height-28,28,() -> {page=Math.min(pages()-1,page+1);});
        rebuild();
    }
    void refresh() { if(minecraft.screen==this) init(); }
    private void selectMethod() {
        minecraft.setScreen(new ChoiceScreen(this,"Mining method",Arrays.stream(MiningMethod.values()).map(m->m.label).toList(), n->{method=MiningMethod.values()[n];page=0;}));
    }
    private void selectStat() {
        minecraft.setScreen(new ChoiceScreen(this,"Choose a stat",Arrays.stream(Stat.values()).map(s->s.label).toList(), n->{stat=Stat.values()[n];page=0;}));
    }
    private void selectProfile() {
        if(data.report==null)return;
        minecraft.setScreen(new ChoiceScreen(this,"SkyBlock profile",data.report.profiles.stream().map(p->p.name+(p.selected?" (selected)":"")+" - "+p.mode).toList(), n->{data.profileIndex=n;page=0;}));
    }
    private void add(String text,int color) {
        if(text.isBlank()){rows.add(new Row("",color,-1));return;}
        String remaining=text;
        while(!remaining.isEmpty()) {
            String part=font.plainSubstrByWidth(remaining,Math.max(80,width-36));
            if(part.isEmpty())break;
            if(part.length()<remaining.length() && part.lastIndexOf(' ')>0)part=part.substring(0,part.lastIndexOf(' '));
            rows.add(new Row(part,color,-1));remaining=remaining.substring(part.length()).stripLeading();
        }
    }
    private void rebuild() {
        rows.clear(); Profile p=data.profile();
        add(data.message,data.demo?GOLD:MUTED);
        if(p==null) {
            add("Simple mining progression, in one place.",WHITE);
            add("Load uses the local companion service. See README for its one-time setup.",MUTED);
            add("Demo lets you browse the interface without a service or API key.",MUTED);
            add("Your API key belongs in the service environment, never in the mod or chat.",MUTED);return;
        }
        if(!p.inventoryEnabled) {
            add("Inventory unavailable or incomplete; gear recommendations are paused. See Other for setup help.",GOLD);
            if(tab==1 || tab==2) return;
        }
        switch(tab) {
            case 0 -> overview(p);
            case 1 -> upgrades(p,null);
            case 2 -> upgrades(p,stat);
            case 3 -> rates();
            case 4 -> other(p);
        }
        page=Math.clamp(page,0,Math.max(0,pages()-1));
    }
    private void overview(Profile p) {
        if (!p.milestones.isEmpty()) {
            add("Basic progression",GOLD);
            for (var m:p.milestones) {
                add(m.name(),WHITE);
                rows.add(new Row(fmt(m.current())+" / "+fmt(m.maximum())+" "+m.unit(),m.current()>=m.maximum()?GOLD:GREEN,
                        m.maximum()>0?Math.min(1,m.current()/m.maximum()):0));
            }
        }
        if (!p.inventoryEnabled) return;
        add(stat.label+" - base item progress",GOLD);
        add("Best owned per slot / catalog maximum. Bars exclude item modifiers; this is not full maxed-setup progress.",MUTED);
        for(var row:data.engine.overview(p,method,stat)) {
            add(row.group()+": "+(row.current()==null?"Missing":row.current().name),WHITE);
            rows.add(new Row(fmt(row.value())+" / "+fmt(row.maximum())+(row.value()>=row.maximum()?"  Complete (base)":""),row.value()>=row.maximum()?GOLD:GREEN,row.maximum()>0?Math.min(1,row.value()/row.maximum()):0));
            if(row.value()<row.maximum())add("Target: "+row.target().name+" | Missing "+fmt(row.maximum()-row.value()),MUTED);
        }
        add("Other sources",GOLD);
        add("Pet, HOTM perks, skill, collections and attributes: see Other. Full stat maxima need additional rules.",MUTED);
    }
    private void upgrades(Profile p,Stat focus) {
        var input=data.inputs(method);
        if(focus==null && !input.confirmed) {
            add("Enter effective stats with Type Stats to rank by cost per 1% more drops.",GOLD);
            add("Use Stats now to rank base-item improvements by coins per point.",WHITE);return;
        }
        add(focus==null?"Best value for "+method.label:focus.label+" - cheapest points first",GOLD);
        add("Base-item alternatives against best owned gear. Check modifiers before replacing. Full purchase cost; no assumed resale credit.",MUTED);
        if(focus==null)add("Drop-only estimate: speed thresholds, abilities, route and cold effects are excluded. Inspect speed/cold in Stats.",MUTED);
        var list=data.engine.upgrades(p,method,focus,input.stats,data.prices);
        if(list.isEmpty())add("No positive upgrades in this base-item catalog. This does not mean your setup is fully maxed.",WHITE);
        for(var u:list) {
            add("",MUTED);add(u.item().name+(u.owned()?" - already owned":""),GOLD);
            if(u.current()!=null)add("Replaces "+u.current().name,MUTED);
            StringJoiner changes=new StringJoiner(", ");
            u.delta().forEach((s,v)->{if(v!=0)changes.add((v>0?"+":"")+fmt(v)+" "+s.label);});
            add(changes.toString(),WHITE);
            add("Price: "+(u.cost()==null?"unknown":coins(u.cost()))+" | "+(u.cost()==null?"value unknown":coins(u.costPerUnit())+(focus==null?" / 1%":" / point")),GREEN);
            if(focus==null)add("Estimated drops: +"+fmt(u.gain())+"%",GREEN);
            Price quote=data.prices.get(u.item().id);
            add(quote!=null?quote.source+" | "+quote.updatedAt:u.item().source,MUTED);
            if(!u.blocked().isEmpty())add(u.blocked(),GOLD);
        }
    }
    private void rates() {
        var i=data.inputs(method);
        add(method.label+" - rate estimate",GOLD);
        add("Use Type Stats for effective stats, active blocks/sec, drops/block, unit sale price and Coleweight/item.",WHITE);
        if(!i.confirmed || i.blocksPerSecond<=0){add("Set your inputs first. Default: "+fmt(method.defaultEfficiency())+"% efficiency. No invented hourly rate.",GOLD);return;}
        try {
            var r=ProgressionEngine.rate(method,i.stats,i.efficiency,i.blocksPerSecond,i.baseDrops,i.coinsPerItem,i.coleweightPerItem);
            add(fmt(i.efficiency)+"% mining uptime",MUTED);
            add(fmt(r.itemsPerHour())+" items / hour",GREEN);
            add(i.coinsPerItem>0?coins(r.coinsPerHour())+" / hour":"Coins/hour: enter a sale price",GREEN);
            add(i.coleweightPerItem>0?fmt(r.coleweightPerHour())+" Coleweight / hour":"Coleweight/hour: enter weight per item",GREEN);
            add("For gems, items are rough-equivalent drops (a flawed gem is 80 rough). Match price and weight units.",MUTED);
            add("Spread assumes extra blocks are available. Abilities, fuel, taxes, latency, travel and shaft loot are not modeled. Shafting estimates Glacite only. MF Gems uses your chosen gem's inputs.",MUTED);
        }catch(IllegalArgumentException e){add(e.getMessage(),GOLD);}
    }
    private void other(Profile p) {
        add("Profile progression & manual checks",GOLD);
        for(var e:p.progress.entrySet())add(e.getKey()+": "+fmt(e.getValue()),WHITE);
        for(String note:p.notes)add(note,MUTED);
        add("Before buying",GOLD);
        add("Check HOTM perks and powder, Mining level, gemstone slots/quality, reforges, enchants, drill modules/fuel, pet level/item, collections, attributes and temporary buffs.",WHITE);
        add("Ownership includes API-visible inventory, armor, equipment, wardrobe, bags, ender chest, backpacks and sacks. Stored gear is not necessarily equipped.",MUTED);
        add("Only the base-item catalog is ranked. Recipe materials, resale values and manual upgrades are not priced automatically yet.",MUTED);
        add("Inventory API: SkyBlock Menu > Settings > API Settings > Inventory API: ON. Refresh after the 5-minute profile cache expires.",MUTED);
    }
    private int visibleRows(){return Math.max(1,(height-172)/18);}
    private int pages(){return Math.max(1,(rows.size()+visibleRows()-1)/visibleRows());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractRenderState(g,mx,my,delta);
        g.text(font,"SkyProgression",14,14,GOLD,true);
        String heading=data.demo?"DEMO":"Mining";g.text(font,heading,width-font.width(heading)-14,14,MUTED,false);
        int start=page*visibleRows(),y=138;
        for(int n=start;n<Math.min(rows.size(),start+visibleRows());n++) {
            Row r=rows.get(n);g.text(font,r.text,18,y,r.color,false);
            if(r.progress>=0) {g.fill(18,y+11,width-18,y+14,0xFF26384A);g.fill(18,y+11,18+(int)((width-36)*r.progress),y+14,r.color);}
            y+=18;
        }
        g.text(font,"Page "+(page+1)+" / "+pages(),84,height-22,MUTED,false);
    }
    @Override public boolean isPauseScreen(){return false;}
    static String fmt(double n){return String.format(Locale.US,"%,.2f",n).replaceAll("\\.?0+$","");}
    static String coins(double n){return fmt(n)+" coins";}
}
