package cc.crafto.ui.client;

import cc.crafto.ui.ContractHudState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Display-only right-centre overlay. No blur and no input capture. */
final class ContractHud {
    private final CraftoUIClient client;
    private List<ContractHudState.Entry> entries=List.of();
    private long received;
    private int page;
    ContractHud(CraftoUIClient client){this.client=client;}
    void clear(){entries=List.of();received=0;page=0;}
    void update(ContractHudState state){
        if(!entries.stream().map(ContractHudState.Entry::id).toList().equals(state.entries().stream().map(ContractHudState.Entry::id).toList()))page=0;
        for(var entry:state.entries())if(entry.status()==ContractHudState.Status.READY&&client.preferences().announce(entry.id())){
            if(client.preferences().notifications)client.notifications.add(entry.inventory()?"Ресурсы собраны":"Контракт выполнен",entry.title()+" — можно сдать и получить награду.");client.settings.save();
        }
        entries=state.entries();received=System.nanoTime();
    }
    List<ContractHudState.Entry> entries(){return entries;}
    void next(){page++;}
    record Bounds(float x,float y,float w,float h,float scale,int visible,int row,boolean targets){}
    Bounds bounds(int width,int height,List<ContractHudState.Entry> selected){
        var prefs=client.preferences();
        float scale=Math.min(prefs.scale,Math.max(.35f,(width-24)/188f));
        boolean targets=selected.stream().anyMatch(e->!e.targetKey().isEmpty());int row=targets?78:55;
        int visible=Math.min(Math.clamp((int)((height-60)/scale-40)/row,1,prefs.rows),Math.max(1,selected.size()));
        int pages=(selected.size()+visible-1)/visible;page=Math.floorMod(page,Math.max(1,pages));
        int count=Math.max(1,Math.min(visible,selected.size()-page*visible));
        float w=188*scale,h=(24+count*row+(pages>1?16:4))*scale;
        if(h>height-16){scale*=Math.max(.1f,(height-16)/h);w=188*scale;h=(24+count*row+(pages>1?16:4))*scale;}
        return new Bounds(8+Math.max(0,width-16-w)*prefs.x,8+Math.max(0,height-16-h)*prefs.y,w,h,scale,visible,row,targets);
    }
    void preview(GuiGraphicsExtractor g){render(g,true);}
    void extract(GuiGraphicsExtractor g){render(g,false);}
    private String clip(String text,int width){var font=Minecraft.getInstance().font;return font.width(text)<=width?text:font.plainSubstrByWidth(text,Math.max(0,width-font.width("…")))+"…";}
    private void render(GuiGraphicsExtractor g,boolean preview){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||(!preview&&(!client.settings.contractsHud||mc.gui.screen()!=null||System.nanoTime()-received>TimeUnit.SECONDS.toNanos(20))))return;
        List<ContractHudState.Entry> entries=client.preferences().selected(this.entries);
        if(entries.isEmpty()){
            if(preview){var b=bounds(g.guiWidth(),g.guiHeight(),entries);g.fill((int)b.x(),(int)b.y(),(int)(b.x()+b.w()),(int)(b.y()+b.h()),0xDD18202C);g.text(mc.font,"Закрепите контракт",(int)b.x()+8,(int)b.y()+16,0xFFFFFFFF,false);}return;
        }
        Bounds b=bounds(g.guiWidth(),g.guiHeight(),entries);float scale=b.scale(),x=b.x(),y=b.y();
        boolean targets=b.targets();int w=188,row=b.row(),visible=b.visible(),pages=(entries.size()+visible-1)/visible;
        int start=page*visible,count=Math.min(visible,entries.size()-start),h=Math.round(b.h()/scale);
        var font=mc.font;
        g.pose().pushMatrix();
        try{
            g.pose().translate(x,y);g.pose().scale(scale,scale);
            g.fill(0,0,w,h,0xD0181F29);g.outline(0,0,w,h,0x90616E80);
            g.text(font,"Контракты",10,8,0xFFF0F4FA,false);
            g.fill(10,21,w-10,22,0x80616E80);
            for(int i=0;i<count;i++){
                var entry=entries.get(start+i);int cy=25+i*row;
                int accent=switch(entry.status()){case READY->0xFF54D797;case ACTIVE->0xFF80C6E8;default->0xFFE5C281;};
                g.fill(5,cy+2,7,cy+row-5,accent);
                var lines=font.split(Component.literal(entry.title()),w-32);
                for(int line=0;line<Math.min(2,lines.size());line++){
                    if(line==1&&lines.size()>2)g.text(font,"…",w-20,cy+11,0xFFEFF2F7,false);
                    g.text(font,lines.get(line),14,cy+line*11,0xFFEFF2F7,false);
                }
                int progressY=targets?49:25,barY=targets?64:40;
                if(targets&&!entry.targetKey().isEmpty()){
                    String prefix=switch(entry.kind()){case "DELIVERY"->"Сдать: ";case "MINING"->"Добыть: ";case "HUNTING"->"Победить: ";case "FISHING"->"Поймать: ";default->"Цель: ";};
                    var targetLines=font.split(Component.literal(prefix).append(Component.translatable(entry.targetKey())),w-32);
                    for(int line=0;line<Math.min(2,targetLines.size());line++)g.text(font,targetLines.get(line),14,cy+24+line*11,0xFFB7C3D3,false);
                    if(targetLines.size()>2)g.text(font,"…",w-20,cy+35,0xFFB7C3D3,false);
                }
                String label=switch(entry.status()){
                    case ACTIVE->(entry.inventory()?"Собрано: ":"Прогресс: ")+entry.progress()+" / "+entry.goal();
                    case READY->"Готово к сдаче";
                    case PENDING->"Награда ожидается";
                    case HELD->"Сдача проверяется";
                    case REVIEW->"Проверка награды";
                    case BLOCKED->"Награда отложена";
                };
                g.text(font,clip(label,w-26),14,cy+progressY,accent,false);
                g.fill(14,cy+barY,w-12,cy+barY+4,0xFF3A4555);
                int fill=(int)Math.round((w-26)*(entry.progress()/(double)entry.goal()));
                if(fill>0)g.fill(14,cy+barY,14+fill,cy+barY+4,accent);
            }
            if(pages>1)g.text(font,clip((page+1)+" / "+pages+"  ·  "+client.hudNextKey()+" — дальше",w-20),10,h-13,0xFFA4B2C7,false);
        }finally{g.pose().popMatrix();}
    }
}
