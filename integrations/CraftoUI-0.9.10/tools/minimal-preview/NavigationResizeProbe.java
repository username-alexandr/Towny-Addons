package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import java.nio.file.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;
import static cc.crafto.ui.client.MinimalProbe.*;

/** Production screens, Minecraft bitmap metrics and explicit platform/transport doubles. */
public class NavigationResizeProbe {
    static int checks;
    static void verify(boolean ok,String description){if(!ok)throw new AssertionError(description);checks++;}
    static MenuScreen menu(CraftoUIClient c,String id,int w,int h)throws Exception{
        var definition=JsonParser.parseString(Files.readString(resources.resolve("menus/"+id+".json"))).getAsJsonObject();
        var s=new MenuScreen(c,object("screen",definition,"theme",c.lastTheme,"data",object("player.name","ТестовыйИгрок","player.status","Онлайн","player.level",24,"player.exp",.5),"revision",1,"session","resize-session","navigation",id.equals("hub")?"":"resize-navigation","backLabel",id.equals("hub")?"В игру":"Назад"));
        size(s,w,h);return s;
    }
    static void blur(Screen screen,CraftoUIClient c){
        int before=screen.blurCalls;c.settings.blur=true;draw(screen);
        verify(screen.blurCalls==before+1,screen.getClass().getSimpleName()+" enables blur once in a frame");
        before=screen.blurCalls;c.settings.blur=false;draw(screen);
        verify(screen.blurCalls==before,screen.getClass().getSimpleName()+" respects disabled blur");
    }
    static void resize(String theme,int w,int h,float setting)throws Exception{
        var c=client(theme);c.settings.animations=false;c.settings.scale=setting;
        for(String id:List.of("hub","town","economy","adventures","society","development","security","management","profile")){
            var s=menu(c,id,w,h);var graphics=draw(s);var view=(JsonObject)field(s,"view");int width=integer(view,"width",0);
            verify(((Number)field(s,"left")).doubleValue()>=0&&((Number)field(s,"top")).doubleValue()>=0,id+" window stays within GUI at "+setting);
            verify(graphics.clippedTexts.isEmpty(),id+" clipped text at "+w+"x"+h+" / "+setting+": "+graphics.clippedTexts);
            if(!id.equals("profile")){
                boolean back=!id.equals("hub");int backWidth=width<400?26:78,titleX=back?backWidth+24:18,searchWidth=width<400?44:width<440?62:102;
                verify(CivicStyle.wrap(s.getTitle().getString(),width-titleX-searchWidth-64).size()<=2,id+" full header stays readable");
                var grid=view.getAsJsonArray("components").asList().stream().map(JsonElement::getAsJsonObject).filter(n->str(n,"type","").equals("grid")).findFirst().orElseThrow();
                int columns=integer(grid,"columns",1),cell=(integer(grid,"w",0)-(columns-1)*integer(grid,"gap",0))/columns;
                for(var child:grid.getAsJsonArray("children")){
                    var n=child.getAsJsonObject();int available=cell-(str(n,"icon","").isBlank()?14:44);
                    verify(CivicStyle.wrap(str(n,"text",""),available).size()<=2,id+" label needs more than two lines: "+str(n,"text","")+" / "+cell);
                }
            }
            blur(s,c);
        }
        var nativeScreen=nativeScreen(c,inventory("builds"),"builds",w,h);blur(nativeScreen,c);
        set(nativeScreen,"inventoryMode",true);blur(nativeScreen,c);
        if(setting==1){save(menu(c,"economy",w,h),"resize-"+theme+"-economy-"+w+"x"+h);}
    }
    static void settings(String theme,int w,int h)throws Exception{
        var c=client(theme);c.settings.animations=false;var owner=menu(c,"hub",w,h);draw(owner);
        var settings=new SettingsScreen(owner,c);size(settings,w,h);blur(settings,c);
        set(settings,"section",1);settings.rebuildWidgets();draw(settings);
        var configure=settings.widgets.stream().filter(b->b.label.startsWith("Настроить панель")).findFirst().orElseThrow();
        settings.setFocused(configure);settings.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(configure.x+3,Math.max(configure.y+3,settings.bodyTop+3)),false);
        verify(Minecraft.getInstance().gui.screen instanceof PersonalScreen,"settings opens its contract page");
        var contracts=(PersonalScreen)Minecraft.getInstance().gui.screen;size(contracts,w,h);draw(contracts);blur(contracts,c);
        verify(contracts.widgets.stream().noneMatch(b->List.of("Город","Приглашения","Сводка","Поиск / избранное").contains(b.label)),"contract settings contain no unrelated category tabs");
        int messages=c.sent.size();for(int i=0;i<81;i++)contracts.tick();verify(c.sent.size()==messages,"contract settings do not request city/directory data");
        var position=new HudPositionScreen(contracts,c);size(position,w,h);blur(position,c);position.onClose();verify(Minecraft.getInstance().gui.screen==contracts,"HUD Back returns to contract settings");
        contracts.onClose();verify(Minecraft.getInstance().gui.screen==settings&&owner.suspended,"contract Back returns to Settings with live owner session");
        settings.onClose();verify(Minecraft.getInstance().gui.screen==owner&&!owner.suspended,"settings Back returns to its original menu");
        var city=new CivicScreen(c,owner,"city");size(city,w,h);blur(city,c);
        var command=new CommandScreen(c,owner,"Towny");size(command,w,h);blur(command,c);
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);resources=Path.of(args[1]);
        for(String theme:List.of("dark","classic"))for(int[] size:new int[][]{{871,447},{620,400},{520,340},{426,240},{320,240},{320,180}}){
            for(float scale:new float[]{.65f,.75f,.9f,1f,1.15f,1.3f})resize(theme,size[0],size[1],scale);
            settings(theme,size[0],size[1]);
        }
        System.out.println("PASS "+checks+" navigation, resize, text-bound and blur-request checks; "+renders+" renders; explicit platform/network doubles, no GPU blur validation");
    }
}
