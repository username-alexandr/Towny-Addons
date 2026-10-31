package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static cc.crafto.ui.Protocol.*;
import static cc.crafto.ui.client.PanelProbe.*;

/** New civic screens, including the actual dashboard renderer, with explicit transport/platform doubles. */
public class RecentMenusProbe {
    static int passed,renders;
    static void verify(boolean ok,String message){if(!ok)throw new AssertionError(message);passed++;System.out.println("PASS "+message);}
    static JsonObject row(String title,String detail,String action){return object("title",title,"detail",detail,"action",action);}
    static JsonObject payload(CivicScreen s,String scenario,int page)throws Exception{
        String route=switch(scenario){case "review"->"invite-town";case "document"->"history-entry";default->scenario;};
        var rows=new JsonArray();var body=object("op","view","request",get(s,"awaited"),"token","recent-"+scenario,"route",route,"title",scenario.equals("city")?"NeverLand":CivicStyle.title(route),"id","","rows",rows,"lines",List.of(),"page",page,"pages",1,"notice","");
        switch(scenario){
            case "city"->{
                var metrics=List.of(object("label","Казна","value","128 540","hint","Баланс города","icon","treasury"),object("label","Жители","value","24","hint","В сети: 8","icon","residents"),object("label","Занято участков","value","40","hint","Лимит: 68","icon","map"),object("label","Можно захватить","value","28","hint","По лимиту города","icon","claim"));
                var shortcuts=new JsonArray();String[] titles={"Постройки","План развития","Материалы","Общая цель","Карта участков","Казна","История города","Приглашения"},icons={"build","planner","materials","goal","map","treasury","history","inbox"};
                for(int i=0;i<titles.length;i++)shortcuts.add(object("title",titles[i],"action","shortcut-"+i,"presentation",object("icon",icons[i])));
                body.add("city",object("role","Мэр","developmentCount",4,"metrics",metrics,"shortcuts",shortcuts,"section","active","buildsKnown",true,"contractsKnown",true,"activeCount",6,"buildCount",4,"taskCount",2));
                for(int i=0;i<2;i++)rows.add(object("title",i==0?"Ратуша · этап 3":"Собрать древесину","detail",i==0?"Установлено: 640 / 1000":"Прогресс: 96 / 128 · ваш вклад: 32","action","city-"+page+"-"+i,"presentation",object("icon",i==0?"build":"contract","category",i==0?"Строительство":"Задание города","status","В работе","done",i==0?640:96,"total",i==0?1000:128,"known",true)));
                body.addProperty("pages",3);
            }
            case "history"->{rows.add(row("Построена ратуша","Сегодня · уровень 3","history-one"));rows.add(row("Город расширен","Захвачено 4 участка","history-two"));body.addProperty("pages",2);}
            case "treasury"->{rows.add(row("Поступление в казну","Взнос жителя: 2 500","treasury-one"));rows.add(row("Расход на стройку","Улучшение ратуши: 1 000","treasury-two"));body.addProperty("pages",2);}
            case "materials"->{var r=row("Древесина","Осталось: 128 · склад: 32 · у игрока: 16","");r.addProperty("targetKey","block.minecraft.oak_log");rows.add(r);}
            case "inbox"->{rows.add(row("Город «Северный»","Приглашение вступить в город","invite-town"));rows.add(row("Компания «Северная артель»","Приглашение в компанию","invite-company"));}
            case "goals"->{rows.add(row("Ратуша · этап 3","Общий прогресс: 640 / 1000","goal-one"));rows.add(row("Снабжение города","Древесина: 96 / 128","goal-two"));}
            case "review"->{body.addProperty("op","review");body.addProperty("confirmation","recent-confirmation");body.addProperty("title","Принять приглашение?");body.add("lines",JSON.toJsonTree(List.of("Город: Северный","Вы получите статус жителя.","Проверьте город перед подтверждением.")));}
            case "document"->{body.addProperty("document",true);body.addProperty("title","Подробности события");var lines=new ArrayList<String>();for(int i=0;i<30;i++)lines.add("Запись "+(i+1)+": изменение города и сведения об участнике.");body.add("lines",JSON.toJsonTree(lines));}
        }
        body.add("rows",rows);return body;
    }
    static CivicScreen setup(CraftoUIClient c,int w,int h,String scenario)throws Exception{
        var s=new CivicScreen(c,null,scenario.equals("review")||scenario.equals("document")?"inbox":scenario);size(s,w,h);
        for(int i=0;i<4;i++){s.receive(payload(s,scenario,0));draw(s);if(!(Boolean)get(s,"busy"))break;}
        verify(!(Boolean)get(s,"busy"),scenario+" capacity negotiation settles");return s;
    }
    static void render(CivicScreen s,Path path)throws Exception{
        var image=new BufferedImage(s.width*2,s.height*2,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();graphics.scale(2,2);{var frame=new GuiGraphicsExtractor(graphics);s.extractBackground(frame,-1,-1,0);s.extractRenderState(frame,-1,-1,0);}graphics.dispose();
        verify((image.getRGB(0,0)>>>24)<255,"world backdrop remains translucent");ImageIO.write(image,"PNG",path.toFile());renders++;
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]),themes=Path.of(args[1]);
        for(String themeName:List.of("dark","classic"))for(int[] size:new int[][]{{829,563},{520,340},{320,240},{320,180}})for(String scenario:List.of("city","history","treasury","materials","inbox","goals","review","document")){
            var theme=JsonParser.parseString(Files.readString(themes.resolve(themeName+".json"))).getAsJsonObject();var c=client(theme);var s=setup(c,size[0],size[1],scenario);String label=themeName+" "+scenario+" "+size[0]+"x"+size[1];
            var frame=(CivicPageLayout)get(s,"pageLayout");
            verify(frame!=null&&frame.width()<=640&&frame.height()<=400&&frame.x()>0&&frame.y()>0,label+" uses compact shared dialog");
            var footers=s.widgets.stream().filter(b->List.of("← Назад","Обновить").contains(b.label)).toList();
            verify(footers.size()==2&&footers.stream().allMatch(b->b.x>=frame.x()&&b.x+b.w<=frame.x()+frame.width()&&b.y>=(Integer)unchecked(s,"bottom")&&b.y+b.h<=frame.y()+frame.height()),label+" fixed footer stays in dialog");
            render(s,out.resolve("recent-"+themeName+"-"+scenario+"-"+size[0]+"x"+size[1]+".png"));
            int left=(Integer)get(s,"left"),top=(Integer)get(s,"bodyTop"),bottom=(Integer)get(s,"bottom"),before=c.sent.size();
            s.mouseScrolled(0,0,0,-1);s.mouseScrolled(left+5,top+5,1,0);verify(c.sent.size()==before,label+" outside and horizontal wheel do nothing");
            if(scenario.equals("city")){
                verify(s.widgets.stream().filter(b->b.label.startsWith("История города.")).count()==1&&s.widgets.stream().filter(b->b.label.startsWith("Карта участков.")).count()==1,label+" keeps single city shortcuts");
                s.mouseScrolled(left+5,top+5,0,-1);verify((Integer)get(s,"cityScroll")>0&&c.sent.size()==before,label+" dashboard scrolls before requesting next page");
                s.keyPressed(new KeyEvent(269));render(s,out.resolve("recent-"+themeName+"-city-"+size[0]+"x"+size[1]+"-scrolled.png"));
                var card=s.widgets.stream().filter(b->b.label.startsWith("Ратуша")).findFirst().orElseThrow();verify(!card.isMouseOver(card.x+2,bottom+1),label+" clipped card cannot intercept footer");
                s.mouseScrolled(left+5,top+5,0,-1);verify(c.sent.size()==before+1&&integer(c.sent.getLast(),"page",-1)==1&&str(c.sent.getLast(),"token","").equals("recent-city"),label+" bottom wheel requests next city page");
                s.mouseScrolled(left+5,top+5,0,-1);verify(c.sent.size()==before+1,label+" city waits for pending response");
                s.receive(payload(s,scenario,1));draw(s);s.keyPressed(new KeyEvent(268));before=c.sent.size();s.keyPressed(new KeyEvent(266));verify(c.sent.size()==before+1&&integer(c.sent.getLast(),"page",-1)==0,label+" Page Up at top requests previous city page");
            }else if(scenario.equals("document")){
                before=c.sent.size();s.mouseScrolled(left+5,top+5,0,-1);verify((Integer)get(s,"textPage")==1&&c.sent.size()==before,label+" long details paginate locally");
            }else if(scenario.equals("history")||scenario.equals("treasury")){
                before=c.sent.size();s.keyPressed(new KeyEvent(269));s.keyPressed(new KeyEvent(267));verify(c.sent.size()==before+1&&integer(c.sent.getLast(),"page",-1)==1,label+" Page Down reaches next server page");
            }
        }
        System.out.println("PASS "+passed+" recent-menu checks; "+renders+" actual-screen AWT renders with synthetic data; no game runtime");
    }
    static Object unchecked(Object object,String name){try{return get(object,name);}catch(Exception e){throw new RuntimeException(e);}}
}
