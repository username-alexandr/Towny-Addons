package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Production CivicScreen, explicit UI and network doubles. No game or live server. */
public final class CivicRecoveryProbe {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static Object get(Object o,String n)throws Exception{var f=CivicScreen.class.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String n,Object v)throws Exception{var f=CivicScreen.class.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static int request(CivicScreen s)throws Exception{return (Integer)get(s,"awaited");}
    private static void draw(CivicScreen s){{var frame=new GuiGraphicsExtractor();s.extractBackground(frame,-1,-1,0);s.extractRenderState(frame,-1,-1,0);}}
    private static JsonObject view(CivicScreen s,String route,boolean back)throws Exception{
        return object("op","view","request",request(s),"token","view-token","route",route,"title","История города",
            "id","","rows",new JsonArray(),"lines",List.of(),"page",0,"pages",1,"canBack",back);
    }
    private static CivicScreen screen(CraftoUIClient c,Screen parent,int width,int height,String route,boolean back)throws Exception{
        var s=new CivicScreen(c,parent,route);s.width=width;s.height=height;s.initialize();s.receive(view(s,route,back));draw(s);
        Minecraft.getInstance().gui.setScreen(s);return s;
    }
    private static void click(CivicScreen s,String label){
        var b=s.widgets.stream().filter(w->w.label.equals(label)).findFirst().orElseThrow();
        s.mouseClicked(new MouseButtonEvent(b.x+b.w/2.,b.y+b.h/2.),false);
    }
    public static void main(String[] args)throws Exception{
        for(int[] size:new int[][]{{960,500},{520,340},{320,240},{320,180}}){
            String name=size[0]+"×"+size[1];var c=new CraftoUIClient();
            var s=screen(c,null,size[0],size[1],"history",false);
            s.receive(object("op","error","request",request(s),"message","Экран устарел. Нажмите «Обновить»."));draw(s);
            int before=c.sent.size();click(s,"Обновить");
            check(c.sent.size()==before+1&&str(c.sent.getLast(),"op","").equals("refresh"),name+" stale screen refresh must resynchronise instead of reusing page token");
            check(!c.sent.getLast().has("action")&&!c.sent.getLast().has("route"),name+" refresh contains no client action or destination");
            s.receive(view(s,"history",false));draw(s);check(!(Boolean)get(s,"busy")&&!(Boolean)get(s,"failed"),name+" refreshed view unlocks controls");

            var parent=new MenuScreen();s=screen(c,parent,size[0],size[1],"history",false);click(s,"← Назад");
            check(Minecraft.getInstance().gui.screen==parent,name+" direct history Back returns to its actual parent");
            check(!parent.suspended&&str(c.sent.getLast(),"op","").equals("close"),name+" root Back cancels server state");

            s=screen(c,null,size[0],size[1],"history",true);set(s,"token","stale");click(s,"← Назад");
            check(str(c.sent.getLast(),"op","").equals("back"),name+" nested Back uses server history");
            var old=view(s,"history",true);s.keyPressed(new KeyEvent(256));
            check(Minecraft.getInstance().gui.screen==null&&!(Boolean)get(s,"busy"),name+" Esc closes even while waiting for Back");
            check(str(c.sent.getLast(),"op","").equals("close"),name+" Esc sends cancellation without waiting");
            int afterClose=request(s);s.receive(old);check(request(s)==afterClose&&!(Boolean)get(s,"busy"),name+" late reply cannot revive closed screen");
            s.removed();check(c.civicReturn==null,name+" explicitly closed screen is not retained as a native return target");

            s=screen(c,null,size[0],size[1],"history",true);set(s,"busy",true);click(s,"×");
            check(Minecraft.getInstance().gui.screen==null&&str(c.sent.getLast(),"op","").equals("close"),name+" close button works during a server wait");

            s=screen(c,null,size[0],size[1],"history",true);s.updated();set(s,"deadline",0L);s.tick();draw(s);
            check((Boolean)get(s,"failed")&&!(Boolean)get(s,"busy"),name+" timeout releases the refresh button");
            click(s,"Обновить");check(str(c.sent.getLast(),"op","").equals("refresh"),name+" timeout can retry recovery");
            s.keyPressed(new KeyEvent(256));check(Minecraft.getInstance().gui.screen==null,name+" timeout recovery never blocks Esc");

            s=screen(c,null,size[0],size[1],"history",true);s.receive(object("op","review","request",request(s),"title","Подтверждение","confirmation","one-use-ticket","lines",List.of("Условия")));draw(s);click(s,"← Назад");
            check(str(c.sent.getLast(),"op","").equals("refresh")&&str(c.sent.getLast(),"confirmation","").isEmpty(),name+" Back cancels review without confirming or popping the route");

            s=screen(c,null,size[0],size[1],"history",true);s.receive(object("op","review","request",request(s),"title","Confirm","confirmation","expired-ticket","lines",List.of("Conditions")));draw(s);
            s.receive(object("op","error","request",request(s),"message","Подтверждение истекло"));draw(s);before=c.sent.size();click(s,"Обновить");
            check(c.sent.size()==before+1&&str(c.sent.getLast(),"op","").equals("refresh"),name+" expired confirmation also leaves refresh usable");

            s=screen(c,null,size[0],size[1],"history",true);s.keyPressed(new KeyEvent(259));check(str(c.sent.getLast(),"op","").equals("back"),name+" Backspace navigates Back");

            s=screen(c,null,size[0],size[1],"history",false);s.updated();old=view(s,"history",false);
            var newer=screen(c,null,size[0],size[1],"inbox",false);String beforeRoute=(String)get(newer,"route");newer.receive(old);
            check(get(newer,"route").equals(beforeRoute),name+" delayed previous-screen reply cannot replace new screen");
            check(request(s)!=request(newer),name+" civic requests are unique across instances");
            newer.resume();check(str(c.sent.getLast(),"op","").equals("refresh"),name+" native return also resynchronises");
        }
        System.out.println("PASS "+checks+" civic recovery checks; production CivicScreen with explicit UI/network doubles, no game runtime");
    }
}
