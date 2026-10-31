package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import static cc.crafto.ui.Protocol.*;

/** Regression fixtures reproduce the supplied legacy grid, order controls and long lore. */
public final class DensityProbe {
    static int checks,renders;static Path out,resources;
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    static Object field(Object o,String n)throws Exception{return MinimalProbe.field(o,n);}
    static Object attr(Object o,String n)throws Exception{return MinimalProbe.attr(o,n);}
    static GuiGraphicsExtractor draw(Screen s){return MinimalProbe.draw(s);}
    static void render(Screen s,String name,int mx,int my)throws Exception{var b=new BufferedImage(s.width*2,s.height*2,2);var g=b.createGraphics();g.scale(2,2);{var frame=new GuiGraphicsExtractor(g);s.extractBackground(frame,mx,my,0);s.extractRenderState(frame,mx,my,0);}g.dispose();ImageIO.write(b,"PNG",out.resolve(name+".png").toFile());renders++;}
    static CraftoUIClient client(String theme)throws Exception{return MinimalProbe.client(theme);}
    static void navigation(String theme,int w,int h,boolean legacy)throws Exception{
        var c=client(theme);String base=legacy?"migrations/0.8.13/menus/":"menus/";
        var menu=JsonParser.parseString(Files.readString(resources.resolve(base+"society.json"))).getAsJsonObject();
        var s=new MenuScreen(c,object("screen",menu,"theme",c.lastTheme,"data",object(),"revision",1,"session","density","navigation","back-token","backLabel","Назад"));MinimalProbe.size(s,w,h);draw(s);
        check((Integer)field(s,"maxBodyScroll")==0,"navigation never adds a vertical scroll tail: "+w+"x"+h+" legacy="+legacy);
        int pages=(Integer)field(s,"pageCount");var seen=new HashSet<String>();var expected=new HashSet<String>();MenuSchema.visit(menu.getAsJsonArray("components"),n->{if(str(n,"type","").equals("button"))expected.add(str(n,"id",""));});
        for(int page=0;page<pages;page++){
            draw(s);var hits=(List<?>)field(s,"hits");
            for(var hit:hits){var n=(JsonObject)attr(hit,"node");String id=str(n,"id","");if(!str(n,"type","").equals("button")||id.startsWith("__"))continue;
                seen.add(id);check((Integer)attr(hit,"h")>=28&&(Integer)attr(hit,"h")<=42,"readable compact button");
                check((Integer)attr(hit,"y")+(Integer)attr(hit,"h")<=(Integer)field(s,"viewportHeight")-40,"complete button stays above fixed footer");
            }
            if(page+1<pages){s.keyPressed(new KeyEvent(267));s.mouseClicked(new MouseButtonEvent(30,90),false);check(c.sent.isEmpty(),"page change rejects stale action hitboxes");}
        }
        check(seen.equals(expected),"every original action remains reachable through pages");check(pages==1||h<400||w<500,"normal nine-action menu fits without paging");
        s.keyPressed(new KeyEvent(266));draw(s);s.keyPressed(new KeyEvent(259));check(str(c.sent.getLast(),"navigation","").equals("back-token"),"previous menu token survives local pagination");
        MinimalProbe.set(s,"pageIndex",0);draw(s);render(s,"density-"+theme+"-"+(legacy?"legacy":"current")+"-"+w+"x"+h,-100,-100);
    }
    static AddonScreen board(CraftoUIClient c,int w,int h)throws Exception{
        var m=new ChestMenu(6);for(int i=0;i<25;i++)m.getSlot(i).item=new ItemStack("minecraft:iron_sword","Зачистка торговых путей",List.of("Редкость: Легендарный","Сложность: Высшая","Задание: Победить мобов","Цель: Крипер × 160","Награда: 1280.00 монет","Время на выполнение: 1 д.","","При остановке сервера или паузе заказов","оставшееся время сохраняется.","Выход из игры не останавливает таймер.","Нажмите, чтобы принять заказ"));
        m.getSlot(45).item=new ItemStack("minecraft:book","Мои заказы",List.of("Прогресс и доступные награды"));
        m.getSlot(47).item=new ItemStack("minecraft:fishing_rod","Где ловить рыбу",List.of("Ловите в открытой воде. Водоём может истощиться.","Нажмите: состояние рыбы рядом с вами."));
        m.getSlot(49).item=new ItemStack("minecraft:clock","Обновление: каждый день в 00:00 МСК",List.of("Следующее: завтра в 00:00 МСК"));
        m.getSlot(53).item=new ItemStack("minecraft:emerald","Лимит наград",List.of("Осталось за последние 24 ч.: 5000 монет","Лимит восстанавливается постепенно.","Одновременно: не более 3 заказов"));
        Minecraft.getInstance().player.containerMenu=m;Minecraft.getInstance().gameMode.clicks.clear();
        var options=object("viewKey","orders","module","Контракты","commandModule","NeverLandTownyContracts","navigation","orders-back","backLabel","Назад","theme",c.lastTheme,"presentation",object("toolbar",object("primary",45,"info",53,"help",47),"statusSlot",49));
        var s=new AddonScreen(c,new ContainerScreen(m,new Object(),Component.literal("Государственные заказы")),options);MinimalProbe.size(s,w,h);draw(s);return s;
    }
    static void orders(String theme,int w,int h)throws Exception{
        var c=client(theme);var s=board(c,w,h);var slots=(List<Integer>)field(s,"slots");check(slots.size()==25&&!slots.contains(45)&&!slots.contains(47)&&!slots.contains(49)&&!slots.contains(53),"only actual orders occupy catalogue pages");
        var base=(AddonLayout)field(s,"layout");var controls=(List<?>)field(s,"toolbarControls");check(controls.size()==3,"mine, budget and fishing controls are fixed on the first page");
        for(var control:controls)check((Integer)attr(control,"y")==base.height()-64&&(Integer)attr(control,"x")+(Integer)attr(control,"w")<=base.width()-16,"all three controls fit the fixed toolbar");
        for(var card:(List<?>)field(s,"cards"))check((Integer)attr(card,"y")+(Integer)attr(card,"h")<=base.height()-88,"cards cannot overlap status or toolbar");
        render(s,"density-"+theme+"-orders-"+w+"x"+h,-100,-100);
        var first=((List<?>)field(s,"cards")).getFirst();double scale=((Number)field(s,"scale")).doubleValue(),left=((Number)field(s,"left")).doubleValue(),top=((Number)field(s,"top")).doubleValue();
        render(s,"density-"+theme+"-tooltip-"+w+"x"+h,(int)(left+((Integer)attr(first,"x")+10)*scale),(int)(top+((Integer)attr(first,"y")+10)*scale));
        s.keyPressed(new KeyEvent(267));draw(s);check(((List<?>)field(s,"toolbarControls")).size()==3,"toolbar stays fixed on the next order page");
        var fishing=((List<?>)field(s,"toolbarControls")).getLast();check(attr(fishing,"role").equals("help"),"fishing follows mine and reward budget");
        MinimalProbe.set(s,"lastClick",0L);MinimalProbe.click(s,(Integer)attr(fishing,"x"),(Integer)attr(fishing,"y"),0,0);check(Minecraft.getInstance().gameMode.clicks.getLast().slot()==47,"fishing still invokes the original addon status action");draw(s);MinimalProbe.set(s,"lastClick",0L);
        var primary=((List<?>)field(s,"toolbarControls")).stream().filter(o->{try{return attr(o,"role").equals("primary");}catch(Exception ex){throw new RuntimeException(ex);}}).findFirst().orElseThrow();
        MinimalProbe.click(s,(Integer)attr(primary,"x"),(Integer)attr(primary,"y"),0,0);check(Minecraft.getInstance().gameMode.clicks.getLast().slot()==45,"mine control invokes original native slot");
        draw(s);var info=((List<?>)field(s,"toolbarControls")).stream().filter(o->{try{return attr(o,"role").equals("info");}catch(Exception ex){throw new RuntimeException(ex);}}).findFirst().orElseThrow();int before=Minecraft.getInstance().gameMode.clicks.size();
        MinimalProbe.click(s,(Integer)attr(info,"x"),(Integer)attr(info,"y"),0,0);draw(s);check((Integer)field(s,"selected")==53,"budget description uses the actual budget item");check(Minecraft.getInstance().gameMode.clicks.size()==before,"budget information cannot execute an order action");
        render(s,"density-"+theme+"-budget-"+w+"x"+h,-100,-100);s.keyPressed(new KeyEvent(256));draw(s);check(!(Boolean)field(s,"detailsOpen"),"escape returns from information to the same board");
        var last=((List<?>)field(s,"cards")).getLast();MinimalProbe.set(s,"selected",attr(last,"slot"));s.keyPressed(new KeyEvent(258));check((Integer)field(s,"toolbarFocus")==0,"Tab reaches persistent controls after the catalogue");
        MinimalProbe.set(s,"lastClick",0L);s.keyPressed(new KeyEvent(257));check(Minecraft.getInstance().gameMode.clicks.getLast().slot()==45,"keyboard activation preserves the native Mine action");
        s.keyPressed(new KeyEvent(258));s.keyPressed(new KeyEvent(258));before=Minecraft.getInstance().gameMode.clicks.size();s.keyPressed(new KeyEvent(257));check((Boolean)field(s,"detailsOpen")&&Minecraft.getInstance().gameMode.clicks.size()==before,"budget information is keyboard reachable without a native transaction");
    }
    static void descriptions(String theme,int w,int h)throws Exception{
        var c=client(theme);String title="Большая зачистка торговых путей";var lore=List.of("Редкость: Легендарный","Сложность: Высшая","Цель: Крипер × 160","Награда:\t1280.00 монет","Время на выполнение: 1 д.");
        var layout=ItemInfoCard.layout(title,lore,"",Math.min(330,w-16),true);
        check(layout.lines().stream().noneMatch(l->l.text().contains("\t")||l.text().contains("§")),"lore cannot depend on tab stops or legacy formatting codes");
        check(layout.lines().stream().anyMatch(l->l.rarity()!=null),"rarity remains visually coloured");check(layout.lines().stream().anyMatch(l->l.text().contains("1280.00")&&l.tone().equals("accent")),"reward is separated from its muted label");
        for(int[] pointer:new int[][]{{w-1,h-1},{1,1},{w/2,h/2}}){var bounds=ItemInfoCard.tooltip(new GuiGraphicsExtractor(),new ItemStack("minecraft:iron_sword"),title,lore,"",c.lastTheme,pointer[0],pointer[1],w,h);check(bounds.x()>=0&&bounds.y()>=0&&bounds.x()+bounds.width()<=w&&bounds.y()+bounds.height()<=h,"styled description stays within the GUI");}
    }
    static void allConfiguredMenus(String theme,int w,int h)throws Exception{
        for(String id:List.of("hub","main","town","economy","adventures","society","management","development","security","profile","help")){
            var c=client(theme);var path=id.equals("help")?Path.of(System.getProperty("density.help.json")):resources.resolve("menus/"+id+".json");var menu=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var s=new MenuScreen(c,object("screen",menu,"theme",c.lastTheme,"data",object("player.name","Александр","player.status","Онлайн","player.world","NeverLand","player.gamemode","Выживание","player.group","Игрок"),"revision",1,"session",id,"navigation","back","backLabel","Назад"));MinimalProbe.size(s,w,h);draw(s);
            check((Integer)field(s,"maxBodyScroll")==0,id+" fits without vertical body scrollbar at "+w+"x"+h);
            if(h>=400&&w>=620)check((Integer)field(s,"pageCount")==1,id+" fits one normal-sized window");
            var expected=new HashSet<String>();MenuSchema.visit(menu.getAsJsonArray("components"),n->{if(str(n,"type","").equals("text"))expected.add(str(n,"id",""));});
            var visible=new HashSet<String>();int pages=(Integer)field(s,"pageCount");for(int page=0;page<pages;page++){
                draw(s);for(var hit:(List<?>)field(s,"hits")){var node=(JsonObject)attr(hit,"node");if(str(node,"type","").equals("text"))visible.add(str(node,"id",""));}if(page+1<pages)s.keyPressed(new KeyEvent(267));
            }
            if(id.equals("profile")||id.equals("help")){check(visible.containsAll(expected.stream().filter(key->!Set.of("nick").contains(key)).toList()),id+" text remains available through local pages");MinimalProbe.set(s,"pageIndex",0);draw(s);render(s,"density-"+theme+"-"+id+"-"+w+"x"+h,-100,-100);}
        }
    }
    static class HeaderPanel extends PanelScreen {
        final JsonObject colours;HeaderPanel(JsonObject colours){super("NeverLand",new CraftoUIClient());this.colours=colours;}
        @Override protected void init(){begin(colours,"NeverLand","Показатели города и доступные действия","city");footer(this::onClose,"",null);finish();}
    }
    static void headers(String theme,int w,int h)throws Exception{
        var c=client(theme);var panel=new HeaderPanel(c.lastTheme);MinimalProbe.size(panel,w,h);var local=draw(panel);var civic=new CivicScreen(c,null,"city");MinimalProbe.size(civic,w,h);
        civic.receive(object("op","view","request",field(civic,"awaited"),"route","city","title","NeverLand","token","header-test","rows",new JsonArray(),"lines",List.of(),"pages",1));var server=draw(civic);
        for(var g:List.of(local,server)){
            var title=g.positions.stream().filter(t->t.text().equals("NeverLand")).findFirst().orElseThrow();var subtitle=g.positions.stream().filter(t->t.text().equals("Показатели города и доступные действия")).findFirst();
            if(subtitle.isPresent()){check(Math.abs(title.x()-subtitle.get().x())<0.5,"title and subtitle share the same left edge");check(subtitle.get().y()-title.y()>=20,"subtitle cannot touch the title");}
        }
        render(panel,"density-"+theme+"-panel-header-"+w+"x"+h,-100,-100);render(civic,"density-"+theme+"-city-header-"+w+"x"+h,-100,-100);
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);resources=Path.of(args[1]);MinimalProbe.resources=resources;
        for(String theme:List.of("dark","classic"))for(int[] size:new int[][]{{829,563},{620,400},{520,340},{320,240},{320,180}}){navigation(theme,size[0],size[1],true);navigation(theme,size[0],size[1],false);orders(theme,size[0],size[1]);descriptions(theme,size[0],size[1]);allConfiguredMenus(theme,size[0],size[1]);headers(theme,size[0],size[1]);}
        System.out.println("PASS "+checks+" density/toolbar/description checks; "+renders+" production-renderer PNGs; explicit platform/inventory/transport doubles, no game runtime");
    }
}
