package cc.crafto.ui.server;
import java.util.*;
import com.google.gson.*;
import static cc.crafto.ui.Protocol.*;
/** Immutable data-only pages; actions never contain client-supplied commands. */
final class CivicView {
    record Action(String kind,String id,String extra){Action(String kind,String id){this(kind,id,"");}}
    record Row(String title,String detail,String targetKey,Action action,String reason,String rarity,String reward,String kind,String icon){
        Row(String title,String detail,String targetKey,Action action,String reason){this(title,detail,targetKey,action,reason,"","","","");}
        Row(String title,String detail,Action action){this(title,detail,"",action,"");}
        static Row info(String title,String detail){return new Row(title,detail,null);}
        Row order(String rarity,String reward,String kind){return order(rarity,reward,kind,"");}
        Row order(String rarity,String reward,String kind,String type){var style=cc.crafto.ui.OrderRarityStyle.parse(rarity);return new Row(title,detail,targetKey,action,reason,rarity,reward,kind,style==null?"":style.icon(type));}
    }
    String title="Город";String route="city",id="",notice="";int page,pages=1;boolean document;
    final List<Row> rows=new ArrayList<>();final List<String> lines=new ArrayList<>();JsonObject map;
    JsonObject city;
    JsonArray tree;
    final Map<String,Action> actionsForTree=new LinkedHashMap<>();
    Row financeLink;
    final Map<Integer,Row> metricLinks=new HashMap<>();
    final List<Row> shortcuts=new ArrayList<>();
    // Identity keys remain stable when pending confirmations are inserted before city entries.
    final Map<Row,JsonObject> presentation=new IdentityHashMap<>();
    private JsonObject node(Row r,String key,Map<String,Action> actions){
        boolean enabled=r.action()!=null&&r.reason().isBlank();
        if(enabled)actions.put(key,r.action());
        JsonObject node=object("title",r.title(),"detail",r.detail(),"targetKey",r.targetKey(),"action",enabled?key:"","reason",r.reason());
        if(city!=null){node.addProperty("title",bounded(r.title(),240));node.addProperty("detail",bounded(r.detail(),400));node.addProperty("reason",bounded(r.reason(),240));}
        if(!r.rarity().isBlank()){node.addProperty("rarity",r.rarity());node.addProperty("reward",r.reward());node.addProperty("kind",r.kind());node.addProperty("icon",r.icon());}
        if(presentation.containsKey(r))node.add("presentation",presentation.get(r).deepCopy());
        return node;
    }
    private static String bounded(String value,int max){return value.codePointCount(0,value.length())<=max?value:value.substring(0,value.offsetByCodePoints(0,max-1))+"…";}
    JsonObject json(String token,Map<String,Action> actions,int requestedPage,int size){
        size=Math.clamp(size,1,12);
        var list=new JsonArray();pages=Math.max(1,(rows.size()+size-1)/size);page=Math.clamp(requestedPage,0,pages-1);
        for(int i=page*size;i<Math.min(rows.size(),(page+1)*size);i++)list.add(node(rows.get(i),"a"+i,actions));
        JsonObject out=object("op","view","token",token,"title",title,"route",route,"id",id,"notice",notice,"document",document,"lines",lines,"rows",list,"page",page,"pages",pages);
        if(city!=null){var header=city.deepCopy();var links=new JsonArray();for(int i=0;i<shortcuts.size();i++)links.add(node(shortcuts.get(i),"c"+i,actions));header.add("shortcuts",links);
            for(var entry:metricLinks.entrySet()){var link=node(entry.getValue(),"cm"+entry.getKey(),actions);var metric=header.getAsJsonArray("metrics").get(entry.getKey()).getAsJsonObject();metric.add("action",link.get("action"));metric.add("reason",link.get("reason"));}
            if(financeLink!=null&&header.has("finance")){var link=node(financeLink,"cf",actions);var finance=header.getAsJsonObject("finance");finance.add("action",link.get("action"));finance.add("reason",link.get("reason"));}
            out.add("city",header);}
        if(tree!=null){out.add("tree",tree);actions.putAll(actionsForTree);}
        if(map!=null)out.add("map",map);return out;
    }
}
