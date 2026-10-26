package io.github.tianiste.skyprogression.client;

import io.github.tianiste.skyprogression.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

final class InputsScreen extends Screen {
    private final ProgressionScreen parent;
    private final ClientData.Inputs target;
    private final MiningMethod method;
    private final String[] values=new String[15];
    private final String[] labels=new String[15];
    private int page;
    private String error="";
    InputsScreen(ProgressionScreen parent,ClientData.Inputs target,MiningMethod method){
        super(Component.literal("Type Stats - "+method.label));this.parent=parent;this.target=target;this.method=method;
        for(int n=0;n<10;n++){labels[n]=Stat.values()[n].label;values[n]=Double.toString(target.stats.getOrDefault(Stat.values()[n],0.0));}
        String[] extra={"Efficiency %","Active blocks / sec","Base drops / block","Coins / item","Coleweight / item"};
        double[] nums={target.efficiency,target.blocksPerSecond,target.baseDrops,target.coinsPerItem,target.coleweightPerItem};
        for(int n=0;n<5;n++){labels[n+10]=extra[n];values[n+10]=Double.toString(nums[n]);}
    }
    private int count(){return Math.max(1,(height-105)/32);}
    @Override protected void init(){
        clearWidgets();int count=count(),start=page*count;
        for(int n=start;n<Math.min(15,start+count);n++) {
            int index=n;var box=new EditBox(font,width/2,44+(n-start)*32,width/2-20,20,Component.literal(labels[n]));
            box.setMaxLength(16);box.setValue(values[n]);box.setResponder(v->values[index]=v);addRenderableWidget(box);
        }
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);init();}).bounds(20,height-30,30,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(14/count,page+1);init();}).bounds(54,height-30,30,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"),b->save()).bounds(width-150,height-30,60,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width-84,height-30,64,20).build());
    }
    private void save(){
        try {
            double[] numbers=new double[15];
            for(int n=0;n<15;n++){numbers[n]=Double.parseDouble(values[n]);if(!Double.isFinite(numbers[n])||numbers[n]<0)throw new IllegalArgumentException(labels[n]+" must be non-negative");}
            if(numbers[10]>100)throw new IllegalArgumentException("Efficiency must be 0 to 100");
            Map<Stat,Double> stats=new EnumMap<>(Stat.class);for(int n=0;n<10;n++)stats.put(Stat.values()[n],numbers[n]);
            ProgressionEngine.rate(method,stats,numbers[10],numbers[11],numbers[12],numbers[13],numbers[14]);
            target.stats=stats;target.efficiency=numbers[10];target.blocksPerSecond=numbers[11];target.baseDrops=numbers[12];target.coinsPerItem=numbers[13];target.coleweightPerItem=numbers[14];target.confirmed=true;
            ClientData.INSTANCE.save();onClose();
        }catch(NumberFormatException e){error="Enter numbers in every field (use . for decimals).";}
        catch(IllegalArgumentException e){error=e.getMessage();}
        catch(Exception e){error="Could not save inputs to the config folder.";}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        super.extractRenderState(g,x,y,delta);g.text(font,title,20,12,0xFFFFCE66,true);
        g.text(font,"Effective stats from your in-game menu",20,28,0xFF9AAABD,false);
        int start=page*count();for(int n=start;n<Math.min(15,start+count());n++)g.text(font,font.plainSubstrByWidth(labels[n],width/2-26),20,50+(n-start)*32,0xFFE5ECF4,false);
        g.text(font,font.plainSubstrByWidth(error.isEmpty()?"Saved for this profile and method. Page "+(page+1):error,width-40),20,height-48,0xFFFFCE66,false);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
