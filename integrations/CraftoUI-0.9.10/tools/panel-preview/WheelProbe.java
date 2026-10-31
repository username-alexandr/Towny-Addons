package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static cc.crafto.ui.Protocol.*;
import static cc.crafto.ui.client.PanelProbe.*;

/** Regression for a real command catalogue with more than one server page. */
public class WheelProbe {
    static int passed;
    static void verify(boolean ok,String message){if(!ok)throw new AssertionError(message);passed++;System.out.println("PASS "+message);}
    static final List<String> LABELS=List.of("Достижения города","Археология","Армия","Постройки","Лагерь","История города","Гражданство","Компании","Заказы и городские контракты","Панель администратора","Министры города","Правопорядок");
    static void answer(CommandScreen s,CraftoUIClient c,int page)throws Exception{
        int capacity=integer(c.sent.getLast(),"size",6),count=41;
        var entries=new JsonArray();
        for(int i=page*capacity;i<Math.min(count,(page+1)*capacity);i++)entries.add(object("id","module-"+i,"label",LABELS.get(i%LABELS.size()),"type","module"));
        s.receive(object("op","list","request",get(s,"awaited"),"token","wheel-token","module","","moduleLabel","","entries",entries,"count",count,"page",page,"pages",(count+capacity-1)/capacity));draw(s);
    }
    static CommandScreen catalog(CraftoUIClient c,int w,int h)throws Exception{
        var s=new CommandScreen(c,parent(c),"");size(s,w,h);answer(s,c,0);return s;
    }
    static void wheel(PanelScreen s,double delta){s.mouseScrolled(s.left+12,s.bodyTop+12,0,delta);}
    static void atBottom(PanelScreen s){s.scrollTo(Integer.MAX_VALUE);}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]),themes=Path.of(args[1]);
        for(String themeName:List.of("dark","classic"))for(int[] size:new int[][]{{829,563},{871,447},{520,340},{320,240},{320,180}}){
            var theme=JsonParser.parseString(Files.readString(themes.resolve(themeName+".json"))).getAsJsonObject();
            var c=client(theme);var s=catalog(c,size[0],size[1]);String label=themeName+" "+size[0]+"x"+size[1];
            verify(s.layout.x()>0&&s.layout.y()>0&&s.layout.width()<=640&&s.layout.height()<=400,label+" centered bounded window");
            int before=c.sent.size();wheel(s,1);
            s.mouseScrolled(s.left+10,s.bodyTop+10,1,0);s.mouseScrolled(0,0,0,-1);s.mouseScrolled(s.left+10,s.bodyTop+10,0,Double.NaN);
            verify(c.sent.size()==before,label+" first page, outside and horizontal wheel send nothing");
            atBottom(s);before=c.sent.size();wheel(s,-1);
            verify(c.sent.size()==before+1&&str(c.sent.getLast(),"op","").equals("list")&&integer(c.sent.getLast(),"page",-1)==1&&str(c.sent.getLast(),"token","").equals("wheel-token"),label+" wheel at body end requests next server page");
            wheel(s,-1);draw(s);wheel(s,-1);
            verify(c.sent.size()==before+1,label+" busy and dirty catalogue cannot skip pending pages");
            answer(s,c,1);s.scrollTo(0);before=c.sent.size();wheel(s,1);
            verify(c.sent.size()==before+1&&integer(c.sent.getLast(),"page",-1)==0,label+" wheel up requests previous page");
            answer(s,c,0);atBottom(s);before=c.sent.size();wheel(s,-.4);wheel(s,-.4);
            verify(c.sent.size()==before,label+" fractional deltas wait for a full step");wheel(s,-.4);
            verify(c.sent.size()==before+1,label+" accumulated trackpad step changes one page");
            int last=(Integer)get(s,"pages")-1;answer(s,c,last);atBottom(s);before=c.sent.size();wheel(s,-1);wheel(s,-6);
            verify(c.sent.size()==before&&!widget(s,"›").active,label+" last page is bounded without repeated requests");
            answer(s,c,0);before=c.sent.size();s.scrollTo(0);wheel(s,-1);
            if(s.contentHeight>s.bottom-s.bodyTop)verify(s.scroll>0&&c.sent.size()==before,label+" overflow scrolls before changing pages");
            else verify(c.sent.size()==before+1,label+" fitting catalogue turns page directly");
            answer(s,c,0);
            BufferedImage img=new BufferedImage(s.width*2,s.height*2,BufferedImage.TYPE_INT_ARGB);var graphics=img.createGraphics();graphics.scale(2,2);{var frame=new GuiGraphicsExtractor(graphics);s.extractBackground(frame,-1,-1,0);s.extractRenderState(frame,-1,-1,0);}graphics.dispose();
            verify((img.getRGB(0,0)>>>24)<255&&(img.getRGB((s.layout.x()+10)*2,(s.layout.y()+10)*2)>>>24)==255,label+" translucent world shade and opaque dialog");
            if(size[0]==829||size[1]==180)ImageIO.write(img,"PNG",out.resolve("wheel-"+themeName+"-commands-"+size[0]+"x"+size[1]+".png").toFile());
        }
        var theme=JsonParser.parseString(Files.readString(themes.resolve("dark.json"))).getAsJsonObject();
        var c=client(theme);var search=(PersonalScreen)setup(c,829,563,"search");int before=c.sent.size();atBottom(search);wheel(search,-1);draw(search);
        verify((Integer)get(search,"page")==1&&c.sent.size()==before,"personal search changes local page without a network request");
        search.scrollTo(0);wheel(search,1);draw(search);verify((Integer)get(search,"page")==0,"personal search returns to previous page");
        var command=catalog(c,829,563);command.setFocused(widget(command,"← Назад"));before=c.sent.size();command.keyPressed(new KeyEvent(267));
        verify(c.sent.size()==before+1&&integer(c.sent.getLast(),"page",-1)==1,"Page Down changes catalogue page when no editor is focused");
        command=commands(c,320,180,"command-review");before=c.sent.size();atBottom(command);wheel(command,-1);
        verify(c.sent.size()==before&&get(command,"stage").equals("review"),"wheel cannot submit or leave confirmation");
        command=catalog(c,829,563);((EditBox)get(command,"input")).setValue("город");before=c.sent.size();wheel(command,-1);
        verify(c.sent.size()==before,"pending search cannot paginate stale results");
        var orders=civic(c,320,180,"orders");int left=(Integer)get(orders,"left"),top=(Integer)get(orders,"bodyTop");before=c.sent.size();orders.mouseScrolled(left+10,top+10,0,-1);
        verify(c.sent.size()==before&&(Integer)get(orders,"pageScroll")>0,"civic order body scrolls locally first");
        orders.setFocused(orders.widgets.stream().filter(b->b instanceof OrderCardButton).reduce((a,b)->b).orElseThrow());
        for(int i=0;i<100&&c.sent.size()==before;i++)orders.mouseScrolled(left+10,top+10,0,-1);
        verify(c.sent.size()==before+1&&str(c.sent.getLast(),"op","").equals("page")&&integer(c.sent.getLast(),"page",-1)==1,"civic orders reach the next server page at scroll boundary");
        orders.mouseScrolled(left+10,top+10,0,-1);verify(c.sent.size()==before+1,"civic pending page ignores repeated wheel input");
        orders=civic(c,829,563,"orders");before=c.sent.size();orders.mouseScrolled(0,0,0,-1);orders.mouseScrolled((Integer)get(orders,"left")+10,(Integer)get(orders,"bodyTop")+10,1,0);
        verify(c.sent.size()==before,"civic outside and horizontal wheel do not turn pages");
        set(orders,"page",1);set(orders,"pageScroll",Integer.MAX_VALUE);orders.rebuildWidgets();before=c.sent.size();orders.mouseScrolled((Integer)get(orders,"left")+10,(Integer)get(orders,"bodyTop")+10,0,-1);
        verify(c.sent.size()==before,"civic last page does not request itself");
        System.out.println("PASS "+passed+" dialog/wheel regression checks with explicit platform/transport doubles; no game runtime");
    }
}
