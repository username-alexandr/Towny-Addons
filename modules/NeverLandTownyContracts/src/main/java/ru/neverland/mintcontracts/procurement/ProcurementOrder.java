package ru.neverland.mintcontracts.procurement;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact immutable purchase terms; a published municipal contract uses this same request UUID. */
public record ProcurementOrder(UUID id,UUID town,String townName,UUID actor,String actorName,String project,int level,
        String building,String fingerprint,String itemKey,String item,String name,int amount,long cents,long duration,
        long created,long expires,String state,long started,String detail,UUID refundActor,String refundName,String refundReason) {
    public static final Set<String> STATES=Set.of("QUOTE","FUNDING","FUNDED","PUBLISHING","PUBLISHED","SETTLED","REJECTED","REVIEW","CANCELLED","REFUNDING","REFUNDED");
    public ProcurementOrder {
        Objects.requireNonNull(id);Objects.requireNonNull(town);Objects.requireNonNull(actor);
        text(townName,100);text(actorName,64);text(building,160);text(name,160);text(item,131072);text(fingerprint,128);
        if(project==null||!project.matches("[a-z0-9_-]{1,80}")||level<1||level>1000||itemKey==null||!itemKey.matches("[a-f0-9]{64}")||amount<1||amount>1_000_000||cents<1||cents>100_000_000_000L||duration<60||duration>31_536_000||created<1||expires<created||started<0||!STATES.contains(state)||detail==null||detail.length()>400)throw new IllegalArgumentException("Неверная сохранённая смета закупки");
        if(refundActor==null){if(!refundName.isEmpty()||!refundReason.isEmpty())throw new IllegalArgumentException("Возврат без инициатора");}else{text(refundName,64);text(refundReason,300);}
    }
    private static void text(String value,int max){if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException("Неверный текст сметы закупки");}
    public ProcurementOrder state(String next,long start,String reason){return new ProcurementOrder(id,town,townName,actor,actorName,project,level,building,fingerprint,itemKey,item,name,amount,cents,duration,created,expires,next,start,reason,refundActor,refundName,refundReason);}
    public ProcurementOrder refund(UUID actor,String name,String reason){return new ProcurementOrder(id,town,townName,this.actor,actorName,project,level,building,fingerprint,itemKey,item,this.name,amount,cents,duration,created,expires,"REFUNDING",started,"Возврат оплаченного резерва ожидает подтверждения",actor,name,reason);}
    public String bankReason(){return "Закупка для здания "+project+" • "+id;}
    public UUID refundId(){return UUID.nameUUIDFromBytes(("procurement-refund:"+id).getBytes(StandardCharsets.UTF_8));}
    public String template(){return "procure_"+id.toString().replace("-","");}
    public Map<String,Object> view(){var row=new LinkedHashMap<String,Object>();row.put("requestId",id.toString());row.put("town",town.toString());row.put("townName",townName);row.put("actor",actor.toString());row.put("actorName",actorName);row.put("project",project);row.put("targetLevel",level);row.put("building",building);row.put("itemKey",itemKey);row.put("name",name);row.put("amount",amount);row.put("rewardCents",cents);row.put("durationSeconds",duration);row.put("createdAt",created);row.put("expiresAt",expires);row.put("status",state);row.put("startedAt",started);row.put("detail",detail);row.put("contractId",id.toString());return Map.copyOf(row);}
}
