package io.github.tianiste.skyprogression.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.IntConsumer;
import static io.github.tianiste.skyprogression.client.Theme.*;

final class ChoiceScreen extends Screen {
    private final Screen parent;
    private final List<String> choices;
    private final IntConsumer selected;
    private int page,left,top,span,end,count;
    private String query="";
    private List<Integer> filtered=List.of();
    ChoiceScreen(Screen parent,String title,List<String> choices,IntConsumer selected){super(Component.literal(title));this.parent=parent;this.choices=List.copyOf(choices);this.selected=selected;}
    @Override protected void init(){
        clearWidgets();span=Math.min(440,width-24);left=(width-span)/2;top=Math.max(12,(height-460)/2);end=height-top;
        count=Math.max(1,(end-top-107)/25);
        var search=new EditBox(font,left+12,top+33,span-24,20,Component.literal("Filter choices"));search.setMaxLength(40);search.setValue(query);search.setHint(Component.literal("Search..."));search.setTextColor(CYAN);
        search.setResponder(v->{query=v;page=0;rebuildChoices();});addRenderableWidget(search);
        rebuildChoices();
    }
    private final List<Theme.Action> optionButtons=new ArrayList<>();
    private void rebuildChoices(){
        for(var b:optionButtons)removeWidget(b);optionButtons.clear();
        List<Integer> found=new ArrayList<>();for(int i=0;i<choices.size();i++)if(choices.get(i).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))found.add(i);filtered=found;
        page=Math.clamp(page,0,Math.max(0,(filtered.size()-1)/count));int start=page*count;
        for(int n=start;n<Math.min(filtered.size(),start+count);n++){int index=filtered.get(n);add(choices.get(index),left+12,top+64+(n-start)*25,span-24,()->{selected.accept(index);onClose();});}
        add("<",left+12,end-31,28,()->{page--;rebuildChoices();});add(">",left+44,end-31,28,()->{page++;rebuildChoices();});add("Back",left+span-76,end-31,64,this::onClose);
    }
    private void add(String label,int x,int y,int w,Runnable action){var b=new Theme.Action(label,x,y,w,20,false,CYAN,action);b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label)));optionButtons.add(b);addRenderableWidget(b);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,0xE508090D);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        panel(g,left,top,span,end-top,BACK);g.fill(left+1,top+1,left+span-1,top+27,RAISED);g.text(font,title,left+12,top+10,GOLD,true);
        if(filtered.isEmpty())g.text(font,"No matching choices",left+12,top+66,MUTED,false);
        g.text(font,(page+1)+" / "+Math.max(1,(filtered.size()+count-1)/count),left+84,end-25,MUTED,false);super.extractRenderState(g,x,y,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
