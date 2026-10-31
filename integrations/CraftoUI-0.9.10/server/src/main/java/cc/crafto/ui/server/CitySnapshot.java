package cc.crafto.ui.server;
import cc.crafto.ui.Protocol;
import com.google.gson.JsonObject;
import java.util.*;
record CitySnapshot(String townId,String name,String status,String treasury,int claimed,int limit,int remaining,int residents,
                    List<Row> builds,List<Row> resources,List<Row> contracts,Map<String,Integer> levels,Map<String,String> names,
                    boolean buildsKnown,boolean contractsKnown,double pendingReward,long observedAt) {
    record Row(String text,String targetKey) {Row(String text){this(text,"");}}
    CitySnapshot{builds=List.copyOf(builds);resources=List.copyOf(resources);contracts=List.copyOf(contracts);levels=Map.copyOf(levels);names=Map.copyOf(names);}
    JsonObject json(){return Protocol.object("name",name,"status",status,"treasury",treasury,"claimed",claimed,"limit",limit,"remaining",remaining,"residents",residents,
        "builds",builds,"resources",resources,"contracts",contracts,"pendingReward",pendingReward,"contractsKnown",contractsKnown,"observedAt",observedAt);}
    static List<String> changes(CitySnapshot before,CitySnapshot after){
        List<String> out=new ArrayList<>();
        if(before==null)out.add("Первый вход: история начнётся с этого посещения.");
        else if(!before.townId.equals(after.townId))out.add(after.townId.isEmpty()?"Вы больше не состоите в городе.":"Ваш город теперь: "+after.name);
        else if(!after.townId.isEmpty()){
            if(!before.name.equals(after.name))out.add("Город переименован: "+before.name+" → "+after.name);
            if(before.claimed!=after.claimed)out.add("Занятые участки: "+before.claimed+" → "+after.claimed);
            if(before.remaining!=after.remaining)out.add("Можно захватить: "+number(before.remaining)+" → "+number(after.remaining));
            if(before.residents!=after.residents)out.add("Жители: "+before.residents+" → "+after.residents);
            if(before.buildsKnown&&after.buildsKnown)for(var e:new TreeMap<>(after.levels).entrySet())if(e.getValue()>before.levels.getOrDefault(e.getKey(),0))out.add("Завершена постройка: "+after.names.getOrDefault(e.getKey(),e.getKey())+", уровень "+e.getValue());
        }
        if(after.contractsKnown&&after.pendingReward>0)out.add(String.format(Locale.ROOT,"Доступна выплата за городские задания: %.2f",after.pendingReward));
        if(out.isEmpty())out.add("Изменений города с прошлого посещения не обнаружено.");
        return List.copyOf(out.subList(0,Math.min(out.size(),32)));
    }
    static String number(int value){return value<0?"без лимита":Integer.toString(value);}
    static String clean(String value){String s=value.replaceAll("(?i)(?:[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx])","").replaceAll("[\\p{Cntrl}]"," ").strip();return s.substring(0,Math.min(160,s.length()));}
}
