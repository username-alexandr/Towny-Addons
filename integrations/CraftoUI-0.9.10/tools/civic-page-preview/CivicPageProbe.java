package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.*;
import java.awt.image.*;
import java.awt.RenderingHints;
import javax.imageio.*;
import java.nio.file.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;
import static cc.crafto.ui.client.CivicLifecycleProbe.*;

/** Synthetic data, real production screen/layout/widgets. Game/platform behaviour is explicitly doubled. */
public class CivicPageProbe {
    static int verified,renders;
    static void verify(boolean b,String message){if(!b)throw new AssertionError(message);verified++;System.out.println("PASS "+message);}
    static JsonObject row(String title,String detail,String action){return object("title",title,"detail",detail,"action",action,"reason","","targetKey","");}
    static JsonObject page(CivicScreen s,String scenario)throws Exception{
        String route=switch(scenario){case "empty","invites","long","error","townless"->"inbox";case "blocked"->"invite-company";case "history"->"history";case "materials"->"materials";case "planner","planner-supply","planner-effects","procurement-requests","procurement-order"->scenario;case "goals"->"goals";case "review"->"invite-town";default->"inbox";};
        var b=view(s,route,false);b.addProperty("title",CivicStyle.title(route));var rows=new JsonArray();b.add("rows",rows);
        switch(scenario){
            case "empty","townless"->{b.add("lines",JSON.toJsonTree(List.of(scenario.equals("townless")?"Без города":"Житель")));rows.add(row("Приглашений нет","Новые приглашения появятся здесь.",""));}
            case "invites"->{b.add("lines",JSON.toJsonTree(List.of("Житель")));rows.add(row("Город «NeverLand»","Приглашение вступить","invite-town"));rows.add(row("Компания «Северная артель»","Истекает через 42 мин.","invite-company"));}
            case "blocked"->{b.add("lines",JSON.toJsonTree(List.of("Северная артель")));var r=row("Принять","Вступить сотрудником","accept");r.addProperty("reason","Сначала вступите в город компании");rows.add(r);rows.add(row("Отклонить","Убрать это приглашение","decline"));}
            case "history"->{b.add("lines",JSON.toJsonTree(List.of("Новые события сверху · время UTC")));rows.add(row("Обновлён городской совет","25.10.2026, 20:14 · Александр","history-1"));rows.add(row("Расширение границ города","25.10.2026, 19:28 · Новых участков: 4","history-2"));rows.add(row("Завершено строительство ратуши","25.10.2026, 18:56 · Уровень 3","history-3"));}
            case "materials"->{b.add("lines",JSON.toJsonTree(List.of("Осталось установить · на складе · у вас · добыть для установки. Ресурсы здесь не резервируются.","Материал возвращается после установки: нужен один предмет каждого типа.")));rows.add(row("Дубовые доски","Установить: 640 · склад: 32 · у вас: 16 · добыть: 0",""));rows.add(row("Каменные кирпичи","Установить: 128 · склад: 0 · у вас: 0 · добыть: 1",""));}
            case "goals"->{b.add("lines",JSON.toJsonTree(List.of("Личный вклад: подходящие блоки, установленные после закрепления и сохранившиеся в чертеже.")));rows.add(row("Ратуша · этап 3","Общий прогресс 640 / 1000 · ваш вклад 32","build"));var r=row("Снять закрепление","Стройка и ресурсы сохранятся","unpin");r.addProperty("reason","Нужно право управления целями");rows.add(r);}
            case "planner"->{b.add("lines",JSON.toJsonTree(List.of("Личный расчёт · город NeverLand")));rows.add(row("Добавить здание","До восьми целей; зависимости учитываются один раз","catalog"));rows.add(row("Стоимость строительства","12 450,00 монет",""));rows.add(row("Материалы и снабжение","Общая цепочка, фонд и склад","supply"));rows.add(row("Последствия для города","Участки, содержание и энергоснабжение","effects"));}
            case "planner-supply"->{b.add("lines",JSON.toJsonTree(List.of("Фонд и склад учтены по одному разу.")));rows.add(row("Каменные кирпичи","Нужно: 640 · сдано: 64 · склад: 128 · собрать: 448",""));rows.add(row("Дубовые доски","Нужно: 128 · сдано: 0 · склад: 32 · собрать: 96",""));rows.add(row("Закупки города","Опубликованные заявки, оплата и возвраты","procurement"));}
            case "planner-effects"->{b.add("lines",JSON.toJsonTree(List.of("Эффекты включатся после завершения и запуска зданий.")));rows.add(row("Ратуша · уровень 3","Дополнительных участков: 8 · цена: 12 450,00",""));rows.add(row("Содержание города","Текущий расход: 200,00 · после стройки: 250,00",""));}
            case "procurement-requests"->{rows.add(row("Каменные кирпичи","Осталось: 448 · награда: 0,10 за предмет · опубликована","order"));rows.add(row("Дубовые доски","Ожидает подтверждения казны · резерв сохранён","order2"));}
            case "procurement-order"->{b.add("lines",JSON.toJsonTree(List.of("Каменные кирпичи · уровень 3","Нужно: 448 · сдано: 64","Оплата подтверждается через городскую казну.")));rows.add(row("Опубликовать заявку","Условия появятся перед подтверждением","publish"));rows.add(row("Отменить и вернуть резерв","Возврат только неиспользованной суммы","cancel"));}
            case "long"->{b.add("lines",JSON.toJsonTree(List.of("Глава подразделения с очень длинным названием · участник нескольких компаний · дополнительная информация от сервера")));for(int i=0;i<12;i++)rows.add(row("Компания «Очень длинное название торгового объединения №"+i+"»","Приглашение с дополнительными условиями и длинным пояснением, которое должно переноситься и быть доступным через подсказку.","a"+i));b.addProperty("pages",3);}
            case "review"->{b.addProperty("op","review");b.addProperty("title","Вступить в город NeverLand?");b.addProperty("confirmation","server-ticket");b.add("lines",JSON.toJsonTree(List.of("Действие: принять приглашение в город.","Город: NeverLand","Вы получите статус жителя.","Подтверждайте только если согласны с условиями.")));}
            case "error"->{b.addProperty("op","error");b.addProperty("message","Нет связи с источником приглашений. Попробуйте обновить экран.");}
        }return b;
    }
    static CivicScreen setup(CraftoUIClient c,int w,int h,String scenario)throws Exception{
        c.townMember=!scenario.equals("townless");var s=new CivicScreen(c,null,"inbox");s.width=w;s.height=h;s.initialize();
        // Mimic the server responding to follow-up capacity changes, without assigning client-side tokens.
        for(int i=0;i<3;i++){s.receive(page(s,scenario));draw(s);if(!(Boolean)get(s,"busy"))break;}
        return s;
    }
    static void render(CivicScreen s,Path path)throws Exception{
        var image=new BufferedImage(s.width*2,s.height*2,BufferedImage.TYPE_INT_ARGB);var g2=image.createGraphics();g2.scale(2,2);g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        {var frame=new GuiGraphicsExtractor(g2);s.extractBackground(frame,-1,-1,0);s.extractRenderState(frame,-1,-1,0);}g2.dispose();ImageIO.write(image,"PNG",path.toFile());renders++;
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]),themes=Path.of(args[1]);
        for(String theme:List.of("dark","classic")){
            var c=new CraftoUIClient();c.lastTheme=JsonParser.parseString(Files.readString(themes.resolve(theme+".json"))).getAsJsonObject();
            for(int[] size:new int[][]{{871,447},{520,340},{320,240},{320,180}}){
                for(String scenario:List.of("empty","invites","blocked","history","materials","goals","planner","planner-supply","planner-effects","procurement-requests","procurement-order","review","long","townless","error")){
                    var s=setup(c,size[0],size[1],scenario);render(s,out.resolve(theme+"-"+scenario+"-"+size[0]+"x"+size[1]+".png"));
                    var layout=(CivicPageLayout)get(s,"pageLayout");
                    verify(layout!=null&&s.widgets.stream().filter(b->b.label.equals("← Назад")||b.label.equals("В игру")||b.label.equals("Обновить")).allMatch(b->b.y>=layout.bottom()&&b.y+b.h<=s.height),theme+" "+scenario+" "+size[0]+"x"+size[1]+" footer in bounds");
                }
            }
        }
        var c=new CraftoUIClient();c.lastTheme=JsonParser.parseString(Files.readString(themes.resolve("dark.json"))).getAsJsonObject();
        var s=setup(c,871,447,"empty");verify(s.widgets.stream().noneMatch(b->b.label.startsWith("Приглашений нет")),"empty state is not a fake disabled button");
        s=setup(c,871,447,"invites");var invitation=s.widgets.stream().filter(b->b.label.startsWith("Город «")).findFirst().orElseThrow();int sent=c.sent.size();s.mouseClicked(new MouseButtonEvent(invitation.x+4,invitation.y+4),false);verify(c.sent.size()==sent+1&&str(c.sent.getLast(),"action","").equals("invite-town"),"invitation click sends only its server action id");
        sent=c.sent.size();s.mouseClicked(new MouseButtonEvent(invitation.x+4,invitation.y+4),false);verify(c.sent.size()==sent,"second click cannot execute stale widgets");
        s=setup(c,871,447,"blocked");var blocked=s.widgets.stream().filter(b->b.label.startsWith("Принять")).findFirst().orElseThrow();sent=c.sent.size();s.mouseClicked(new MouseButtonEvent(blocked.x+4,blocked.y+4),false);verify(c.sent.size()==sent&&get(s,"notice").equals("Сначала вступите в город компании"),"blocked action explains reason and sends no request");
        s=setup(c,320,180,"long");var layout=(CivicPageLayout)get(s,"pageLayout");sent=c.sent.size();s.mouseScrolled(layout.left()+5,layout.bodyTop()+5,0,-1);verify((Integer)get(s,"pageScroll")>0&&c.sent.size()==sent,"wheel scrolls local content without server polling");
        var last=s.widgets.stream().filter(b->b.label.startsWith("Компания «")).reduce((a,b)->b).orElseThrow();s.setFocused(last);verify(last.y+last.h<=layout.bottom(),"keyboard focus brings last card into viewport");
        verify(!last.isMouseOver(last.x+3,layout.bottom()+1),"clipped body cannot steal footer clicks");render(s,out.resolve("dark-long-320x180-scrolled.png"));
        s.receive(page(s,"empty"));draw(s);verify((Integer)get(s,"pageScroll")<=Math.max(0,(Integer)get(s,"pageContent")-layout.viewport()),"shorter response clamps old scroll position");
        s=setup(c,520,340,"review");sent=c.sent.size();var confirm=s.widgets.stream().filter(b->b.label.equals("Подтвердить")).findFirst().orElseThrow();s.mouseClicked(new MouseButtonEvent(confirm.x+2,confirm.y+2),false);verify(c.sent.size()==sent+1&&str(c.sent.getLast(),"confirmation","").equals("server-ticket"),"confirmation submits server-issued ticket");
        s=setup(c,871,447,"townless");verify(s.widgets.stream().noneMatch(b->b.label.equals("Город")||b.label.equals("Материалы")||b.label.equals("Карта")||b.label.equals("Цели")),"townless player sees only personal tabs");
        s=setup(c,871,447,"materials");verify(s.widgets.stream().filter(b->b.label.startsWith("Дубовые")||b.label.startsWith("Каменные")).noneMatch(b->b.active),"material information has no clickable affordance");
        s=setup(c,871,447,"invites");verify(s.widgets.stream().filter(b->b.label.startsWith("Город «")).allMatch(b->b.tooltip==null),"short card does not produce redundant hover text");
        s=setup(c,320,180,"long");verify(s.widgets.stream().filter(b->b.label.startsWith("Компания «")).allMatch(b->b.tooltip!=null),"clipped long text remains available in tooltip");
        s=setup(c,871,447,"invites");set(s,"serverBack",true);sent=c.sent.size();var back=s.widgets.stream().filter(b->b.label.equals("← Назад")).findFirst().orElseThrow();s.mouseClicked(new MouseButtonEvent(back.x+2,back.y+2),false);verify(c.sent.size()==sent+1&&str(c.sent.getLast(),"op","").equals("back"),"back requests the authoritative previous menu");
        s=setup(c,871,447,"goals");var emptyGoal=view(s,"goals",false);emptyGoal.add("lines",JSON.toJsonTree(List.of("Цель не выбрана. Мэр может закрепить стройку или городской контракт.")));s.receive(emptyGoal);draw(s);verify(((java.util.List<?>)get(s,"pagePanels")).size()==1,"empty goal has one explanation panel instead of duplicated summaries");
        System.out.println("PASS "+verified+" civic-page checks, "+renders+" full-screen AWT previews; synthetic data, no game runtime");
    }
}
