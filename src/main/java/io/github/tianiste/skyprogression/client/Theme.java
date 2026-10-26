package io.github.tianiste.skyprogression.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Stitch palette, drawn natively: no web renderer, remote fonts, or background blur. */
final class Theme {
    static final int BACK=0xFF0D0D13, PANEL=0xFF1B1B22, RAISED=0xFF26262F, EDGE=0xFF393943;
    static final int TEXT=0xFFDEDEE6, MUTED=0xFFACA59A, GOLD=0xFFFFC665, CYAN=0xFF49F6F6, GREEN=0xFF40ED45, RED=0xFFFFABA2;
    static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) {
        g.fill(x,y,x+w,y+h,color);g.outline(x,y,w,h,EDGE);
    }
    static void bar(GuiGraphicsExtractor g,int x,int y,int w,double progress,int color) {
        g.fill(x,y,x+w,y+5,BACK);int filled=(int)(Math.clamp(progress,0,1)*w);
        if(filled>0){g.fill(x,y,x+filled,y+5,color);g.fill(x,y,x+filled,y+1,0x50FFFFFF);}
    }
    static String fit(Font font,String value,int width) {
        if(width<=0)return "";
        return font.width(value)<=width?value:font.plainSubstrByWidth(value,Math.max(0,width-font.width("...")))+"...";
    }
    static final class Action extends Button {
        private final boolean selected;
        private final int accent;
        Action(String label,int x,int y,int w,int h,boolean selected,int accent,Runnable action) {
            super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.selected=selected;this.accent=accent;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
            var font=Minecraft.getInstance().font;
            panel(g,getX(),getY(),width,height,isHoveredOrFocused()?RAISED:PANEL);
            if(selected)g.fill(getX()+1,getY(),getRight()-1,getY()+2,accent);
            if(isFocused())g.outline(getX(),getY(),width,height,CYAN);
            String label=fit(font,getMessage().getString(),width-8);
            g.text(font,label,getX()+(width-font.width(label))/2,getY()+(height-8)/2,!active?0xFF686872:selected||isHoveredOrFocused()?accent:TEXT,false);
        }
    }
}
