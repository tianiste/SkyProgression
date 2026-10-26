package io.github.tianiste.skyprogression.client;

import io.github.tianiste.skyprogression.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.concurrent.Future;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.client.Theme.*;

public final class ProgressionScreen extends Screen {
    private final ClientData data=ClientData.INSTANCE;
    private MiningMethod method=MiningMethod.MF_GEMS;
    private Stat stat=Stat.MINING_FORTUNE;
    private int lastRevision, tab, left, top, span, bottom, bodyTop, bodyBottom, scroll, contentHeight, analysisGeneration;
    private boolean wide, calculating;
    private EditBox username;
    private Future<?> analysis;
    private List<Overview> overview=List.of();
    private List<Item> gear=List.of();
    private List<Upgrade> upgrades=List.of();
    private final List<Card> cards=new ArrayList<>();
    private final List<Placed> placed=new ArrayList<>();
    private static final String[] TABS={"Overview","Upgrades","Stats","Breakdown","Rates","Other"};
    private static final int BREAKDOWN=3,RATES=4,OTHER=5;
    private record Line(String text,int color,double progress) {}
    private static final class Card {
        ItemStack icon=ItemStack.EMPTY;
        final String title;final int accent,column;final List<Line> lines=new ArrayList<>();
        Card(String title,int accent,int column){this.title=title;this.accent=accent;this.column=column;}
        Card line(String value,int color){lines.add(new Line(value,color,-1));return this;}
        Card meter(String value,double current,double maximum){lines.add(new Line(value,current>=maximum?GREEN:CYAN,maximum>0?current/maximum:0));return this;}
    }
    private record Placed(Card card,int x,int y,int width,int height,List<Line> lines) {}
    public ProgressionScreen(){super(Component.literal("SkyProgression"));}
    private Theme.Action button(String label,int x,int y,int w,boolean selected,Runnable action){
        var b=new Theme.Action(label,x,y,w,20,selected,GOLD,action);addRenderableWidget(b);return b;
    }
    @Override protected void init(){
        String typed=username!=null?username.getValue():data.player.isEmpty()?minecraft.getUser().getName():data.player;
        clearWidgets();span=Math.min(960,width-16);left=(width-span)/2;top=Math.max(6,(height-620)/2);bottom=height-top;
        wide=span>=600;
        button("X",left+span-28,top+7,20,false,this::onClose);
        button("Demo",left+span-82,top+7,50,data.demo,()->{data.demo();scroll=0;refresh();}).active=data.ready;
        int y=top+34;
        int nameWidth=wide?125:Math.max(60,span-131);
        username=new EditBox(font,left+9,y,nameWidth,20,Component.literal("Minecraft username"));
        username.setMaxLength(16);username.setValue(typed);username.setTextColor(CYAN);addRenderableWidget(username);
        button(data.loading?"Cancel":"Load",left+13+nameWidth,y,54,false,()->{if(data.loading){data.cancel();refresh();}else data.load(username.getValue(),minecraft::execute,this::refresh);}).active=data.ready;
        Profile p=data.profile();
        String profile=p==null?"Profile":p.name+" ["+p.mode+"]";
        if(wide){
            int x=left+nameWidth+73,available=span-nameWidth-91,w=(available-8)/3;
            button(profile+" v",x,y,w,false,this::selectProfile).active=p!=null;
            button(method.label+" v",x+w+4,y,w,false,this::selectMethod);
            button(stat.label+" v",x+2*(w+4),y,w,false,this::selectStat);
        }else{
            y+=24;int w=(span-26)/2;
            button(profile+" v",left+9,y,w,false,this::selectProfile).active=p!=null;
            button(method.label+" v",left+13+w,y,w,false,this::selectMethod);
        }
        y+=25;int tw=(span-22-(TABS.length-1))/TABS.length;
        for(int i=0;i<TABS.length;i++){int selected=i;button(TABS[i],left+9+i*(tw+1),y,tw,tab==i,()->{tab=selected;scroll=0;refresh();});}
        y+=25;
        if(!wide){button(stat.label+" v",left+9,y,span-124,false,this::selectStat);button("Type Stats",left+span-110,y,101,false,this::typeStats).active=data.ready;y+=24;}
        if(tab==BREAKDOWN){breakdownControls(y+3);bodyTop=y+31;}
        else bodyTop=y+(!wide&&height<330?4:31);
        bodyBottom=bottom-48;
        button("<",left+9,bottom-30,24,false,()->scroll=Math.max(0,scroll-Math.max(30,bodyBottom-bodyTop-20)));
        button(">",left+37,bottom-30,24,false,()->scroll=Math.min(maxScroll(),scroll+Math.max(30,bodyBottom-bodyTop-20)));
        if(wide)button("Type Stats",left+span-111,bottom-30,102,false,this::typeStats).active=data.ready;
        data.initialize(minecraft::execute,this::refresh);
        data.ensureStats(method,minecraft::execute,this::refresh);
        lastRevision=data.revision;analyze();
    }
    @Override public void tick(){if(lastRevision!=data.revision)refresh();}
    void refresh(){if(minecraft.screen==this)init();}
    private void typeStats(){minecraft.setScreen(new InputsScreen(this,data.inputs(method),method));}
    private void breakdownControls(int y){
        Profile p=data.profile();var i=data.inputs(method);boolean live=p!=null&&!data.demo;
        int w=(span-18-12)/4,x=left+9;
        button("Tool: "+toolName(i.tool)+" v",x,y,w,false,this::selectTool).active=live;
        StatContext.Zone z=data.zone(method);
        button("Zone: "+(z==null?"Not chosen":z.label)+" v",x+w+4,y,w,false,this::selectZone).active=live;
        button("God Potion: "+(i.godPotion?"On":"Off"),x+2*(w+4),y,w,i.godPotion,()->{i.godPotion=!i.godPotion;persist();refresh();}).active=live;
        button(data.statsLoading(method)?"Loading...":"Refresh",x+3*(w+4),y,span-18-3*(w+4),false,()->{data.refreshStats();refresh();}).active=live&&!data.statsLoading(method);
    }
    /** Tool list of the last ledger for this profile; it does not change with the selections. */
    private List<StatsReport.Tool> tools(){Profile p=data.profile();return data.stats!=null&&p!=null&&data.stats.profileId.equals(p.id)?data.stats.tools:List.of();}
    private String toolName(String key){
        if(key==null||key.isEmpty())return "Choose held tool";
        return tools().stream().filter(t->t.key().equals(key)).map(StatsReport.Tool::name).findFirst().orElse(tools().isEmpty()?"Selected":"Not found");
    }
    private void persist(){data.save(minecraft::execute,ignored->{});}
    private void selectTool(){
        var list=tools();List<String> labels=new ArrayList<>();labels.add("Not chosen (tool stats unknown)");
        for(var t:list)labels.add(t.name()+" ("+t.location()+")");
        var i=data.inputs(method);
        minecraft.setScreen(new ChoiceScreen(this,"Held tool",labels,n->{i.tool=n==0?"":list.get(n-1).key();scroll=0;persist();}));
    }
    private void selectZone(){
        var zones=StatContext.Zone.values();List<String> labels=new ArrayList<>();labels.add("Usual for "+method.label+" ("+method.defaultZone().label+")");
        for(var z:zones)labels.add(z.label);labels.add("Not chosen (location bonuses not counted)");
        var i=data.inputs(method);
        minecraft.setScreen(new ChoiceScreen(this,"Mining zone",labels,n->{i.zone=n==0?"":n>zones.length?"-":zones[n-1].name();scroll=0;persist();}));
    }
    private void selectMethod(){minecraft.setScreen(new ChoiceScreen(this,"Mining method",Arrays.stream(MiningMethod.values()).map(m->m.label).toList(),n->{method=MiningMethod.values()[n];scroll=0;}));}
    private void selectStat(){minecraft.setScreen(new ChoiceScreen(this,"Stat focus",Arrays.stream(Stat.values()).map(s->s.label).toList(),n->{stat=Stat.values()[n];scroll=0;}));}
    private void selectProfile(){if(data.report!=null)minecraft.setScreen(new ChoiceScreen(this,"SkyBlock profile",data.report.profiles.stream().map(p->p.name+" ["+p.mode+"]"+(p.selected?" - active":"")).toList(),n->{data.profileIndex=n;scroll=0;}));}
    private void analyze(){
        int generation=++analysisGeneration;if(analysis!=null)analysis.cancel(true);
        Profile p=data.profile();overview=List.of();gear=List.of();upgrades=List.of();
        if(p==null||!data.ready||!p.inventoryEnabled||tab>=BREAKDOWN){calculating=false;buildCards();return;}
        calculating=true;buildCards();
        var selectedMethod=method;var selectedStat=stat;int selectedTab=tab;
        var effective=data.effective(method);var quotes=new HashMap<>(data.prices);
        analysis=ClientData.WORK.submit(()->{
            try {
                var base=data.engine.overview(p,selectedMethod,selectedStat);
                var selectedGear=new ArrayList<>(data.engine.bestOwned(p,selectedMethod,selectedStat).values());
                var ranked=selectedTab==1||selectedTab==2?data.engine.upgrades(p,selectedMethod,selectedTab==2?selectedStat:null,effective,quotes):List.<Upgrade>of();
                minecraft.execute(()->{if(generation!=analysisGeneration||minecraft.screen!=this)return;overview=base;gear=selectedGear;upgrades=ranked;calculating=false;buildCards();});
            }catch(Exception e){minecraft.execute(()->{if(generation!=analysisGeneration||minecraft.screen!=this)return;calculating=false;cards.clear();card("Unable to calculate",RED,0).line("Try loading the profile again. No upgrades were assumed.",TEXT);layoutCards();});}
        });
    }
    private Card card(String title,int accent,int column){Card c=new Card(title,accent,column);cards.add(c);return c;}
    private void buildCards(){
        cards.clear();Profile p=data.profile();
        if(!data.ready){card("Preparing SkyProgression",GOLD,0).line(data.message,MUTED);layoutCards();return;}
        if(p==null){
            card(data.loading?"Loading your profile...":"Your next mining upgrade",GOLD,0).line("Choose a method and see what to improve next.",TEXT).line("Load your username, or select Demo to explore offline.",CYAN);
            card("Connect your profile",CYAN,1).line("Start the local companion, then press Load. Your application key stays in the companion.",MUTED).line("Enable Inventory API in SkyBlock Settings for gear recommendations.",TEXT);
        }else if(!p.inventoryEnabled&&tab<BREAKDOWN){card("Inventory unavailable",RED,0).line("Gear recommendations are paused. Enable Inventory API in SkyBlock Settings, then reload after the profile cache expires.",TEXT);}
        else if(calculating){card("Calculating upgrades...",CYAN,0).line("You can switch tabs or close this screen while calculations run.",MUTED);}
        else switch(tab){case 0->overviewCards(p);case 1,2->upgradeCards();case BREAKDOWN->breakdownCards();case RATES->rateCards();case OTHER->otherCards(p);}
        layoutCards();
    }
    private ItemStack icon(String group){
        return new ItemStack(switch(group){
            case "Tool" -> Items.DIAMOND_PICKAXE;
            case "Helmet" -> Items.DIAMOND_HELMET;
            case "Chestplate" -> Items.DIAMOND_CHESTPLATE;
            case "Leggings" -> Items.DIAMOND_LEGGINGS;
            case "Boots" -> Items.DIAMOND_BOOTS;
            case "Lantern" -> Items.LANTERN;
            default -> Items.AMETHYST_SHARD;
        });
    }
    private void overviewCards(Profile p){
        Card basic=card("Heart of the Mountain & skills",GOLD,0);
        if(p.milestones.isEmpty())basic.line("Skill progress is not available for this profile.",MUTED);
        for(var m:p.milestones)basic.meter(m.name()+"  "+fmt(m.current())+" / "+fmt(m.maximum()),m.current(),m.maximum());
        Card powder=card("Powder totals",CYAN,0);
        boolean hasPowder=false;
        for(var e:p.progress.entrySet())if(e.getKey().contains("powder")){powder.line(e.getKey(),MUTED).line(fmt(e.getValue()),CYAN);hasPowder=true;}
        if(!hasPowder)powder.line("Powder data unavailable.",MUTED);
        powder.line("Spent + available. Targets depend on your HOTM build.",MUTED);
        card("Progression scope",GOLD,0).line("Base-item bars compare the supported catalog. Modifier bars track supported enchants and gemstones.",TEXT).line("Pets, full HOTM stat math and reforge upgrades still need manual checks.",MUTED);
        for(var row:overview){
            Card c=card(row.group()+" | "+(row.current()==null?"Missing":row.current().name),GOLD,1);
            c.icon=icon(row.group());
            c.meter(stat.label+" (base)  "+fmt(row.value())+" / "+fmt(row.maximum()),row.value(),row.maximum());
            if(row.value()<row.maximum())c.line("Target: "+row.target().name,MUTED);
            if(row.current()!=null){
                Item current=row.current();
                c.line("Item "+(current.observed?"lore":"base")+": "+fmt(Model.stat(current.stats,stat))+" "+stat.label,CYAN);
                if(!current.location.isBlank())c.line("Found in "+current.location,MUTED);
                for(var m:GearUpgrades.progress(current,method,stat))c.meter(m.name()+"  "+fmt(m.current())+" / "+fmt(m.maximum()),m.current(),m.maximum());
            }
        }
        if(overview.isEmpty())card("Gear audit",GOLD,1).line("No supported gear for this stat. Choose another stat focus.",MUTED);
    }
    private void upgradeCards(){
        boolean focused=tab==2;
        if(!focused&&!data.hasStats(method)){card(data.statsLoading(method)?"Loading automatic stats...":"Enter your effective stats",GOLD,0).line("Automatic Mining Fortune/Speed come from your loaded profile. Type Stats adds or overrides values.",TEXT).line("The Stats tab works without these inputs and ranks coins per stat point.",CYAN);return;}
        card(focused?stat.label+" | cheapest points first":method.label+" | best value first",GOLD,0).line("Each recommendation compares to your current setup. Alternatives are not a combined shopping list.",MUTED);
        if(upgrades.isEmpty())card("No supported upgrades found",CYAN,0).line("This does not mean your account is fully maxed. Try another method or stat.",TEXT);
        int rank=0;
        for(var u:upgrades){
            Card c=card("#"+(++rank)+"  "+u.item().name,u.blocked().isEmpty()?GOLD:RED,0);
            c.icon=icon(u.current()!=null?u.current().group:u.item().group);
            if(u.current()!=null)c.line((u.item().modification?"On: ":"Replaces: ")+u.current().name,MUTED);
            StringJoiner changes=new StringJoiner(" / ");u.delta().forEach((s,v)->{if(v!=0)changes.add((v>0?"+":"")+fmt(v)+" "+s.label);});c.line(changes.toString(),CYAN);
            c.line(u.cost()==null?"Price unknown":coins(u.cost())+" | "+coins(u.costPerUnit())+(focused?" / point":" / 1%"),u.cost()==null?MUTED:GREEN);
            if(!focused)c.line("Estimated drops: +"+fmt(u.gain())+"%",GREEN);
            if(u.owned())c.line(u.item().modification?"Materials already owned":"Item already owned",GREEN);
            if(!u.blocked().isEmpty())c.line(u.blocked(),RED);
            var quote=data.prices.get(u.item().id);c.line(quote!=null?quote.source:u.item().source,MUTED);
        }
        Card comparison=card("Cost efficiency",CYAN,1);
        var known=upgrades.stream().filter(u->u.cost()!=null&&Double.isFinite(u.costPerUnit())&&u.blocked().isEmpty()).limit(4).toList();
        double max=known.stream().mapToDouble(Upgrade::costPerUnit).max().orElse(1);
        for(var u:known){comparison.line(u.item().name,TEXT);comparison.lines.add(new Line(coins(u.costPerUnit())+(focused?" / point":" / 1%"),CYAN,u.costPerUnit()/Math.max(1,max)));}
        comparison.line("Shorter bars cost less per gain. These are separate alternatives.",MUTED);
        card("Before you buy",GOLD,1).line("Owned materials are used first. Supported socket unlock and removal fees are included.",TEXT).line("No assumed resale credit or transfer of modifiers onto replacement gear.",MUTED).line("Speed thresholds, abilities and routes are not part of the drop-gain estimate.",MUTED);
    }
    private void rateCards(){
        var i=data.inputs(method);var stats=data.effective(method);Card c=card(method.label+" | rate scenario",GOLD,0);
        if(i.blocksPerSecond<=0){c.line("Enter effective stats and measured active blocks/sec in Type Stats.",TEXT).line("Default mining efficiency: "+fmt(method.defaultEfficiency())+"%",CYAN);return;}
        try{var r=ProgressionEngine.rate(method,stats,i.efficiency,i.blocksPerSecond,i.baseDrops,i.coinsPerItem,i.coleweightPerItem);
            c.line(fmt(i.efficiency)+"% mining uptime",MUTED).line(fmt(r.itemsPerHour())+" items / hour",CYAN).line(i.coinsPerItem>0?coins(r.coinsPerHour())+" / hour":"Enter sale price for coins/hour",GOLD).line(i.coleweightPerItem>0?fmt(r.coleweightPerHour())+" Coleweight / hour":"Enter item weight for Coleweight/hour",GREEN);
        }catch(IllegalArgumentException e){c.line(e.getMessage(),RED);}
        card("How to read this estimate",CYAN,1).line("Gems use rough-equivalent units. Match your sale price and item weight to those units.",TEXT).line("Spread assumes extra blocks are available. Fuel, taxes, route, abilities and shaft loot are excluded. Shafting estimates Glacite only.",MUTED);
    }
    /** Effective stats in the header; "auto" marks automatic totals, "?" a value that is neither known nor entered. */
    private String statSummary(){
        var e=data.effective(method);var manual=data.inputs(method).stats;
        if(e.isEmpty())return data.statsLoading(method)?"Automatic stats loading...":"Effective stats: not entered";
        StringJoiner out=new StringJoiner("  ");
        for(Stat s:List.of(Stat.MINING_SPEED,Stat.MINING_FORTUNE,Stat.PRISTINE)){Double v=e.get(s);
            out.add((s==Stat.MINING_SPEED?"SPD ":s==Stat.MINING_FORTUNE?"FTN ":"PRIST ")+(v==null?"?":compact(v)+(manual.containsKey(s)?"":" auto")));}
        return out.toString();
    }
    private void breakdownCards(){
        if(data.demo){card("Stat breakdown",GOLD,0).line("Demo has no API data. Load your player for the automatic Mining Fortune and Speed breakdown.",TEXT);return;}
        StatsReport r=data.stats(method);
        if(r==null){
            String error=data.statsError(method);
            if(!error.isEmpty())card("Stat breakdown unavailable",RED,0).line(error,TEXT).line("Press Refresh to retry. Manual values in Type Stats still work.",MUTED);
            else card("Calculating stat breakdown...",CYAN,0).line("Loading from the companion. You can keep using the other tabs.",MUTED);
            return;
        }
        var manual=data.inputs(method).stats;
        var ledgers=new ArrayList<>(r.stats);ledgers.sort(Comparator.comparing(l->l.stat!=stat));
        int column=0;
        for(var l:ledgers){
            int col=column++%2;
            Card head=card(l.stat.label+" | "+(l.complete?"":"at least ")+fmt(l.total),col==0?GOLD:CYAN,col);
            head.line("Flat "+fmt(l.flat)+(l.multiplier!=1?" x "+fmt(l.multiplier)+" from additive % buffs":""),TEXT);
            if(r.tool.isEmpty())head.line("Choose your held tool: the API does not report it.",RED);
            if(!l.complete)head.line("Some counted sources have unknown values, so this is a lower bound.",RED);
            if(l.bestOwned!=null)head.meter("Best owned loadout: "+fmt(l.bestOwned),l.total,Math.max(l.total,l.bestOwned));
            Double override=manual.get(l.stat);if(override!=null)head.line("Manual value in use instead: "+fmt(override)+" (clear it in Type Stats)",GOLD);
            for(Breakdown.State state:Breakdown.State.values()){
                var list=l.contributions.stream().filter(c->c.state()==state).toList();if(list.isEmpty())continue;
                int color=switch(state){case COUNTED->GREEN;case SITUATIONAL->GOLD;case MISSING->RED;case TEMPORARY->CYAN;case UNTRACKABLE->MUTED;};
                Card c=card(stateTitle(state)+" | "+l.stat.label,color,col);
                for(var x:list){
                    String value=x.value()==null?"?":(x.category().equals(Breakdown.ADDITIVE_PERCENT)?"+"+fmt(x.value())+"%":(x.value()>=0?"+":"")+fmt(x.value()));
                    String text=value+"  "+x.source()+" ("+x.category()+")"+(x.confidence()==Breakdown.Confidence.MANUAL&&x.value()!=null?" - assumed":"");
                    if(state==Breakdown.State.COUNTED&&x.value()!=null&&x.maximum()!=null&&x.maximum()>x.value())c.meter(text+" / "+fmt(x.maximum()),x.value(),x.maximum());
                    else c.line(text,x.value()==null&&state==Breakdown.State.COUNTED?RED:TEXT);
                    if(!x.condition().isEmpty())c.line("  "+x.condition(),MUTED);
                    if(state!=Breakdown.State.COUNTED||x.value()==null)c.line("  "+x.note(),MUTED);
                }
            }
            if(!l.notes.isEmpty()){Card n=card("Notes | "+l.stat.label,MUTED,col);l.notes.forEach(t->n.line(t,MUTED));}
        }
    }
    private static String stateTitle(Breakdown.State state){
        return switch(state){case COUNTED->"Counted";case SITUATIONAL->"Situational, not counted";case MISSING->"Missing";
            case TEMPORARY->"Temporary, not counted";case UNTRACKABLE->"Not in the API";};
    }
    private void otherCards(Profile p){
        Card details=card("Profile progression",GOLD,0);for(var e:p.progress.entrySet())details.line(e.getKey()+": "+fmt(e.getValue()),TEXT);
        Card notes=card("Profile notes",CYAN,1);for(String note:p.notes)notes.line(note,MUTED);
        card("Manual checks",GOLD,0).line("Check HOTM perks and powder, pet level/item, reforges, drill modules/fuel, collections, attributes and temporary buffs.",TEXT).line("Stored gear is not necessarily equipped or usable.",MUTED);
        card("Inventory API help",CYAN,1).line("SkyBlock Menu > Settings > API Settings > Inventory API: ON.",TEXT).line("Profiles cache for five minutes; prices for fifteen. Unknown prices never mean free.",MUTED);
    }
    private void layoutCards(){
        placed.clear();int gap=8,columns=wide?2:1,w=columns==2?(span-34)/2:span-20;
        int[] ys={0,0};
        for(Card c:cards){
            int col=columns==2?c.column:0;List<Line> lines=new ArrayList<>();
            for(Line line:c.lines){
                // Font wrapping only on content changes, never for every rendered frame.
                String rest=line.text;int guard=0;
                while(!rest.isEmpty()&&guard++<100){String part=font.plainSubstrByWidth(rest,Math.max(20,w-20));if(part.isEmpty())break;
                    if(part.length()<rest.length()&&part.lastIndexOf(' ')>0)part=part.substring(0,part.lastIndexOf(' '));
                    rest=rest.substring(part.length()).stripLeading();lines.add(new Line(part,line.color,rest.isEmpty()?line.progress:-1));}
            }
            int h=30+lines.stream().mapToInt(l->l.progress>=0?22:13).sum();
            placed.add(new Placed(c,left+10+col*(w+gap),ys[col],w,h,lines));ys[col]+=h+gap;
        }
        contentHeight=Math.max(ys[0],ys[1]);scroll=Math.clamp(scroll,0,maxScroll());
    }
    private int maxScroll(){return Math.max(0,contentHeight-Math.max(1,bodyBottom-bodyTop));}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(x>=left&&x<=left+span&&y>=bodyTop&&y<=bodyBottom){scroll=Math.clamp(scroll-(int)(vertical*32),0,maxScroll());return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(username!=null&&username.isFocused())return super.keyPressed(event);
        if(event.key()==82){if(!data.loading)data.load(username.getValue(),minecraft::execute,this::refresh);return true;}
        if(event.key()==290){tab=OTHER;scroll=0;refresh();return true;}
        if(event.key()==267){scroll=Math.min(maxScroll(),scroll+Math.max(30,bodyBottom-bodyTop-20));return true;}
        if(event.key()==266){scroll=Math.max(0,scroll-Math.max(30,bodyBottom-bodyTop-20));return true;}
        return super.keyPressed(event);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,0xD908090D);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        // Draw frame before widgets; do not request Minecraft's full-screen blur pass.
        panel(g,left,top,span,bottom-top,BACK);
        g.text(font,"SkyProgression",left+10,top+13,GOLD,true);
        if(wide)g.text(font,"Fabric 26.1.2",left+108,top+13,MUTED,false);
        Profile p=data.profile();String state=data.loading?"LOADING":data.demo?"DEMO":p==null?"OFFLINE":"PROFILE LOADED";
        if(wide)g.text(font,state,left+span-95-font.width(state),top+13,data.demo?GOLD:data.loading?CYAN:p==null?MUTED:GREEN,false);
        if(tab!=BREAKDOWN&&(wide||height>=330)){panel(g,left+9,bodyTop-30,span-18,24,PANEL);
        String summary=p==null?"Mining progression":p.player+" / "+p.name+" ["+p.mode+"]";
        g.text(font,fit(font,summary,wide?span/2:span-34),left+15,bodyTop-22,TEXT,false);
        if(wide)g.text(font,fit(font,statSummary(),span/2-22),left+span/2,bodyTop-22,CYAN,false);
        }
        if(bodyBottom>bodyTop){
            g.enableScissor(left+4,bodyTop,left+span-4,bodyBottom);
            for(Placed box:placed){int y=bodyTop+box.y-scroll;if(y+box.height<bodyTop||y>bodyBottom)continue;
                panel(g,box.x,y,box.width,box.height,PANEL);g.fill(box.x+1,y+1,box.x+box.width-1,y+21,RAISED);
                int inset=box.card.icon.isEmpty()?9:29;if(!box.card.icon.isEmpty())g.fakeItem(box.card.icon,box.x+7,y+3);
                g.text(font,fit(font,box.card.title,box.width-inset-9),box.x+inset,y+7,box.card.accent,false);
                int lineY=y+27;for(Line line:box.lines){if(lineY+22>=bodyTop&&lineY<bodyBottom){g.text(font,line.text,box.x+9,lineY,line.color,false);if(line.progress>=0)Theme.bar(g,box.x+9,lineY+11,box.width-18,line.progress,line.color);}lineY+=line.progress>=0?22:13;}
                if(mx>=box.x&&mx<box.x+box.width&&my>=y&&my<y+21&&my>=bodyTop&&my<bodyBottom&&font.width(box.card.title)>box.width-inset-9)g.setTooltipForNextFrame(font,Component.literal(box.card.title),mx,my);
            }
            g.disableScissor();
            if(maxScroll()>0){int track=bodyBottom-bodyTop,thumb=Math.max(12,(int)((double)track*track/contentHeight));g.fill(left+span-6,bodyTop,left+span-3,bodyBottom,RAISED);int sy=bodyTop+(int)((track-thumb)*(double)scroll/maxScroll());g.fill(left+span-6,sy,left+span-3,sy+thumb,GOLD);}
        }
        g.text(font,fit(font,data.message,span-20),left+10,bottom-42,data.demo?GOLD:MUTED,false);
        g.text(font,fit(font,"ESC Close  |  R Reload  |  F1 Help",span-(wide?190:83)),left+70,bottom-24,MUTED,false);
        if(my>=bottom-44&&my<bottom-32&&mx>=left&&mx<=left+span)g.setTooltipForNextFrame(font,Component.literal(data.message),mx,my);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void removed(){analysisGeneration++;if(analysis!=null)analysis.cancel(true);}
    @Override public boolean isPauseScreen(){return false;}
    static String fmt(double n){return String.format(Locale.US,"%,.2f",n).replaceAll("\\.?0+$","");}
    static String compact(double n){return n>=1_000_000?fmt(n/1_000_000)+"M":n>=1_000?fmt(n/1_000)+"K":fmt(n);}
    static String coins(double n){return compact(n)+" coins";}
}
