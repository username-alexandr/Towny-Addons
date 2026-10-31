package cc.crafto.ui.client;

import cc.crafto.ui.CivicPageLayout;
import cc.crafto.ui.CommandCatalogLayout;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Shared presentation for local pages and server forms. Actions remain owned by each screen. */
abstract class PanelScreen extends Screen {
    private final CraftoUIClient backgroundClient;
    protected JsonObject theme;
    protected CivicPageLayout layout;
    protected int left,span,bodyTop,bottom,scroll,contentHeight;
    protected boolean dirty;
    protected String heading="",subtitle="",icon="",status="";
    private int pageIndex,pageCount=1;
    private Runnable previousPage,nextPage;
    private double wheelRemainder;
    private boolean hasTabs=true;
    private final Map<AbstractWidget,Integer> bodyWidgets=new LinkedHashMap<>();
    private final List<Panel> panels=new ArrayList<>();
    private record Panel(int y,int height,String title,String detail,String icon){}

    PanelScreen(String title,CraftoUIClient client){super(Component.literal(title));backgroundClient=client;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){
        if(backgroundClient.settings.blur)extractBlurredBackground(g);
        g.fill(0,0,width,height,UiTheme.color(theme,"shade"));
    }
    protected void begin(JsonObject theme,String heading,String subtitle,String icon){
        this.theme=theme;this.heading=heading;this.subtitle=subtitle;this.icon=icon;
        layout=CivicPageLayout.fit(width,height);left=layout.left();span=layout.span();bodyTop=layout.bodyTop();bottom=layout.bottom();
        hasTabs=true;
        bodyWidgets.clear();panels.clear();contentHeight=0;status="";pageLabel="";
        pageIndex=0;pageCount=1;previousPage=null;nextPage=null;
    }
    protected void changed(){dirty=true;}
    protected void noTabs(){hasTabs=false;bodyTop=layout.navY()+2;}
    protected void resetScroll(){scroll=0;wheelRemainder=0;}
    protected void synchronizeWidgets(){
        if(!dirty)return;dirty=false;
        EditBox old=getFocused() instanceof EditBox e?e:null;int cursor=old==null?0:old.getCursorPosition(),position=scroll;
        rebuildWidgets();
        if(old!=null&&getFocused() instanceof EditBox e&&old.getMessage().getString().equals(e.getMessage().getString())){e.setCursorPosition(Math.min(cursor,e.getValue().length()));scrollTo(position);}
    }
    protected int columns(int minimum,int max){return Math.clamp((span+10)/(minimum+10),1,max);}
    protected int cellWidth(int columns){return (span-8-(columns-1)*10)/columns;}
    protected <T extends AbstractWidget>T body(T widget,int y){
        bodyWidgets.put(widget,y);widget.setY(bodyTop+y-scroll);contentHeight=Math.max(contentHeight,y+widget.getHeight());return addRenderableWidget(widget);
    }
    protected CivicCardButton card(String title,String detail,String icon,String mode,boolean selected,int x,int y,int w,int h,Runnable action){
        return body(new CivicCardButton(title,detail,"",icon,mode,action!=null,selected,left+x,0,w,h,bodyTop,bottom,()->{if(!dirty&&action!=null)action.run();},theme),y);
    }
    protected Button bodyButton(String label,int x,int y,int w,Runnable action){return card(label,"","","control",false,x,y,w,22,action);}
    protected Button bodyPrimary(String label,int x,int y,int w,Runnable action){return card(label,"","","primary",false,x,y,w,22,action);}
    protected Button fixed(String label,int x,int y,int w,boolean primary,Runnable action){
        return addRenderableWidget(new CivicCardButton(label,"","","",primary?"primary":"control",true,false,x,y,w,22,0,height,()->{if(!dirty)action.run();},theme));
    }
    private Button pagerButton(String label,int x,Runnable action){
        return addRenderableWidget(new CivicCardButton(label,"","","","control",true,false,x,bottom+2,24,CommandCatalogLayout.PAGER_HEIGHT,0,height,()->{if(!dirty)action.run();},theme));
    }
    protected Button tab(String label,String icon,int i,int count,boolean selected,Runnable action){
        var b=layout.tab(i,count);return addRenderableWidget(new CivicCardButton(label,"","",icon,span/count<140?"icon-tab":"tab",true,selected,b.x(),b.y(),b.width(),b.height(),0,height,()->{if(!dirty)action.run();},theme));
    }
    protected EditBox input(String label,String hint,String value,int x,int y,int w,int max,java.util.function.Consumer<String> responder){
        var box=new ClippedEditBox(label,left+x,0,w,22);box.setMaxLength(max);box.setValue(value);box.setHint(Component.literal(hint));box.setResponder(responder);return body(box,y);
    }
    protected int info(String title,String detail,String icon,int y){
        int h=(title.isBlank()?0:CivicStyle.wrap(title,span-54).size()*12+10)+CivicStyle.wrap(detail,CivicStyle.detailWidth(title.isBlank()?span-32:span-54)).size()*10+16;
        panels.add(new Panel(y,h,title,detail,icon));contentHeight=Math.max(contentHeight,y+h);return y+h+12;
    }
    protected void finish(){scrollTo(scroll);}
    protected void pager(int page,int pages,Runnable previous,Runnable next){
        pageIndex=page;pageCount=pages;previousPage=previous;nextPage=next;
        if(pages<=1)return;
        pagerButton("‹",left+span-96,previous).active=page>0;
        pagerButton("›",left+span-24,next).active=page+1<pages;
        pageLabel=(page+1)+"/"+pages;
    }
    private String pageLabel="";
    protected void footer(Runnable back,String right,Runnable rightAction){
        pageLabel="";fixed("← Назад",left,layout.footerY(),80,false,back);
        if(rightAction!=null)fixed(right,left+span-90,layout.footerY(),90,false,rightAction);
    }
    private int maxScroll(){return Math.max(0,contentHeight-(bottom-bodyTop));}
    protected boolean scrollTo(int requested){
        int next=Math.clamp(requested,0,maxScroll());boolean moved=next!=scroll;scroll=next;
        bodyWidgets.forEach((b,y)->b.setY(bodyTop+y-scroll));return moved;
    }
    @Override public void setFocused(GuiEventListener target){
        super.setFocused(target);if(target instanceof AbstractWidget b&&bodyWidgets.containsKey(b)){
            int top=bodyWidgets.get(b),end=top+b.getHeight();scrollTo(top<scroll?top:end>scroll+bottom-bodyTop?end-(bottom-bodyTop):scroll);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        synchronizeWidgets();int bg=UiTheme.color(theme,"background"),panel=UiTheme.color(theme,"panel"),muted=UiTheme.color(theme,"muted"),ink=UiTheme.color(theme,"text"),accent=UiTheme.color(theme,"accent");
        g.nextStratum();CivicStyle.panel(g,layout.x(),layout.y(),layout.width(),layout.height(),CivicStyle.mix(bg,panel,30));
        CivicStyle.icon(g,icon,left+2,layout.y()+13,CivicStyle.inkAccent(theme));

        int titleY=layout.y()+14;
        if(layout.compact())CivicStyle.text(g,heading,left+28,titleY,span-38,ink);
        else {g.pose().pushMatrix();g.pose().translate(left+28,titleY);g.pose().scale(1.15f,1.15f);CivicStyle.text(g,heading,0,0,(int)((span-38)/1.15f),ink);g.pose().popMatrix();CivicStyle.detail(g,subtitle,left+28,layout.y()+39,span-38,muted);}
        if(hasTabs)g.fill(left,layout.navY()+layout.navHeight()+2,left+span,layout.navY()+layout.navHeight()+3,CivicStyle.mix(panel,UiTheme.color(theme,"border"),30));
        g.enableScissor(left,bodyTop,left+span,bottom);
        for(var p:panels){int y=bodyTop+p.y()-scroll;CivicStyle.panel(g,left,y,span-8,p.height(),panel);g.nextStratum();int dy=12;
            if(!p.title().isBlank()){CivicStyle.icon(g,p.icon(),left+12,y+9,CivicStyle.inkAccent(theme));var titleLines=CivicStyle.wrap(p.title(),span-54);for(int i=0;i<titleLines.size();i++)g.text(font,titleLines.get(i),left+38,y+13+i*12,ink,false);dy=12+titleLines.size()*12;}
            for(String line:CivicStyle.wrap(p.detail(),CivicStyle.detailWidth(p.title().isBlank()?span-32:span-54))){CivicStyle.detail(g,line,left+(p.title().isBlank()?16:38),y+dy,p.title().isBlank()?span-32:span-54,muted);dy+=10;}
        }
        g.disableScissor();
        if(maxScroll()>0){int track=bottom-bodyTop,thumb=Math.max(10,track*track/contentHeight),y=bodyTop+(track-thumb)*scroll/maxScroll();g.fill(left+span-3,bodyTop,left+span,bottom,panel);g.fill(left+span-3,y,left+span,y+thumb,accent);}
        String footer=status;
        if(pageCount>1)footer+=(footer.isBlank()?"":" · ")+"Колёсико — страницы";
        else if(footer.isBlank()&&maxScroll()>0)footer="Колёсико / Page Down — прокрутка";
        CivicStyle.text(g,footer,left,bottom+8,span-(pageLabel.isEmpty()?0:106),muted);
        if(!pageLabel.isEmpty())g.centeredText(font,pageLabel,left+span-48,bottom+8,ink);
        g.fill(left,bottom+26,left+span,bottom+27,CivicStyle.mix(panel,UiTheme.color(theme,"border"),30));
        g.nextStratum();super.extractRenderState(g,mx,my,delta);
        if(!footer.isBlank()&&font.width(footer)>span-(pageLabel.isEmpty()?0:106)&&mx>=left&&mx<left+span-(pageLabel.isEmpty()?0:106)&&my>=bottom+6&&my<bottom+20)g.setTooltipForNextFrame(font,Component.literal(footer),mx,my);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){return !dirty&&super.mouseClicked(event,twice);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){
        if(!Double.isFinite(v)||v==0)return false;
        if(x<left||x>=left+span||y<bodyTop||y>=bottom+24){wheelRemainder=0;return false;}
        if(dirty)return true;
        if(y<bottom&&maxScroll()>0&&scrollTo(scroll+(v<0?32:-32))){wheelRemainder=0;return true;}
        return turnPage(v);
    }
    private boolean turnPage(double vertical){
        if(pageCount<=1)return false;
        int direction=vertical<0?1:-1;
        if(pageIndex+direction<0||pageIndex+direction>=pageCount){wheelRemainder=0;return true;}
        // Trackpads may send fractional deltas. One gesture must not skip many server pages.
        if(Math.signum(wheelRemainder)!=Math.signum(vertical))wheelRemainder=0;
        wheelRemainder+=vertical;
        if(Math.abs(wheelRemainder)<1)return true;
        wheelRemainder=0;Runnable action=direction<0?previousPage:nextPage;
        if(action!=null)action.run();return true;
    }
    @Override public boolean charTyped(CharacterEvent e){synchronizeWidgets();return super.charTyped(e);}
    @Override public boolean keyPressed(KeyEvent e){
        if(e.key()==256||e.key()==259&&!(getFocused() instanceof EditBox)){onClose();return true;}if(dirty){if(getFocused() instanceof EditBox)synchronizeWidgets();else return true;}
        if(!(getFocused() instanceof EditBox)){if(e.key()==266)return scrollTo(scroll-(bottom-bodyTop))||turnPage(1);if(e.key()==267)return scrollTo(scroll+bottom-bodyTop)||turnPage(-1);if(e.key()==268)return scrollTo(0);if(e.key()==269)return scrollTo(maxScroll());}
        return super.keyPressed(e);
    }
    @Override public boolean isPauseScreen(){return false;}

    private final class ClippedEditBox extends EditBox {
        ClippedEditBox(String label,int x,int y,int w,int h){super(font,x,y,w,h,Component.literal(label));setBordered(false);setTextShadow(false);setTextColor(UiTheme.color(theme,"text"));}
        @Override public boolean isMouseOver(double x,double y){return y>=bodyTop&&y<bottom&&super.isMouseOver(x,y);}
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int x,int y,float d){
            if(getY()>=bottom||getY()+getHeight()<=bodyTop)return;
            g.enableScissor(getX()-5,Math.max(bodyTop,getY()-3),getX()+getWidth()+5,Math.min(bottom,getY()+getHeight()+3));
            CivicStyle.panel(g,getX()-5,getY()-3,getWidth()+10,getHeight()+6,UiTheme.color(theme,"button"));
            if(isFocused())g.fill(getX()-3,getY()+getHeight()+1,getX()+getWidth()+3,getY()+getHeight()+2,UiTheme.color(theme,"accent"));
            g.nextStratum();super.extractWidgetRenderState(g,x,y,d);g.disableScissor();
        }
    }
}
