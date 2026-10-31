package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static cc.crafto.ui.Protocol.*;

/** Actual configuration/native renderers. Fixtures supply synthetic items, player, inventory and transport. */
public final class MinimalProbe {
    static int passed,renders;static Path out,resources;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);passed++;}
    static Object field(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static Object attr(Object object,String name)throws Exception{var m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(object);}
    static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static CraftoUIClient client(String theme)throws Exception{var c=new CraftoUIClient();c.lastTheme=JsonParser.parseString(Files.readString(resources.resolve("themes/"+theme+".json"))).getAsJsonObject();c.commandsAvailable=true;c.personalAvailable=true;c.settings.blur=false;return c;}
    static GuiGraphicsExtractor draw(Screen s){var g=new GuiGraphicsExtractor();s.extractBackground(g,-100,-100,0);s.extractRenderState(g,-100,-100,0);return g;}
    static void size(Screen s,int w,int h){s.width=w;s.height=h;s.initialize();}
    static void save(Screen s,String name)throws Exception{var image=new BufferedImage(s.width*2,s.height*2,2);var g=image.createGraphics();g.scale(2,2);{var frame=new GuiGraphicsExtractor(g);s.extractBackground(frame,-100,-100,0);s.extractRenderState(frame,-100,-100,0);}g.dispose();ImageIO.write(image,"PNG",out.resolve(name+".png").toFile());renders++;}
    static void click(AddonScreen s,int x,int y,int button,int modifiers)throws Exception{double scale=((Number)field(s,"scale")).doubleValue(),left=((Number)field(s,"left")).doubleValue(),top=((Number)field(s,"top")).doubleValue();s.mouseClicked(new MouseButtonEvent(left+(x+2)*scale,top+(y+2)*scale,button,modifiers),false);}
    static ChestMenu inventory(String scenario){
        var m=new ChestMenu(6);
        if(scenario.equals("market")){for(int i=0;i<20;i++)m.getSlot(i).item=new ItemStack("minecraft:diamond","Алмаз",List.of("Цена: 100 монет","Продавец: NeverLand"));for(int i=45;i<54;i++)m.getSlot(i).item=new ItemStack("minecraft:paper","Рынок "+i,List.of("Выбор рынка и предложения"));}
        else if(scenario.equals("orders")){int i=0;for(var r:OrderRarityStyle.values())m.getSlot(i++).item=new ItemStack(r.icon("DELIVERY"),"Поставка древесины",List.of("Редкость: "+r.label,"Нужно: 64 дубовых бревна"));}
        else{
            String[] names={"Ратуша","Посольство","Суд","Тюрьма","Архив","Бюро переписи населения"},icons={"bell","compass","iron_axe","iron_bars","book","writable_book"};
            for(int i=0;i<names.length;i++)m.getSlot(i).item=new ItemStack("minecraft:"+icons[i],names[i],List.of("Городская постройка","Стоимость и условия доступны при открытии"));
            m.getSlot(45).item=new ItemStack("minecraft:arrow","Все категории",List.of("Вернуться к разделам каталога"));
            String[] extras={"Специализация города","Городские указы","Городской склад","Исследования города","Энергия города","Содержание города"};int[] slots={46,48,49,50,52,53};
            for(int i=0;i<slots.length;i++)m.getSlot(slots[i]).item=new ItemStack("minecraft:book",extras[i],List.of("Дополнительный раздел города"));
        }
        return m;
    }
    static AddonScreen nativeScreen(CraftoUIClient c,ChestMenu m,String scenario,int w,int h)throws Exception{
        var roles=new JsonObject();var aux=new JsonArray();
        if(scenario.equals("builds")){roles.addProperty("back",45);for(int i:new int[]{46,48,49,50,52,53})aux.add(i);}
        var options=object("viewKey","minimal-"+scenario,"module",scenario.equals("builds")?"Постройки":"NeverLand","commandModule",scenario.equals("orders")?"NeverLandTownyContracts":"NeverLandTownyBuilds","navigation","native-token","backLabel","Назад","theme",c.lastTheme,"width",620,"height",420,"columns",3,"layout",scenario.equals("market")?"market":"cards","presentation",object("roles",roles,"auxiliary",aux,"hidden",new JsonArray(),"disabled",new JsonObject()));
        Minecraft.getInstance().player.containerMenu=m;Minecraft.getInstance().gameMode.clicks.clear();var original=new ContainerScreen(m,new Object(),Component.literal(scenario.equals("builds")?"Управление":scenario.equals("market")?"Городской рынок":"Личные заказы"));var s=new AddonScreen(c,original,options);size(s,w,h);draw(s);return s;
    }
    static void nativeChecks(String theme,int w,int h,String scenario)throws Exception{
        var c=client(theme);var m=inventory(scenario);var s=nativeScreen(c,m,scenario,w,h);String prefix=theme+" "+scenario+" "+w+"x"+h;
        var layout=(AddonLayout)field(s,"layout");check(layout.width()*layout.scale()<=640&&layout.height()*layout.scale()<=400,prefix+" bounded window");
        var slots=(List<Integer>)field(s,"slots");var cards=(List<?>)field(s,"cards");
        if(scenario.equals("builds")){
            check(slots.equals(List.of(0,1,2,3,4,5)),prefix+" only six buildings remain in catalogue");
            var text=draw(s).texts;check(text.stream().noneMatch(t->t.equals("Все категории")||t.equals("Разделы")||t.equals("Предметы")),prefix+" no repeated navigation/view buttons");
            check(text.stream().noneMatch(t->t.contains("Исследования города")),prefix+" cross-links leave the building grid");
            check(layout.detailW()==0,prefix+" no permanent empty sidebar");
        }else if(scenario.equals("market")){check(slots.size()==20,prefix+" identical goods stay distinct");check(slots.stream().allMatch(i->i<45),prefix+" market actions separated from goods");}
        else {check(slots.size()==OrderRarityStyle.values().length,prefix+" every rarity retained");check(draw(s).texts.stream().anyMatch(t->t.equals(OrderRarityStyle.values()[OrderRarityStyle.values().length-1].label)),prefix+" rarity visible");}
        save(s,"minimal-"+theme+"-"+scenario+"-"+w+"x"+h);
        var card=cards.getFirst();int slot=(Integer)attr(card,"slot"),x=(Integer)attr(card,"x"),y=(Integer)attr(card,"y");var item=m.getSlot(slot).getItem();
        click(s,x,y,1,1);var click=Minecraft.getInstance().gameMode.clicks.getLast();
        check(click.slot()==slot&&click.button()==1&&click.mode()==ContainerInput.QUICK_MOVE,prefix+" native slot, right button and Shift preserved");check(m.getSlot(slot).getItem()==item,prefix+" rendering does not rewrite items");
        set(s,"lastClick",0L);draw(s);int dw=(Integer)field(s,"designWidth"),dh=(Integer)field(s,"designHeight");
        click(s,dw-78,dh-35,0,0);draw(s);check((Boolean)field(s,"moreOpen"),prefix+" one More menu");save(s,"minimal-"+theme+"-"+scenario+"-"+w+"x"+h+"-more");
        var extras=(List<?>)field(s,"moreControls");check(!extras.isEmpty(),prefix+" secondary actions reachable");
        if(scenario.equals("builds")&&w>=500){
            var research=extras.stream().filter(e->{try{return (Integer)attr(e,"slot")==50;}catch(Exception ex){throw new RuntimeException(ex);}}).findFirst().orElseThrow();
            int before=Minecraft.getInstance().gameMode.clicks.size();click(s,(Integer)attr(research,"x"),(Integer)attr(research,"y"),0,0);
            check(Minecraft.getInstance().gameMode.clicks.getLast().slot()==50,prefix+" grouped research still clicks original slot");click(s,(Integer)attr(research,"x"),(Integer)attr(research,"y"),0,0);check(Minecraft.getInstance().gameMode.clicks.size()==before+1,prefix+" stale dropdown cannot trigger another action");
        }
        s.mouseScrolled(0,0,0,-1);check(true,prefix+" outside wheel safely ignored");
    }
    static void menuChecks(String theme,int w,int h,Path f)throws Exception{
        var c=client(theme);var menu=JsonParser.parseString(Files.readString(f)).getAsJsonObject();String id=str(menu,"id","");
        var data=object("player.name","sasha34","player.status","Онлайн","player.level","24","player.exp","0.65","player.group","Игрок","player.health","20","player.food","20","player.world","NeverLand","player.gamemode","Выживание");
        var s=new MenuScreen(c,object("screen",menu,"theme",c.lastTheme,"data",data,"revision",1,"session","minimal-session","navigation",id.equals("hub")?"":"nav-token","backLabel",id.equals("hub")?"В игру":"Назад"));size(s,w,h);var g=draw(s);
        check(g.texts.stream().noneMatch(t->t.equals("Действия")||t.equals("Поиск / ★")),"configuration header has one search entry");
        var hits=(List<?>)field(s,"hits");var ids=new HashSet<String>();for(var hit:hits){var node=(JsonObject)attr(hit,"node");if(str(node,"type","").equals("button"))check(ids.add(str(node,"id","")),"configuration button drawn once");}
        check(!ids.contains("return")&&!ids.contains("home"),"no redundant configured close/home buttons");
        if(id.equals("hub")){check(!ids.contains("__back")&&ids.contains("__close"),"root has a single close control");}
        else check(ids.contains("__back"),"previous menu retained");
        if(!id.equals("profile"))check(hits.stream().anyMatch(hit->{try{return !str((JsonObject)attr(hit,"node"),"id","").startsWith("__")&&str((JsonObject)attr(hit,"node"),"type","").equals("button")&&(Integer)attr(hit,"h")>=28;}catch(Exception ex){throw new RuntimeException(ex);}}),"at least one complete action is visible before scrolling, including the smallest GUI");
        save(s,"minimal-"+theme+"-menu-"+id+"-"+w+"x"+h);
        if(!id.equals("hub")){int before=c.sent.size();s.keyPressed(new KeyEvent(259));check(c.sent.size()==before+1&&str(c.sent.getLast(),"navigation","").equals("nav-token"),"configuration Back preserves server navigation token");}
    }
    static void compactConfirmation(String theme,int w,int h)throws Exception{
        var c=client(theme);var m=new ChestMenu(6);var conditions=new ArrayList<>(List.of("Необходимо: 64 дубовых бревна","Награда: 120 монет","После подтверждения ресурсы будут переданы городу","Отмену можно выполнить отдельно"));for(int i=1;i<=24;i++)conditions.add("Условие "+i+": ресурсы проверяются сервером");m.getSlot(13).item=new ItemStack("minecraft:oak_log","Поставка древесины",conditions);m.getSlot(49).item=new ItemStack("minecraft:barrier","Отменить поставку",List.of("Ресурсы останутся у вас"));m.getSlot(51).item=new ItemStack("minecraft:emerald","Подтвердить",List.of("Передать ресурсы"));
        Minecraft.getInstance().player.containerMenu=m;Minecraft.getInstance().gameMode.clicks.clear();var roles=object("cancel",49,"confirm",51);var options=object("module","Контракты","commandModule","NeverLandTownyContracts","navigation","confirm-token","backLabel","Назад","theme",c.lastTheme,"presentation",object("roles",roles));
        var s=new AddonScreen(c,new ContainerScreen(m,new Object(),Component.literal("Подтверждение поставки")),options);size(s,w,h);draw(s);
        var layout=(AddonLayout)field(s,"layout");var buttons=(List<?>)field(s,"navigationControls");var cards=(List<?>)field(s,"cards");check(buttons.size()==2,"only original confirmation and cancellation actions");
        for(var card:cards)check((Integer)attr(card,"y")+(Integer)attr(card,"h")<=layout.height()-81,"confirmation actions cannot cover item cards");
        set(s,"detailsOpen",true);draw(s);save(s,"minimal-"+theme+"-confirmation-"+w+"x"+h);check((Integer)field(s,"detailHeight")>=40,"small confirmation description has readable scrolling space");
        double left=((Number)field(s,"left")).doubleValue(),top=((Number)field(s,"top")).doubleValue(),scale=((Number)field(s,"scale")).doubleValue();s.mouseScrolled(left+100*scale,top+80*scale,0,-1);draw(s);check((Integer)field(s,"detailScroll")>0,"long description remains scrollable");
        buttons=(List<?>)field(s,"navigationControls");var confirm=buttons.stream().filter(b->{try{return attr(b,"role").equals("confirm");}catch(Exception ex){throw new RuntimeException(ex);}}).findFirst().orElseThrow();click(s,(Integer)attr(confirm,"x"),(Integer)attr(confirm,"y"),0,0);check(Minecraft.getInstance().gameMode.clicks.getLast().slot()==51,"confirmation sends original native slot");
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);resources=Path.of(args[1]);
        for(String theme:List.of("dark","classic"))for(int[] size:new int[][]{{829,563},{520,340},{320,240},{320,180}}){
            for(String scenario:List.of("builds","market","orders"))nativeChecks(theme,size[0],size[1],scenario);
            for(String id:List.of("hub","town","economy","adventures","society","development","security","management","profile"))menuChecks(theme,size[0],size[1],resources.resolve("menus/"+id+".json"));
            compactConfirmation(theme,size[0],size[1]);
        }
        System.out.println("PASS "+passed+" minimal/native renderer checks; "+renders+" renders; explicit platform/inventory/transport doubles, no game runtime");
    }
}
