package io.github.tianiste.skyprogression.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.IntConsumer;

final class ChoiceScreen extends Screen {
    private final Screen parent;
    private final List<String> choices;
    private final IntConsumer selected;
    private int page;
    ChoiceScreen(Screen parent,String title,List<String> choices,IntConsumer selected){super(Component.literal(title));this.parent=parent;this.choices=choices;this.selected=selected;}
    @Override protected void init(){
        clearWidgets();int count=Math.max(1,(height-80)/24),start=page*count;
        for(int n=start;n<Math.min(choices.size(),start+count);n++) {
            int index=n;
            addRenderableWidget(Button.builder(Component.literal(choices.get(n)),b->{selected.accept(index);onClose();}).bounds(20,40+(n-start)*24,width-40,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);init();}).bounds(20,height-28,30,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(Math.max(0,(choices.size()-1)/count),page+1);init();}).bounds(54,height-28,30,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-80,height-28,60,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){super.extractRenderState(g,x,y,delta);g.text(font,title,20,16,0xFFFFCE66,true);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
