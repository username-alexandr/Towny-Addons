package cc.crafto.ui.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;

/** Information is visually passive; server-issued actions alone receive an action affordance. */
final class CivicCardButton extends Button {
    private final JsonObject theme;
    private final String title,detail,reason,icon,mode;
    private final boolean actionable,selected;
    private final int clipTop,clipBottom;
    private String badge="";private int progress,total;
    CivicCardButton badge(String text){badge=text;setMessage(Component.literal(title+". "+text+". "+detail));if(CivicStyle.wrap(detail,getWidth()-54).size()>(getHeight()>=60?2:1)||CivicStyle.wrap(title,getWidth()-60-Minecraft.getInstance().font.width(text)).size()>2)setTooltip(Tooltip.create(getMessage()));return this;}
    CivicCardButton progress(int done,int total){progress=done;this.total=total;return this;}
    CivicCardButton(String title,String detail,String reason,String icon,String mode,boolean actionable,boolean selected,
                    int x,int y,int w,int h,int top,int bottom,Runnable action,JsonObject theme){
        super(x,y,w,h,Component.literal(title+(detail.isBlank()?"":". "+detail)+(reason.isBlank()?"":". "+reason)),b->action.run(),DEFAULT_NARRATION);
        this.title=title;this.detail=detail;this.reason=reason;this.icon=icon;this.mode=mode;this.actionable=actionable;this.selected=selected;this.theme=theme;clipTop=top;clipBottom=bottom;
        if(mode.equals("tab")||mode.equals("icon-tab")||(mode.equals("row")||mode.equals("compact")||mode.equals("catalog")||mode.equals("setting")||mode.equals("contract"))&&(CivicStyle.wrap(title,w-60).size()>2||CivicStyle.wrap(detail,mode.equals("catalog")?(int)((w-54)/0.82f):w-54).size()>(mode.equals("compact")||mode.equals("catalog")||mode.equals("setting")?h>=60?2:1:2)||!reason.isBlank()))
            setTooltip(Tooltip.create(getMessage()));
    }
    private int color(String key){return UiTheme.color(theme,key);}
    @Override public boolean isMouseOver(double x,double y){return y>=clipTop&&y<clipBottom&&super.isMouseOver(x,y);}
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(alpha==0)return;
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();if(y>=clipBottom||y+h<=clipTop)return;
        boolean blocked=!reason.isBlank(),interactive=actionable&&!blocked,over=isHoveredOrFocused()&&active;
        int accent=color("accent"),text=color("text"),muted=color("muted"),panel=color("panel"),border=CivicStyle.mix(panel,color("border"),35);
        g.enableScissor(x,Math.max(y,clipTop),x+w,Math.min(y+h,clipBottom));g.nextStratum();
        if(mode.endsWith("tab")){
            if(selected||over)CivicStyle.panel(g,x,y,w,h,CivicStyle.mix(panel,selected?accent:color("hover"),selected?16:70));
            if(selected)g.fill(x+10,y+h-2,x+w-10,y+h,accent);
            g.nextStratum();
            if(mode.equals("icon-tab"))CivicStyle.icon(g,icon,x+(w-18)/2,y+(h-18)/2,selected?CivicStyle.inkAccent(theme):muted);
            else {CivicStyle.icon(g,icon,x+10,y+(h-18)/2,selected?CivicStyle.inkAccent(theme):muted);CivicStyle.text(g,title,x+34,y+(h-8)/2,w-40,selected?text:muted);}
        }else if(mode.equals("control")||mode.equals("primary")){
            CivicStyle.panel(g,x,y,w,h,mode.equals("primary")?CivicStyle.mix(panel,accent,active?30:9):over?color("hover"):color("button"));
            if(over)g.outline(x,y,w,h,accent);
            g.nextStratum();g.centeredText(Minecraft.getInstance().font,CivicStyle.clip(title,w-12),x+w/2,y+(h-8)/2,active?text:muted);
        }else if(mode.equals("compact")||mode.equals("catalog")||mode.equals("setting")){
            CivicStyle.panel(g,x,y,w,h,over&&active?CivicStyle.mix(panel,color("hover"),65):panel);
            if(over&&active)g.outline(x,y,w,h,accent);
            if(selected)g.fill(x,y+5,x+2,y+h-5,accent);
            g.nextStratum();CivicStyle.icon(g,icon,x+13,y+13,CivicStyle.inkAccent(theme));
            int badgeWidth=badge.isBlank()?0:Math.min(w/3,Minecraft.getInstance().font.width(badge)+12);
            CivicStyle.wrapped(g,title,x+42,y+(mode.equals("catalog")?7:10),w-52-badgeWidth,2,text);
            if(!badge.isBlank()){CivicStyle.panel(g,x+w-badgeWidth-8,y+9,badgeWidth,17,CivicStyle.mix(panel,accent,selected?20:8));CivicStyle.text(g,badge,x+w-badgeWidth-2,y+14,badgeWidth-10,selected?CivicStyle.inkAccent(theme):muted);}
            if(!detail.isBlank()){
                if(mode.equals("catalog")){
                    int titleLines=Math.min(2,CivicStyle.wrap(title,w-52-badgeWidth).size());
                    g.pose().pushMatrix();
                    g.pose().translate(x+42,y+22+(titleLines-1)*12);
                    g.pose().scale(0.82f,0.82f);
                    CivicStyle.wrapped(g,detail,0,0,(int)((w-54)/0.82f),2,muted);
                    g.pose().popMatrix();
                }else if(h>=60)CivicStyle.detailWrapped(g,detail,x+42,y+h-25,w-54,2,muted);
                else CivicStyle.text(g,detail,x+42,y+h-17,w-54,muted);
            }
        }else{
            boolean special=mode.equals("tree-special");int mark=special?0xFFE1B96B:accent;
            CivicStyle.panel(g,x,y,w,h,over&&interactive?CivicStyle.mix(panel,color("hover"),65):special?CivicStyle.mix(panel,mark,11):panel);
            if(special)g.fill(x,y+5,x+2,y+h-5,mark);
            if(over&&active)g.outline(x,y,w,h,mark);
            g.nextStratum();CivicStyle.icon(g,blocked?"lock":icon,x+14,y+14,special?mark:interactive?CivicStyle.inkAccent(theme):muted);
            CivicStyle.wrapped(g,title,x+38,y+11,w-50,2,text);
            CivicStyle.detailWrapped(g,blocked?reason:detail,x+38,y+33,w-50,2,muted);
            if(mode.equals("contract")){
                CivicStyle.text(g,selected?"На панели":"Нажмите, чтобы закрепить",x+12,y+h-27,w-24,selected?CivicStyle.inkAccent(theme):muted);
                g.fill(x+12,y+h-10,x+w-12,y+h-6,color("background"));if(total>0)g.fill(x+12,y+h-10,x+12+(int)((w-24)*Math.clamp((double)progress/total,0,1)),y+h-6,accent);
            }
        }
        g.disableScissor();
    }
}
