package io.github.tianiste.skyprogression.client;

import io.github.tianiste.skyprogression.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.tianiste.skyprogression.client.Theme.*;

final class InputsScreen extends Screen {
    private final ProgressionScreen parent;
    private final ClientData.Inputs target;
    private final MiningMethod method;
    private final String[] values=new String[15],labels=new String[15];
    private int page,left,top,span,end,columns,perPage,fieldWidth;
    private boolean saving;
    private String error="";
    private final List<EditBox> boxes=new ArrayList<>();
    InputsScreen(ProgressionScreen parent,ClientData.Inputs target,MiningMethod method){
        super(Component.literal("Type Stats | "+method.label));this.parent=parent;this.target=target;this.method=method;
        for(int n=0;n<10;n++){labels[n]=Stat.values()[n].label;Double v=target.stats.get(Stat.values()[n]);values[n]=v==null?"":Double.toString(v);}
        String[] extra={"Efficiency %","Active blocks / sec","Base drops / block","Coins / item","Coleweight / item"};
        double[] nums={target.efficiency,target.blocksPerSecond,target.baseDrops,target.coinsPerItem,target.coleweightPerItem};
        for(int n=0;n<5;n++){labels[n+10]=extra[n];values[n+10]=Double.toString(nums[n]);}
    }
    @Override protected void init(){
        clearWidgets();boxes.clear();span=Math.min(620,width-24);left=(width-span)/2;top=Math.max(10,(height-490)/2);end=height-top;
        columns=span>=420?2:1;perPage=Math.max(1,(end-top-113)/45)*columns;fieldWidth=(span-32-(columns-1)*12)/columns;
        page=Math.clamp(page,0,14/perPage);int start=page*perPage;
        for(int n=start;n<Math.min(15,start+perPage);n++){
            int index=n,slot=n-start,x=left+16+(slot%columns)*(fieldWidth+12),y=top+74+(slot/columns)*45;
            var box=new EditBox(font,x,y,fieldWidth,20,Component.literal(labels[n]));box.setMaxLength(16);box.setValue(values[n]);box.setTextColor(n==0?CYAN:GOLD);box.setResponder(v->values[index]=v);
            if(n<10)box.setHint(Component.literal(StatBreakdown.SUPPORTED.contains(Stat.values()[n])?"Automatic":"Not entered"));box.active=!saving;boxes.add(box);addRenderableWidget(box);
        }
        button("<",left+12,end-31,26,()->{page=Math.max(0,page-1);init();});button(">",left+42,end-31,26,()->{page=Math.min(14/perPage,page+1);init();});
        button(saving?"Saving...":"Apply",left+span-150,end-31,64,this::save).active=!saving;
        button("Cancel",left+span-80,end-31,68,this::onClose);
    }
    private Theme.Action button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(new Theme.Action(label,x,y,w,20,false,GOLD,action));}
    private void save(){
        if(saving)return;
        try{
            double[] numbers=new double[15];for(int n=0;n<15;n++){if(n<10&&values[n].isBlank()){numbers[n]=-1;continue;}numbers[n]=Double.parseDouble(values[n].replace(",",""));if(!Double.isFinite(numbers[n])||numbers[n]<0)throw new IllegalArgumentException(labels[n]+" must be non-negative");}
            if(numbers[10]>100)throw new IllegalArgumentException("Efficiency must be 0 to 100");
            // Blank stats stay absent (automatic or unknown), never zero.
            Map<Stat,Double> stats=new EnumMap<>(Stat.class);for(int n=0;n<10;n++)if(numbers[n]>=0)stats.put(Stat.values()[n],numbers[n]);
            ProgressionEngine.rate(method,stats,numbers[10],numbers[11],numbers[12],numbers[13],numbers[14]);
            target.stats=stats;target.efficiency=numbers[10];target.blocksPerSecond=numbers[11];target.baseDrops=numbers[12];target.coinsPerItem=numbers[13];target.coleweightPerItem=numbers[14];target.confirmed=true;
            saving=true;error="Saving your inputs...";init();
            ClientData.INSTANCE.save(minecraft::execute,success->{saving=false;if(success){if(minecraft.screen==this)onClose();}else{error="Applied for this session, but could not save to disk.";if(minecraft.screen==this)init();}});
        }catch(NumberFormatException e){error="Enter numbers in every field (use . for decimals).";}
        catch(IllegalArgumentException e){error=e.getMessage();}
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,0xE508090D);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        panel(g,left,top,span,end-top,BACK);g.fill(left+1,top+1,left+span-1,top+28,RAISED);g.text(font,fit(font,title.getString(),span-24),left+12,top+10,GOLD,true);
        g.text(font,fit(font,"Enter stats from your in-game menu. Leave a stat blank for its automatic total.",span-24),left+12,top+39,MUTED,false);
        int start=page*perPage;for(int n=start;n<Math.min(15,start+perPage);n++){int slot=n-start;g.text(font,fit(font,labels[n],fieldWidth),left+16+(slot%columns)*(fieldWidth+12),top+61+(slot/columns)*45,n==0?CYAN:GOLD,false);}
        String status=error.isEmpty()?"Profile + method inputs | Page "+(page+1)+" / "+(14/perPage+1):error;
        g.text(font,fit(font,status,span-24),left+12,end-46,error.isEmpty()?MUTED:RED,false);
        super.extractRenderState(g,x,y,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
