package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static cc.crafto.ui.Protocol.*;
import static cc.crafto.ui.client.PanelProbe.*;

/** Production dashboard, explicit platform/transport doubles, synthetic finances. */
public class FinanceProbe {
    static int passed,renders;
    static void verify(boolean value,String message){if(!value)throw new AssertionError(message);passed++;}
    static JsonObject payload(CivicScreen s,String state)throws Exception{
        var p=RecentMenusProbe.payload(s,"city",0);boolean unavailable=state.equals("missing")||state.equals("denied");
        String tone=switch(state){case "surplus"->"positive";case "deficit"->"negative";case "warning"->"warning";default->"neutral";};
        String status=switch(state){case "surplus"->"Казна в плюсе";case "deficit"->"Казна в минусе";case "warning"->"Нужна сверка";case "flat"->"Без изменения";default->"Нет данных";};
        var metrics=List.of(
                object("label","Расходы города","value",unavailable?"—":"12 500,00","hint",unavailable?"Нет данных":"Списано за эту неделю","detail","Все записанные списания городского счёта","icon","expense","tone","neutral"),
                object("label","Поступления","value",unavailable?"—":state.equals("deficit")?"6 250,00":state.equals("flat")?"12 500,00":"18 750,00","hint",unavailable?"Нет данных":"Включая взносы и возвраты","icon","income","tone","neutral"),
                object("label","Итог за неделю","value",unavailable?"—":state.equals("deficit")?"-6 250,00":state.equals("flat")?"0,00":"+6 250,00","hint",status,"detail",state.equals("warning")?"Есть неучтённое изменение баланса; требуется сверка":"Поступления минус расходы. Не прогноз доходности.","icon","treasury","tone",tone));
        p.getAsJsonObject("city").add("finance",object("known",!unavailable,"period","Неделя с 26.10 · UTC","metrics",metrics,"action",state.equals("denied")?"":"cf","reason",state.equals("denied")?"Нужно право просмотра казны":""));
        return p;
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]),themes=Path.of(args[1]);
        for(String themeName:List.of("dark","classic"))for(int[] size:new int[][]{{829,563},{520,340},{320,240},{320,180}})for(String state:List.of("surplus","deficit","flat","warning","missing","denied")){
            var theme=JsonParser.parseString(Files.readString(themes.resolve(themeName+".json"))).getAsJsonObject();var c=client(theme);var s=new CivicScreen(c,null,"city");size(s,size[0],size[1]);
            for(int i=0;i<4;i++){s.receive(payload(s,state));draw(s);if(!(Boolean)get(s,"busy"))break;}
            verify(!(Boolean)get(s,"busy"),"finance capacity negotiation settles");
            var layout=(CityDashboardLayout)get(s,"cityLayout");var frame=(CivicPageLayout)get(s,"pageLayout");
            verify(layout.financeCount()==3,"three finance metrics");verify(frame.width()<=640&&frame.height()<=400,"dialog remains compact");
            var cards=s.widgets.stream().filter(b->b.label.startsWith("Расходы города.")||b.label.startsWith("Поступления.")||b.label.startsWith("Итог за неделю.")).toList();
            verify(cards.size()==3,"financial labels are distinct");
            int before=c.sent.size();
            for(var card:cards){s.setFocused(card);verify(card.y>=(Integer)get(s,"bodyTop")&&card.y+card.h<=(Integer)get(s,"bottom"),"keyboard focus reveals full financial card");verify(!card.isMouseOver(card.x+2,(Integer)get(s,"bottom")+1),"financial cards cannot capture footer clicks");}
            verify(c.sent.size()==before,"focus scroll does not send action");
            var result=cards.getLast();verify(result.tooltip!=null,"full monetary value and explanation available in tooltip");
            verify(cards.stream().allMatch(b->b.active!=state.equals("denied")),"permission restrictions applied");
            s.setFocused(cards.getFirst());
            var image=new BufferedImage(s.width*2,s.height*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.scale(2,2);{var graphicsFrame=new GuiGraphicsExtractor(g);s.extractBackground(graphicsFrame,-1,-1,0);s.extractRenderState(graphicsFrame,-1,-1,0);}g.dispose();
            verify((image.getRGB(0,0)>>>24)<255,"world remains visible");
            ImageIO.write(image,"PNG",out.resolve("finance-"+themeName+"-"+state+"-"+size[0]+"x"+size[1]+".png").toFile());renders++;
            if(!state.equals("denied")){
                result.press.run();verify(c.sent.size()==before+1&&str(c.sent.getLast(),"action","").equals("cf")&&str(c.sent.getLast(),"token","").equals("recent-city"),"financial card opens server-issued treasury action");
                result.press.run();verify(c.sent.size()==before+1,"busy state blocks duplicate action");
            }
            int positive=CivicStyle.financeInk(theme,"positive"),negative=CivicStyle.financeInk(theme,"negative"),warning=CivicStyle.financeInk(theme,"warning");verify(positive!=negative&&positive!=warning&&negative!=warning,"result states have distinct theme-aware colors");
            var custom=theme.deepCopy();custom.addProperty("positive","#FF123456");verify(CivicStyle.financeInk(custom,"positive")==0xFF123456,"resource/server theme can override positive color");
        }
        System.out.println("PASS "+passed+" finance UI checks; "+renders+" production-screen renders with synthetic data; no game runtime");
    }
}
