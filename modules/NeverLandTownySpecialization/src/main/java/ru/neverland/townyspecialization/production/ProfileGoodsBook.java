package ru.neverland.townyspecialization.production;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** A funded reserve/offer has one immutable set of terms; received goods are never counted as local production. */
public final class ProfileGoodsBook {
    private ProfileGoodsBook(){}
    public record Terms(UUID id,UUID town,UUID other,String townName,String otherName,UUID world,String period,String title,Map<String,Long> give,
                        Map<String,Long> receive,Map<String,String> labels,UUID actor,String actorName,long created,long expires){
        public Terms{
            Objects.requireNonNull(id);Objects.requireNonNull(town);text(townName,100);if(other==null){if(!"".equals(otherName))throw new IllegalArgumentException("Имя отсутствующего города");}else text(otherName,100);Objects.requireNonNull(world);Objects.requireNonNull(actor);text(period,180);text(title,100);text(actorName,64);
            give=goods(give,false);receive=goods(receive,other==null);labels=Map.copyOf(labels);var keys=new HashSet<>(give.keySet());keys.addAll(receive.keySet());
            if(!keys.equals(labels.keySet()))throw new IllegalArgumentException("Не совпадают названия продукции в смете");labels.values().forEach(v->text(v,100));
            if(other==null&&!receive.isEmpty()||other!=null&&(other.equals(town)||!Collections.disjoint(give.keySet(),receive.keySet())))throw new IllegalArgumentException("Неверные стороны обмена");
            if(created<1||expires<=created||expires-created>(other==null?168L:24L)*3_600_000)throw new IllegalArgumentException("Неверный срок резерва или предложения");
        }
        public String kind(){return other==null?"PREPARATION":"OFFER";}
        public String hash(){try{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){for(String s:List.of(id.toString(),town.toString(),other==null?"":other.toString(),townName,otherName,world.toString(),period,title,actor.toString(),actorName))out.writeUTF(s);out.writeLong(created);out.writeLong(expires);for(var map:List.of(give,receive)){out.writeInt(map.size());for(var e:new TreeMap<>(map).entrySet()){out.writeUTF(e.getKey());out.writeLong(e.getValue());}}out.writeInt(labels.size());for(var e:new TreeMap<>(labels).entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}}return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
        public Map<String,Object> view(){var out=new LinkedHashMap<String,Object>();out.put("id",id.toString());out.put("town",town.toString());out.put("other",other==null?"":other.toString());out.put("townName",townName);out.put("otherName",otherName);out.put("world",world.toString());out.put("period",period);out.put("title",title);out.put("give",give);out.put("receive",receive);out.put("labels",labels);out.put("actor",actor.toString());out.put("actorName",actorName);out.put("created",created);out.put("expires",expires);out.put("kind",kind());out.put("hash",hash());return Map.copyOf(out);}
    }
    public record Entry(Terms terms,String state,UUID receipt,UUID actor,String actorName,String reason,long settled){
        public Entry{Objects.requireNonNull(terms);Objects.requireNonNull(actor);text(actorName,64);text(reason,300);if(!Set.of("HELD","SWAPPED","CANCELLED","EXPIRED").contains(state)||state.equals("HELD")!=(settled==0)||settled<0||state.equals("SWAPPED")!=(receipt!=null)||state.equals("SWAPPED")&&terms.other()==null)throw new IllegalArgumentException("Неверное состояние сезонного резерва");}
        public Map<String,Object> view(){var out=new LinkedHashMap<String,Object>(terms.view());out.put("state",state);out.put("receipt",receipt==null?"":receipt.toString());out.put("changedBy",actor.toString());out.put("changedByName",actorName);out.put("reason",reason);out.put("settled",settled);return Map.copyOf(out);}
    }
    @SuppressWarnings("unchecked")public static Terms read(Map<String,Object> value){
        var t=new Terms(uuid(value,"id"),uuid(value,"town"),String.valueOf(value.get("other")).isEmpty()?null:uuid(value,"other"),string(value,"townName"),string(value,"otherName"),uuid(value,"world"),string(value,"period"),string(value,"title"),readGoods(value.get("give")),readGoods(value.get("receive")),readLabels(value.get("labels")),uuid(value,"actor"),string(value,"actorName"),number(value.get("created")),number(value.get("expires")));
        if(!t.hash().equals(value.get("hash"))||!t.kind().equals(value.get("kind")))throw new IllegalArgumentException("Подтверждение относится к другой смете");return t;
    }
    public static String encode(Terms terms){var y=new YamlConfiguration();terms.view().forEach(y::set);return y.saveToString();}
    public static Terms decode(String text)throws Exception{var y=new YamlConfiguration();y.loadFromString(text);var out=new HashMap<String,Object>(y.getValues(false));for(String key:List.of("give","receive","labels")){var s=y.getConfigurationSection(key);out.put(key,s==null?Map.of():s.getValues(false));}return read(out);}
    private static UUID uuid(Map<String,Object> map,String key){return UUID.fromString(string(map,key));}
    private static String string(Map<String,Object> map,String key){Object v=map.get(key);if(!(v instanceof String text))throw new IllegalArgumentException("Не хватает поля сметы: "+key);return text;}
    private static long number(Object value){try{if(!(value instanceof Number))throw new IllegalArgumentException("Неверное число сметы");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException e){throw new IllegalArgumentException("Неверное количество",e);}}
    private static Map<String,Long> readGoods(Object value){if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("Нет количеств продукции");var out=new HashMap<String,Long>();for(var e:map.entrySet()){if(!(e.getKey() instanceof String key))throw new IllegalArgumentException("Неверный код продукции");out.put(key,number(e.getValue()));}return out;}
    private static Map<String,String> readLabels(Object value){if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("Нет названий продукции");var out=new HashMap<String,String>();for(var e:map.entrySet()){if(!(e.getKey() instanceof String key)||!(e.getValue() instanceof String title))throw new IllegalArgumentException("Неверное название продукции");out.put(key,title);}return out;}
    public static Map<String,Long> goods(Map<String,Long> input,boolean empty){var out=Map.copyOf(input);if((out.isEmpty()&&!empty)||out.size()>7)throw new IllegalArgumentException("Нужно 1–7 видов продукции");long total=0;for(var e:out.entrySet()){ProductionCatalog.identifier(e.getKey());if(e.getValue()<1||e.getValue()>64)throw new IllegalArgumentException("Нужно 1–64 партии каждого вида");total=Math.addExact(total,e.getValue());}if(total>128)throw new IllegalArgumentException("Смета превышает 128 партий");return out;}
    public static void text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Неверное название или основание");}
}
