package cc.crafto.ui.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static cc.crafto.ui.Protocol.*;

/** City-only cards. All colours come from the current theme; no item/rarity icons are replaced. */
final class CityCardButton extends Button {
    private final JsonObject data,theme;
    private final String mode;
    private final int clipTop,clipBottom;
    private final boolean selected;
    CityCardButton(JsonObject data,String mode,boolean selected,int x,int y,int w,int h,int top,int bottom,Runnable action,JsonObject theme){
        super(x,y,w,h,Component.literal(str(data,"title",str(data,"label",""))+". "+str(data,"value","")+" "+str(data,"detail",str(data,"hint",""))+" "+str(data,"reason","")),b->action.run(),DEFAULT_NARRATION);
        this.data=data;this.mode=mode;this.selected=selected;this.theme=theme;clipTop=top;clipBottom=bottom;
    }
    @Override public boolean isMouseOver(double x,double y){return y>=clipTop&&y<clipBottom&&super.isMouseOver(x,y);}
    private int color(String key){return UiTheme.color(theme,key);}
    private String clip(String text,int w){var f=Minecraft.getInstance().font;return f.width(text)<=w?text:f.plainSubstrByWidth(text,Math.max(0,w-8))+"…";}
    private void text(GuiGraphicsExtractor g,String text,int x,int y,int w,String color){g.text(Minecraft.getInstance().font,clip(text,w),x,y,this.color(color),false);}
    private void wrapped(GuiGraphicsExtractor g,String value,int x,int y,int w,int max,String color){
        var font=Minecraft.getInstance().font;var lines=font.split(Component.literal(value),w);
        for(int i=0;i<Math.min(max,lines.size());i++){
            if(i==max-1&&lines.size()>max){StringBuilder s=new StringBuilder();lines.get(i).accept((index,style,cp)->{s.appendCodePoint(cp);return true;});text(g,s+"…",x,y+i*11,w,color);}
            else g.text(font,lines.get(i),x,y+i*11,this.color(color),false);
        }
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();if(y>=clipBottom||y+h<=clipTop)return;
        boolean finance=mode.equals("finance"),metric=mode.equals("metric")||finance,over=isHoveredOrFocused()&&(!metric||active),blocked=!str(data,"reason","").isBlank();
        g.enableScissor(x,Math.max(y,clipTop),x+w,Math.min(y+h,clipBottom));g.nextStratum();
        g.fill(x,y,x+w,y+h,color(over?"hover":metric||mode.equals("info")?"panel":"button"));
        if(over||selected)g.outline(x,y,w,h,color("accent"));
        if(selected)g.fill(x+1,y+h-3,x+w-1,y+h-1,color("accent"));
        g.nextStratum();
        var presentation=data.has("presentation")?data.getAsJsonObject("presentation"):new JsonObject();
        if(metric){
            icon(g,str(data,"icon",""),x+9,y+5,color("muted"));text(g,str(data,"label",""),x+29,y+8,w-38,"muted");
            String value=str(data,"value","—");float scale=Minecraft.getInstance().font.width(value)*1.5<=w-20?1.5f:1f;
            g.pose().pushMatrix();g.pose().translate(x+10,y+24);g.pose().scale(scale,scale);CivicStyle.text(g,value,0,0,(int)((w-20)/scale),finance?CivicStyle.financeInk(theme,str(data,"tone","neutral")):color("text"));g.pose().popMatrix();
            if(finance)g.fill(x+1,y+2,x+3,y+h-2,CivicStyle.financeInk(theme,str(data,"tone","neutral")));
            text(g,str(data,"hint",""),x+10,y+39,w-20,"muted");
        }else if(mode.equals("shortcut")){
            icon(g,str(presentation,"icon",""),x+9,y+8,color(blocked?"muted":"accent"));text(g,str(data,"title",""),x+30,y+11,w-38,blocked?"muted":"text");
        }else if(mode.equals("tab")){
            var font=Minecraft.getInstance().font;g.centeredText(font,clip(str(data,"title",""),w-12),x+w/2,y+8,color(selected?"text":"muted"));
        }else if(mode.equals("info")){
            text(g,str(data,"title",""),x+12,y+14,w-24,"text");wrapped(g,str(data,"detail",""),x+12,y+32,w-24,3,"muted");
        }else{
            boolean known=bool(presentation,"known",false);int total=integer(presentation,"total",0),done=integer(presentation,"done",0);
            String status=str(presentation,"status",blocked?"Недоступно":"Действие"),category=str(presentation,"category","Требует внимания");
            String badge=known&&total>0?Math.clamp((int)(100L*done/total),0,100)+"%":status;
            int badgeWidth=Math.min(w/2,Minecraft.getInstance().font.width(badge));
            icon(g,str(presentation,"icon","goal"),x+9,y+5,color("accent"));text(g,category,x+29,y+8,w-44-badgeWidth,"muted");text(g,badge,x+w-10-badgeWidth,y+8,badgeWidth,"text");
            wrapped(g,str(data,"title",""),x+10,y+23,w-20,2,"text");
            String detail=blocked?str(data,"reason",""):str(data,"detail","");
            if(detail.isBlank()&&!known)detail=blocked?"Недоступно":status+" · подробнее ›";
            text(g,detail,x+10,y+46,w-20,"muted");
            if(known&&total>0){int bar=w-20;g.fill(x+10,y+h-9,x+w-10,y+h-6,color("background"));g.fill(x+10,y+h-9,x+10+(int)(bar*Math.clamp((double)done/total,0,1)),y+h-6,color("accent"));}
        }
        g.disableScissor();
    }
    /** Small original line pictograms in logical pixels; resource-pack item icons stay intact. */
    private static void icon(GuiGraphicsExtractor g,String id,int x,int y,int c){
        switch(id){
            case "planner"->CivicStyle.icon(g,id,x,y,c);
            case "treasury"->{g.outline(x+1,y+5,14,9,c);g.fill(x+4,y+2,x+12,y+4,c);g.fill(x+4,y+8,x+12,y+10,c);}
            case "income","expense"->{boolean up=id.equals("income");g.fill(x+7,y+2,x+9,y+14,c);for(int i=0;i<5;i++){int yy=up?y+2+i:y+13-i;g.fill(x+7-i,yy,x+9+i,yy+1,c);}}
            case "residents","inbox"->{g.outline(x+2,y+1,5,5,c);g.outline(x+9,y+3,4,4,c);g.outline(x,y+9,9,6,c);g.fill(x+11,y+10,x+15,y+15,c);}
            case "map","claim"->{g.outline(x+1,y+1,14,14,c);g.fill(x+7,y+2,x+8,y+14,c);g.fill(x+2,y+7,x+14,y+8,c);if(id.equals("claim"))g.fill(x+10,y+10,x+13,y+13,c);}
            case "build"->{g.outline(x+1,y+7,5,8,c);g.outline(x+7,y+2,7,13,c);g.fill(x+9,y+5,x+12,y+6,c);g.fill(x+9,y+9,x+12,y+10,c);}
            case "materials"->{g.outline(x+1,y+2,14,13,c);g.fill(x+1,y+6,x+15,y+7,c);g.fill(x+7,y+3,x+9,y+5,c);}
            case "history","orders","contract"->{g.outline(x+2,y+1,12,14,c);for(int i=0;i<3;i++)g.fill(x+5,y+4+i*3,x+11,y+5+i*3,c);}
            default->{g.fill(x+3,y+1,x+5,y+15,c);g.outline(x+5,y+2,10,7,c);}
        }
    }
}
