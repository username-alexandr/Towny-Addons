package cc.crafto.ui.client;

import com.google.gson.JsonObject;
import cc.crafto.ui.OrderRarityStyle;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** One theme-aware description layout. Labels and values have explicit columns, never tabs. */
final class ItemInfoCard {
    record Line(String text,int x,int y,String tone,Integer rarity){}
    record Layout(List<String> title,List<Line> lines,int header,int content,int width,boolean icon) {
        int height(){return header+content+10;}
        int overflow(int height){return Math.max(0,content-Math.max(0,height-header-10));}
    }
    record Bounds(int x,int y,int width,int height){}
    static String clean(String text){return text.replaceAll("(?i)§[0-9a-fk-orx]","").replace('\t',' ').replace('\u00a0',' ').replaceAll(" +"," ").strip();}
    static Layout layout(String title,List<String> lore,String reason,int width,boolean icon){
        int titleWidth=Math.max(24,width-(icon?46:24));var titles=CivicStyle.wrap(clean(title),titleWidth);
        titles=new ArrayList<>(titles.subList(0,Math.min(2,titles.size())));
        int header=Math.max(icon?34:26,12+titles.size()*12+7),y=0;
        var rows=new ArrayList<Line>();var source=new ArrayList<String>();
        if(!reason.isBlank()){source.add("Пока недоступно: "+reason);source.add("");}
        source.addAll(lore);String paragraph="";
        for(int index=0;index<Math.min(192,source.size());index++){
            String raw=source.get(index);for(String part:raw.split("\\R",-1)){
                String text=clean(part);if(text.length()>4096)text=text.substring(0,4096);
                if(text.equals(clean(title)))continue;
                int colon=text.indexOf(':');boolean fact=colon>0&&colon<=32&&!text.startsWith("/");
                boolean instruction=text.matches("(?iu)^[▶►→➜]?\\s*(нажмите|лкм|пкм|shift|колесо|выберите|подтвердите).*"),blank=text.isBlank();
                if(!paragraph.isBlank()&&(fact||instruction||blank||paragraph.matches(".*[.!?]$"))){
                    for(String line:CivicStyle.wrap(paragraph,width-24)){rows.add(new Line(line,12,y,"muted",null));y+=12;}y+=4;paragraph="";
                }
                if(blank){y+=3;continue;}
                if(!fact&&!instruction){paragraph+=(paragraph.isEmpty()?"":" ")+text;continue;}
                if(instruction){
                    if(y>0)y+=3;
                    for(String line:CivicStyle.wrap(text.replaceFirst("^[▶►→➜]\\s*",""),width-24)){rows.add(new Line(line,12,y,"accent",null));y+=12;}
                    y+=4;continue;
                }
                String label=text.substring(0,colon).strip(),value=text.substring(colon+1).strip();
                int column=Math.clamp((width-24)*2/5,76,114),valueX=12+column+8,valueWidth=width-valueX-12;
                var labels=CivicStyle.wrap(label+":",column);var values=CivicStyle.wrap(value,Math.max(24,valueWidth));
                String tone=label.equalsIgnoreCase("Пока недоступно")?"warning":label.toLowerCase(Locale.ROOT).contains("наград")?"accent":"text";
                var rarity=OrderRarityStyle.lore(text);Integer rarityColor=rarity==null?null:rarity.color;
                for(int i=0;i<labels.size();i++)rows.add(new Line(labels.get(i),12,y+i*12,"muted",null));
                for(int i=0;i<values.size();i++)rows.add(new Line(values.get(i),valueX,y+i*12,tone,rarityColor));
                y+=Math.max(labels.size(),values.size())*12+4;
            }
        }
        if(!paragraph.isBlank()){for(String line:CivicStyle.wrap(paragraph,width-24)){rows.add(new Line(line,12,y,"muted",null));y+=12;}y+=2;}
        return new Layout(List.copyOf(titles),List.copyOf(rows),header,y,width,icon);
    }
    static int draw(GuiGraphicsExtractor g,Layout layout,ItemStack icon,JsonObject theme,int x,int y,int height,int scroll,boolean truncated){
        int background=UiTheme.color(theme,"panel"),accent=CivicStyle.inkAccent(theme),width=layout.width();
        CivicStyle.panel(g,x,y,width,height,background);g.outline(x,y,width,height,CivicStyle.mix(background,UiTheme.color(theme,"border"),40));
        g.fill(x+9,y+8,x+11,y+layout.header()-8,accent);g.nextStratum();
        int titleX=x+(layout.icon()?36:18);if(layout.icon())g.item(icon,x+14,y+10);
        for(int i=0;i<layout.title().size();i++)g.text(Minecraft.getInstance().font,layout.title().get(i),titleX,y+11+i*12,UiTheme.color(theme,"text"),false);
        int footer=truncated?18:0,available=Math.max(0,height-layout.header()-10-footer),overflow=Math.max(0,layout.content()-available);scroll=Math.clamp(scroll,0,overflow);
        g.fill(x+12,y+layout.header()-4,x+width-12,y+layout.header()-3,CivicStyle.mix(background,UiTheme.color(theme,"border"),35));
        if(available>0){
            g.enableScissor(x+8,y+layout.header(),x+width-8,y+layout.header()+available);
            for(var line:layout.lines()){
                int ly=y+layout.header()+line.y()-scroll;if(ly+9<y+layout.header()||ly>=y+layout.header()+available)continue;
                int color=line.rarity()!=null?line.rarity():line.tone().equals("warning")?CivicStyle.financeInk(theme,"warning"):UiTheme.color(theme,line.tone());
                g.text(Minecraft.getInstance().font,line.text(),x+line.x(),ly,color,false);
            }
            g.disableScissor();
        }
        if(truncated)CivicStyle.text(g,"Полностью: Ещё → Описание",x+12,y+height-16,width-24,UiTheme.color(theme,"muted"));
        else if(overflow>0&&available>0){int thumb=Math.min(available,Math.max(6,available*available/(available+overflow))),at=(available-thumb)*scroll/overflow;
            g.fill(x+width-6,y+layout.header(),x+width-4,y+layout.header()+available,CivicStyle.mix(background,UiTheme.color(theme,"border"),30));g.fill(x+width-6,y+layout.header()+at,x+width-4,y+layout.header()+at+thumb,accent);}
        return scroll;
    }
    static Bounds tooltip(GuiGraphicsExtractor g,ItemStack item,String title,List<String> lore,String reason,JsonObject theme,int mouseX,int mouseY,int screenWidth,int screenHeight){
        int width=Math.max(100,Math.min(330,screenWidth-16));var layout=layout(title,lore,reason,width,!item.isEmpty());
        int height=Math.min(layout.height(),Math.max(52,screenHeight-16)),x=Math.clamp(mouseX+14,8,Math.max(8,screenWidth-width-8)),y=Math.clamp(mouseY+12,8,Math.max(8,screenHeight-height-8));
        if(mouseX+14+width>screenWidth-8&&mouseX-width-12>=8)x=mouseX-width-12;
        g.nextStratum();draw(g,layout,item,theme,x,y,height,0,layout.height()>height);return new Bounds(x,y,width,height);
    }
}
