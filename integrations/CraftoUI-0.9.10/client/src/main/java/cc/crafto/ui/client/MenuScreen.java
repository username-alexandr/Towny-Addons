package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** One renderer for all validated server-defined screens. Coordinates are design units. */
final class MenuScreen extends Screen {
    final String session;
    final long revision;
    boolean suspended;
    private final CraftoUIClient client;
    private final JsonObject screen,serverTheme;
    private JsonObject view;
    private final String navigation,backLabel;
    private JsonObject theme;
    private final Map<String,String> data=new HashMap<>();
    private final Map<String,Double> scroll=new HashMap<>();
    private final List<Hit> hits=new ArrayList<>();
    private final Map<String,ItemStack> icons=new HashMap<>();
    private final long started=System.nanoTime();
    private boolean detached;
    boolean isDetached(){return detached;}
    String menuId(){return str(screen,"id","hub");}
    private final ActionReplyGate replyGate=new ActionReplyGate();
    private long lastClick,noticeUntil;
    private String notice="",focus="";
    private boolean noticeSuccess;
    private float scale=1,left,top;
    private int viewportHeight,bodyScroll,maxBodyScroll;
    private int pageIndex,pageCount=1;private boolean pageDirty;private double wheelRemainder;
    private record Hit(JsonObject node,int x,int y,int w,int h,boolean enabled,String type,int maxScroll) {
        boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }
    MenuScreen(CraftoUIClient client,JsonObject body){
        super(Component.literal(str(body.getAsJsonObject("screen"),"title","Crafto")));
        this.client=client;screen=body.getAsJsonObject("screen").deepCopy();view=screen;serverTheme=body.getAsJsonObject("theme").deepCopy();
        session=str(body,"session","");revision=body.get("revision").getAsLong();patch(body.getAsJsonObject("data"));reloadTheme();
        navigation=str(body,"navigation","");backLabel=str(body,"backLabel","В игру");
        var position=client.viewMemory.get("menu/"+menuId());bodyScroll=position.scroll();pageIndex=position.page();scroll.putAll(position.lists());
    }
    void reloadTheme(){theme=UiTheme.resolve(serverTheme,client.settings.darkTheme);client.lastTheme=serverTheme.deepCopy();}
    JsonObject resolvedTheme(){return theme;}
    void patch(JsonObject values){if(values!=null)values.entrySet().forEach(e->{if(e.getValue().isJsonPrimitive())data.put(e.getKey(),e.getValue().getAsString());});}
    void patchComponents(JsonArray components){
        JsonObject candidate=screen.deepCopy();candidate.add("components",components.deepCopy());MenuSchema.validate(candidate);
        screen.add("components",candidate.get("components"));hits.clear();focus="";
    }
    void notice(String text,boolean success){notice=text;noticeSuccess=success;noticeUntil=System.nanoTime()+4_000_000_000L;}
    boolean acceptResult(long reply,String action){return replyGate.accept(reply,action);}
    void detach(){detached=true;}
    private String text(JsonObject node,String key,String fallback){return MenuSchema.bind(str(node,key,fallback),data);}
    private int color(String key){
        String fallback=switch(key){case "text"->"#FFF1F4FA";case "accent"->"#FF54D797";case "shade"->"#A0000000";default->"#FF343B4A";};
        String value=str(theme,key,fallback);try{return (int)Long.parseLong(value.substring(1),16);}catch(Exception e){return (int)Long.parseLong(fallback.substring(1),16);}
    }
    private void geometry(){
        view=MenuReflow.fit(screen,Math.max(240,(int)((width-16)/client.settings.scale)),(int)((height-16)/client.settings.scale));
        pageCount=integer(view,"pageCount",1);pageIndex=Math.clamp(pageIndex,0,pageCount-1);
        float w=integer(view,"width",560),h=integer(view,"height",460);
        scale=Math.max(.05f,Math.min((width-16f)/w,client.settings.scale));
        // Reflow keeps a minimum height for complete rows. When the requested scale would
        // exceed the physical viewport, fit the whole paginated page instead of cropping it.
        if(view.has("pageCount")&&h<=400){scale=Math.max(.05f,Math.min(scale,(height-16f)/h));viewportHeight=(int)h;}
        else viewportHeight=Math.min(Math.min(400,(int)h),Math.max(120,(int)((height-16)/scale)));
        if(viewportHeight*scale>height-16)scale=Math.max(.05f,(height-16f)/viewportHeight);
        maxBodyScroll=Math.max(0,(int)h-viewportHeight);bodyScroll=Math.clamp(bodyScroll,0,maxBodyScroll);
        left=(width-w*scale)/2;top=(height-viewportHeight*scale)/2;
        if(client.settings.animations)top+=(float)(6*(1-Math.clamp((System.nanoTime()-started)/180_000_000d,0,1)));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        if(client.settings.blur)extractBlurredBackground(g);
        g.fill(0,0,width,height,color("shade"));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        geometry();hits.clear();
        g.pose().pushMatrix();g.pose().translate(left,top);g.pose().scale(scale,scale);
        int w=integer(view,"width",560),h=viewportHeight;
        box(g,0,0,w,h,color("background"));
        g.fill(14,49,w-14,51,color("border"));
        boolean hasBack=!navigation.isEmpty()&&!backLabel.equals("В игру");
        int backW=w<400?26:78,titleX=hasBack?backW+24:18;
        int searchW=w<400?44:w<440?62:102;
        String menuTitle=getTitle().getString();
        int titleWidth=w-titleX-(client.personalAvailable?searchW+64:56);
        int titleLines=Math.min(2,CivicStyle.wrap(menuTitle,titleWidth).size());
        CivicStyle.wrapped(g,menuTitle,titleX,titleLines>1?16:22,titleWidth,2,color("text"));
        double mx=(mouseX-left)/scale,my=(mouseY-top)/scale;
        int clipX=12,clipY=54,clipW=w-24,clipH=h-94;
        g.enableScissor(clipX,clipY,clipX+clipW,clipY+clipH);
        renderNodes(g,view.getAsJsonArray("components"),0,-bodyScroll,w,integer(view,"height",460),mx,my,true,clipX,clipY,clipW,clipH,"panel",0,0,0);
        g.disableScissor();
        if(maxBodyScroll>0){int track=h-98,thumb=Math.max(12,track*track/(track+maxBodyScroll)),pos=(track-thumb)*bodyScroll/maxBodyScroll;
            g.fill(w-10,55,w-7,h-43,color("border"));g.fill(w-10,55+pos,w-7,55+pos+thumb,color("accent"));}
        // Dialogs clear content hit targets; navigation must remain available above them.
        if(hasBack)button(g,object("id","__back","type","button","text",w<400?"←":"← "+backLabel,"tooltip",backLabel),14,14,backW,26,true,mx,my);
        if(client.personalAvailable){
            button(g,object("id","__personal","type","button","text",w<400?"":"Поиск","tooltip","Поиск и избранное"),w-48-searchW,14,searchW,26,true,mx,my);
            g.nextStratum();CivicStyle.icon(g,"search",w-48-searchW+(searchW<60?(searchW-18)/2:9),18,CivicStyle.inkAccent(theme));
        }
        button(g,object("id","__close","type","button","text","×"),w-40,14,26,26,true,mx,my);
        g.fill(14,h-38,w-14,h-37,color("border"));
        boolean footerActions=false;
        for(var element:view.getAsJsonArray("components")){var node=element.getAsJsonObject();if(!str(node,"layoutRole","").equals("footer"))continue;footerActions=true;
            int bx=integer(node,"x",16),by=h-31,bw=integer(node,"w",100);var display=node.deepCopy();display.addProperty("text",text(node,"shortText",text(node,"text","")));button(g,display,bx,by,bw,22,bool(node,"enabled",true),mx,my);
            hits.set(hits.size()-1,new Hit(node,bx,by,bw,22,bool(node,"enabled",true),"button",0));
        }
        if(!footerActions)g.text(font,clip(text(screen,"footer",""),w-34-(pageCount>1?122:0)),18,h-24,color("muted"),false);
        if(pageCount>1){button(g,object("id","__page_previous","type","button","text","‹"),w-116,h-31,24,22,pageIndex>0,mx,my);g.centeredText(font,(pageIndex+1)+" / "+pageCount,w-66,h-24,color("muted"));button(g,object("id","__page_next","type","button","text","›"),w-40,h-31,24,22,pageIndex+1<pageCount,mx,my);}
        if(System.nanoTime()<noticeUntil&&!notice.isBlank()){
            g.nextStratum();box(g,20,h-83,w-40,32,color("panel"));g.text(font,clip(notice,w-60),30,h-71,color(noticeSuccess?"accent":"text"),false);
        }
        g.pose().popMatrix();
        pageDirty=false;
        for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(hit.contains(mx,my)){
            if(str(hit.node,"id","").startsWith("__page_")&&!hit.enabled)break;
            String tooltip=text(hit.node,"tooltip","");
            if(!hit.enabled){String reason=text(hit.node,"disabledReason","Это действие пока недоступно.");tooltip=reason+(tooltip.isBlank()?"":"\n\n"+tooltip);}
            if(tooltip.isBlank()&&(hit.type.equals("button")||hit.type.equals("text")&&!bool(hit.node,"wrap",false))&&font.width(text(hit.node,"text",""))>hit.w-(hit.type.equals("button")?14:0))tooltip=text(hit.node,"text","");
            // Text inside an information card must not mask the card's hover explanation.
            if(tooltip.isBlank()&&hit.type.equals("text"))continue;
            if(!tooltip.isBlank()){
                ItemInfoCard.tooltip(g,ItemStack.EMPTY,text(hit.node,"text",text(hit.node,"tooltipTitle","Описание")),List.of(tooltip),"",theme,mouseX,mouseY,width,height);
            }break;
        }}
    }
    private void renderNodes(GuiGraphicsExtractor g,JsonArray nodes,int ox,int oy,int parentW,int parentH,double mx,double my,boolean parentEnabled,
                             int cx,int cy,int cw,int ch,String parentType,int cols,int gap,int rowHeight){
        int index=0;
        for(JsonElement e:nodes){JsonObject n=e.getAsJsonObject();String type=str(n,"type","");
            if(str(n,"layoutRole","").equals("footer"))continue;
            if(str(n,"layoutRole","").equals("aside")&&integer(n,"h",0)==0)continue;
            int x=ox+integer(n,"x",0),y=oy+integer(n,"y",0),w=integer(n,"w",0),h=integer(n,"h",0);
            if(parentType.equals("grid")){int cell=(parentW-(cols-1)*gap)/cols;x=ox+(index%cols)*(cell+gap);y=oy+(index/cols)*(rowHeight+gap);w=cell;h=rowHeight;}
            if(parentType.equals("list")){x=ox;y=oy+index*(rowHeight+gap);w=parentW;h=rowHeight;}
            index++;if(w<=0)w=parentW;if(h<=0)h=20;
            if(x+w<=cx||y+h<=cy||x>=cx+cw||y>=cy+ch)continue;
            boolean enabled=parentEnabled&&bool(n,"enabled",true);
            if(type.equals("dialog")){g.nextStratum();g.fill(cx,cy,cx+cw,cy+ch,color("shade"));hits.clear();}
            if(Set.of("panel","grid","list","dialog").contains(type)){
                if(!type.equals("grid")&&!type.equals("list"))box(g,x,y,w,h,color("panel"));
                int nx=Math.max(cx,x),ny=Math.max(cy,y),nw=Math.max(0,Math.min(cx+cw,x+w)-nx),nh=Math.max(0,Math.min(cy+ch,y+h)-ny);
                if(type.equals("panel")&&nw>0&&nh>0){
                    JsonObject hover=n;
                    // Older installed help.yml files still gain working card tooltips.
                    if(menuId().equals("help")&&!n.has("tooltip")&&n.has("children")){
                        var children=n.getAsJsonArray("children");
                        if(children.size()>1){hover=n.deepCopy();hover.addProperty("tooltipTitle",text(children.get(0).getAsJsonObject(),"text","Описание"));hover.addProperty("tooltip",text(children.get(1).getAsJsonObject(),"text",""));}
                    }
                    if(hover.has("tooltip"))hits.add(new Hit(hover,nx,ny,nw,nh,enabled,"panel",0));
                }
                int offset=0;
                if(type.equals("list")){
                    int rh=integer(n,"rowHeight",30),gp=integer(n,"gap",4),size=n.has("children")?n.getAsJsonArray("children").size():0;
                    int max=integer(n,"pageCapacity",0)>0?0:Math.max(0,size*(rh+gp)-gp-h);
                    offset=(int)Math.clamp(scroll.getOrDefault(str(n,"id",""),0d),0,max);
                    hits.add(new Hit(n,nx,ny,nw,nh,enabled,type,max));
                    if(max>0){g.fill(x+w-3,y,x+w,y+h,color("border"));int thumb=Math.max(10,h*h/(h+max));int pos=(int)((h-thumb)*(double)offset/max);g.fill(x+w-3,y+pos,x+w,y+pos+thumb,color("accent"));}
                }
                g.enableScissor(nx,ny,nx+nw,ny+nh);
                if(n.has("children"))renderNodes(g,visibleChildren(n),x,y-offset,w,h,mx,my,enabled,nx,ny,nw,nh,type,Math.max(1,integer(n,"columns",2)),integer(n,"gap",6),Math.max(1,integer(n,"rowHeight",48)));
                g.disableScissor();continue;
            }
            g.enableScissor(Math.max(cx,x),Math.max(cy,y),Math.min(cx+cw,x+w),Math.min(cy+ch,y+h));
            switch(type){
                case "button" -> {
                    button(g,n,x,y,w,h,enabled,mx,my);
                    hits.set(hits.size()-1,new Hit(n,Math.max(cx,x),Math.max(cy,y),Math.min(cx+cw,x+w)-Math.max(cx,x),Math.min(cy+ch,y+h)-Math.max(cy,y),enabled,"button",0));
                }
                case "text","notification" -> {
                    if(type.equals("notification"))box(g,x,y,w,h,color("panel"));
                    String text=text(n,"text","");int c=color(str(n,"color","text"));
                    // Minecraft floors transformed scissor sizes. Keep glyphs away from those edges
                    // so fractional menu scales cannot cut off the final column or an accent.
                    int inset=Math.min(2,Math.max(0,(w-1)/2)),textWidth=w-2*inset;
                    if(bool(n,"wrap",false)){int lineY=y+1;for(var line:font.split(Component.literal(text),textWidth)){if(lineY+font.lineHeight>y+h-1)break;g.text(font,line,x+inset,lineY,c,false);lineY+=font.lineHeight+3;}}
                    else g.text(font,clip(text,textWidth),x+inset,y+(h-font.lineHeight)/2,c,false);
                }
                case "progress" -> {
                    g.fill(x,y,x+w,y+h,color("border"));float v=0;try{v=Float.parseFloat(text(n,"value","0"));}catch(Exception ignored){}
                    v=Float.isFinite(v)?Math.clamp(v,0f,1f):0;
                    g.fill(x+2,y+2,x+2+(int)((w-4)*v),y+h-2,color("accent"));
                }
                case "avatar" -> {
                    box(g,x,y,w,h,color("button"));
                    if(minecraft.player!=null){
                        if(str(n,"mode","model").equals("model")){
                            // Entity picture-in-picture render states use absolute GUI bounds, unlike text/items.
                            g.pose().pushMatrix();g.pose().identity();
                            InventoryScreen.extractEntityInInventoryFollowsMouse(g,
                                Math.round(left+(x+2)*scale),Math.round(top+(y+2)*scale),
                                Math.round(left+(x+w-2)*scale),Math.round(top+(y+h-2)*scale),
                                Math.max(8,Math.round(h*0.42f*scale)),0.0625f,
                                (float)(left+mx*scale),(float)(top+my*scale),minecraft.player);
                            g.pose().popMatrix();
                        }
                        else {Identifier texture=minecraft.player.getSkin().body().texturePath();int size=Math.min(w,h);g.blit(texture,x,y,size,size,8f/64,8f/64,16f/64,16f/64);g.blit(texture,x,y,size,size,40f/64,8f/64,48f/64,16f/64);}
                    }
                }
                case "image" -> {
                    try{Identifier id=Identifier.parse(str(n,"texture","minecraft:textures/missing.png"));g.blit(id,x,y,w,h,0,0,1,1);}catch(Exception ignored){}
                }
                default -> {}
            }
            g.disableScissor();
            if(!type.equals("button"))hits.add(new Hit(n,Math.max(cx,x),Math.max(cy,y),Math.min(cx+cw,x+w)-Math.max(cx,x),Math.min(cy+ch,y+h)-Math.max(cy,y),enabled,type,0));
        }
    }
    private JsonArray visibleChildren(JsonObject node){
        var children=node.getAsJsonArray("children");int capacity=integer(node,"pageCapacity",0);if(capacity<=0)return children;
        var visible=new JsonArray();for(int i=pageIndex*capacity;i<Math.min(children.size(),(pageIndex+1)*capacity);i++)visible.add(children.get(i));return visible;
    }
    private void button(GuiGraphicsExtractor g,JsonObject n,int x,int y,int w,int h,boolean enabled,double mx,double my){
        boolean hovered=mx>=x&&my>=y&&mx<x+w&&my<y+h;
        boolean selected=focus.equals(str(n,"id",""));
        int base=color(enabled?(hovered||selected?"hover":"button"):"disabled");
        box(g,x,y,w,h,base);
        if(enabled&&(hovered||selected)){g.outline(x+1,y+1,w-2,h-2,color("accent"));}
        String icon=str(n,"icon","");boolean hasIcon=!icon.isBlank()&&h>=26;
        int labelX=x+(hasIcon?34:7),labelWidth=w-(hasIcon?44:14);
        int limit=Math.clamp((h-6-font.lineHeight)/12+1,1,2);
        int lines=Math.min(limit,CivicStyle.wrap(text(n,"text",""),labelWidth).size());
        int labelY=y+(h-font.lineHeight-Math.max(0,lines-1)*12)/2;
        if(hasIcon){
            ItemStack item=icons.computeIfAbsent(icon,k->{try{return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(k)));}catch(Exception e){return ItemStack.EMPTY;}});
            g.item(item,x+9,y+(h-16)/2);
            CivicStyle.wrapped(g,text(n,"text",""),labelX,labelY,labelWidth,limit,color(enabled?"text":"muted"));
        }else if(lines>1)CivicStyle.wrapped(g,text(n,"text",""),labelX,labelY,labelWidth,limit,color(enabled?"text":"muted"));
        else {String caption=clip(text(n,"text",""),labelWidth);g.text(font,caption,x+(w-font.width(caption))/2,labelY,color(enabled?"text":"muted"),false);}
        hits.add(new Hit(n,x,y,w,h,enabled,"button",0));
    }
    private void box(GuiGraphicsExtractor g,int x,int y,int w,int h,int background){
        CivicStyle.panel(g,x,y,w,h,background);
    }
    private String clip(String s,int width){if(width<=0)return "";return font.width(s)<=width?s:font.plainSubstrByWidth(s,Math.max(0,width-font.width("…")))+"…";}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(pageDirty)return true;
        if(event.button()!=0)return false;
        double x=(event.x()-left)/scale,y=(event.y()-top)/scale;
        for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(h.type.equals("button")&&h.contains(x,y)){activate(h);return true;}}return true;
    }
    private void remember(){client.viewMemory.put("menu/"+menuId(),pageIndex,-1,bodyScroll,scroll);}
    private void activate(Hit h){
        remember();
        if(str(h.node,"id","").startsWith("__page_")&&!h.enabled)return;
        if(!h.enabled){notice(text(h.node,"disabledReason","Это действие пока недоступно."),false);return;}
        String id=str(h.node,"id","");if(id.equals("__close")){onClose();return;}
        if(id.equals("__page_previous")||id.equals("__page_next")){turnPage(id.equals("__page_next")?1:-1);return;}
        long now=System.nanoTime();if(now-lastClick<150_000_000L)return;lastClick=now;focus=id;
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        if(id.equals("__commands")){client.commands(this,"");return;}
        if(id.equals("__personal")){client.personal(this,0);return;}
        if(id.equals("__back")){back();return;}
        client.send(Kind.ACTION,object("session",session,"revision",revision,"sequence",replyGate.sent(str(h.node,"clientRoute","")),"component",id));
    }
    private void back(){remember();if(navigation.isEmpty())onClose();else client.send(Kind.BACK,object("navigation",navigation));}
    private void turnPage(int direction){int next=Math.clamp(pageIndex+direction,0,pageCount-1);if(next!=pageIndex){pageIndex=next;focus="";hits.clear();pageDirty=true;remember();}}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        if(!Double.isFinite(vertical)||vertical==0)return false;
        double x=(mouseX-left)/scale,y=(mouseY-top)/scale;
        if(x<0||x>=integer(view,"width",560)||y<54||y>=viewportHeight)return false;
        if(pageDirty)return true;
        if(pageCount>1){if(Math.signum(wheelRemainder)!=Math.signum(vertical))wheelRemainder=0;wheelRemainder+=vertical;if(Math.abs(wheelRemainder)>=1){turnPage(vertical<0?1:-1);wheelRemainder=0;}return true;}
        for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(h.type.equals("list")&&h.contains(x,y)){String id=str(h.node,"id","");double old=scroll.getOrDefault(id,0d),next=Math.clamp(old-vertical*22,0,h.maxScroll);scroll.put(id,next);if(old!=next)return true;}}
        bodyScroll=Math.clamp(bodyScroll-(int)(vertical*30),0,maxBodyScroll);return true;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(event.key()==GLFW.GLFW_KEY_PAGE_DOWN||event.key()==GLFW.GLFW_KEY_PAGE_UP){if(pageCount>1){if(!pageDirty)turnPage(event.key()==GLFW.GLFW_KEY_PAGE_DOWN?1:-1);}else bodyScroll=Math.clamp(bodyScroll+(event.key()==GLFW.GLFW_KEY_PAGE_DOWN?1:-1)*Math.max(30,viewportHeight-100),0,maxBodyScroll);return true;}
        if(event.key()==GLFW.GLFW_KEY_F&&(event.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0){client.personal(this,0);return true;}
        if(event.key()==GLFW.GLFW_KEY_F7&&client.commandsAvailable){client.commands(this,"");return true;}
        if(event.key()==GLFW.GLFW_KEY_BACKSPACE){back();return true;}
        if(event.key()==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        List<Hit> buttons=hits.stream().filter(h->h.type.equals("button")).toList();
        if(event.key()==GLFW.GLFW_KEY_TAB&&!buttons.isEmpty()){
            int current=-1;for(int i=0;i<buttons.size();i++)if(str(buttons.get(i).node,"id","").equals(focus))current=i;
            int direction=(event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1;
            Hit h=buttons.get(Math.floorMod(current+direction,buttons.size()));focus=str(h.node,"id","");triggerImmediateNarration(true);return true;
        }
        if(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_KP_ENTER||event.key()==GLFW.GLFW_KEY_SPACE){buttons.stream().filter(h->str(h.node,"id","").equals(focus)).findFirst().ifPresent(this::activate);return true;}
        return super.keyPressed(event);
    }
    @Override public Component getNarrationMessage(){for(Hit h:hits)if(str(h.node,"id","").equals(focus))return Component.literal(text(h.node,"text","")+(h.enabled?"":". "+text(h.node,"disabledReason","Пока недоступно")));return title;}
    void releaseSession(){if(!detached){client.send(Kind.CLOSE,object("session",session));detached=true;}}
    @Override public void onClose(){releaseSession();minecraft.gui.setScreen(null);}
    @Override public void removed(){remember();if(!suspended)releaseSession();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
}
