package cc.crafto.ui;
import com.google.gson.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Opt-in declarative reflow. Original component/action IDs and the source configuration stay intact. */
public final class MenuReflow {
    private MenuReflow(){}
    private static final Set<String> NAVIGATION=Set.of("hub","main","town","economy","adventures","society","management","development","security");
    private static boolean standardNavigation(JsonObject source){
        return source.getAsJsonArray("components").asList().stream().map(JsonElement::getAsJsonObject).anyMatch(n->str(n,"type","").equals("grid")&&Set.of("modules","sections").contains(str(n,"id",""))&&n.has("children")&&n.getAsJsonArray("children").asList().stream().allMatch(e->str(e.getAsJsonObject(),"type","").equals("button")));
    }
    public static JsonObject fit(JsonObject source,int requestedWidth){
        return fit(source,requestedWidth,Integer.MAX_VALUE);
    }
    public static JsonObject fit(JsonObject source,int requestedWidth,int availableHeight){
        boolean navigation=NAVIGATION.contains(str(source,"id",""))&&standardNavigation(source)&&(!source.has("responsive")||bool(source,"responsive",false));
        boolean paginate=navigation||bool(source,"paginate",false)||str(source,"id","").equals("profile")&&bool(source,"responsive",false);
        boolean footerActions=paginate&&bool(source,"footerActions",str(source,"id","").equals("profile"));
        if(!bool(source,"responsive",false)&&!navigation)return source;
        var out=source.deepCopy();int maximum=integer(source,"width",620),width=Math.clamp(requestedWidth,Math.min(240,maximum),maximum);
        out.addProperty("width",width);var nodes=out.getAsJsonArray("components");int padding=16,asideBottom=58,contentBottom=58,asideWidth=0;
        int targetHeight=Math.clamp(availableHeight,150,400);
        if(navigation){
            JsonObject grid=null;var standalone=new ArrayList<JsonObject>();
            for(var value:nodes){var n=value.getAsJsonObject();String type=str(n,"type","");
                if(type.equals("grid")&&n.has("children")){n.addProperty("layoutRole","content");if(grid==null)grid=n;}
                else if(type.equals("panel")&&n.has("children")&&n.getAsJsonArray("children").asList().stream().anyMatch(e->str(e.getAsJsonObject(),"type","").equals("avatar"))&&n.getAsJsonArray("children").asList().stream().noneMatch(e->Set.of("button","grid","list").contains(str(e.getAsJsonObject(),"type","")))){n.addProperty("layoutRole","aside");}
                else if(type.equals("text")){n.addProperty("layoutRole","description");n.addProperty("y",57);n.addProperty("h",16);}
                else if(type.equals("button"))standalone.add(n);
            }
            // Legacy defaults kept navigation buttons below the oversized grid. Keep every
            // action ID, but paginate it with its peers instead of creating a scroll tail.
            if(grid!=null)for(var button:standalone){nodes.remove(button);grid.getAsJsonArray("children").add(button);}
        }
        boolean compact=width<480;var actions=new ArrayList<JsonObject>();
        for(var element:nodes){var n=element.getAsJsonObject();String role=str(n,"layoutRole","");
            if(role.equals("aside")){
                n.addProperty("x",padding);n.addProperty("y",58);
                if(availableHeight<220||paginate&&availableHeight<330){n.add("children",new JsonArray());n.addProperty("w",width-2*padding);n.addProperty("h",0);continue;}
                if(compact){
                    var texts=new ArrayList<JsonObject>();for(var child:n.getAsJsonArray("children"))if(str(child.getAsJsonObject(),"type","").equals("text"))texts.add(child.getAsJsonObject());
                    var children=new JsonArray();if(!texts.isEmpty()){
                        var label=texts.getFirst().deepCopy();String value=str(label,"text","");if(texts.size()>1)value+=" · "+str(texts.get(1),"text","");
                        label.addProperty("text",value);label.addProperty("x",10);label.addProperty("y",8);label.addProperty("w",width-2*padding-20);label.addProperty("h",28);label.addProperty("wrap",true);children.add(label);
                    }
                    n.add("children",children);n.addProperty("w",width-2*padding);n.addProperty("h",44);
                }else asideWidth=integer(n,"w",150);
                asideBottom=58+integer(n,"h",44);
            }
        }
        int x=compact||asideWidth==0?padding:padding+asideWidth+16,span=width-padding-x;
        for(var element:nodes){var n=element.getAsJsonObject();String role=str(n,"layoutRole","");
            if(role.equals("content")){
                int y=compact&&asideBottom>58?asideBottom+12:integer(n,"y",65);n.addProperty("x",x);n.addProperty("y",y);n.addProperty("w",span);
                int count=n.getAsJsonArray("children").size(),gap=integer(n,"gap",8),height=integer(n,"rowHeight",42);
                int minCell=Math.clamp(integer(n,"minCellWidth",130),96,400);
                int columns=str(n,"type","").equals("list")?1:Math.clamp((span+gap)/(minCell+gap),1,integer(n,"columns",3));
                if(str(source,"id","").equals("profile")&&availableHeight<260&&span>=260){n.addProperty("type","grid");columns=2;}
                if(navigation){
                    y=compact&&asideBottom>58?asideBottom+10:nodes.asList().stream().anyMatch(e->str(e.getAsJsonObject(),"layoutRole","").equals("description"))?80:65;
                    gap=6;height=Math.clamp(height,32,42);n.addProperty("y",y);n.addProperty("gap",gap);
                    int maxColumns=Math.max(1,Math.min(4,(span+gap)/(130+gap)));
                    while(columns<maxColumns&&y+((count+columns-1)/columns)*(height+gap)-gap+44>targetHeight)columns++;
                    height=Math.max(28,Math.min(height,targetHeight-44-y));n.addProperty("rowHeight",height);
                    int visibleRows=Math.max(1,(targetHeight-44-y+gap)/(height+gap)),capacity=visibleRows*columns;
                    n.addProperty("pageCapacity",capacity);out.addProperty("pageCount",Math.max(1,(count+capacity-1)/capacity));count=Math.min(count,capacity);
                }else if(paginate){
                    long actionCount=footerActions?0:nodes.asList().stream().filter(e->str(e.getAsJsonObject(),"layoutRole","").equals("actions")).count();
                    int actionColumns=Math.max(1,(span+8)/138),reserve=actionCount==0?0:12+((int)actionCount+actionColumns-1)/actionColumns*38-8;
                    int visibleRows=Math.max(1,(targetHeight-44-y-reserve+gap)/(height+gap)),capacity=visibleRows*columns;
                    n.addProperty("pageCapacity",capacity);out.addProperty("pageCount",Math.max(1,(count+capacity-1)/capacity));count=Math.min(count,capacity);
                }
                if(!str(n,"type","").equals("list"))n.addProperty("columns",columns);
                if(str(source,"id","").equals("help")){
                    int cell=(span-(columns-1)*gap)/columns;
                    for(var child:n.getAsJsonArray("children")){var card=child.getAsJsonObject();if(card.has("children"))for(var text:card.getAsJsonArray("children")){var label=text.getAsJsonObject();label.addProperty("w",Math.max(24,cell-integer(label,"x",0)-10));}}
                }
                int rows=(count+columns-1)/columns,h=Math.max(height,rows*height+Math.max(0,rows-1)*gap);n.addProperty("h",h);contentBottom=Math.max(contentBottom,y+h);
            }else if(role.equals("description")){n.addProperty("x",padding);n.addProperty("w",width-2*padding);}
            else if(role.equals("actions"))actions.add(n);
        }
        if(!actions.isEmpty()&&!footerActions){
            int columns=Math.max(1,Math.min(actions.size(),(span+8)/138)),w=(span-(columns-1)*8)/columns;
            for(int i=0;i<actions.size();i++){var n=actions.get(i);n.addProperty("x",x+i%columns*(w+8));n.addProperty("y",contentBottom+12+i/columns*38);n.addProperty("w",w);n.addProperty("h",30);}
            contentBottom+=12+((actions.size()+columns-1)/columns)*38-8;
        }
        int height=Math.max(paginate?150:180,Math.max(contentBottom,asideBottom)+44);out.addProperty("height",height);
        if(footerActions&&!actions.isEmpty()){
            int space=width-32-(integer(out,"pageCount",1)>1?122:0),w=Math.min(150,(space-(actions.size()-1)*6)/actions.size());
            for(int i=0;i<actions.size();i++){var n=actions.get(i);n.addProperty("layoutRole","footer");n.addProperty("x",16+i*(w+6));n.addProperty("y",height-31);n.addProperty("w",w);n.addProperty("h",22);}
        }
        return out;
    }
}
