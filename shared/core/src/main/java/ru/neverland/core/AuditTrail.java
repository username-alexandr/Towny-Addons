package ru.neverland.core;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/** Local evidence survives absence/disable of Control. Audit failures never repeat a bank call. */
public final class AuditTrail {
    private static final Map<Path,AuditJournal> journals=new HashMap<>();
    private static final Map<Path,Long> warnings=new HashMap<>();
    private AuditTrail(){}
    public static synchronized boolean record(Plugin plugin,String step,String operation,String kind,String outcome,
            AuditRecord.Party actor,AuditRecord.Party from,AuditRecord.Party to,String asset,long quantity,String money,String details){
        Path path=plugin.getDataFolder().toPath().resolve("audit");
        try{
            var record=new AuditRecord(AuditRecord.id(plugin.getName(),operation,step),System.currentTimeMillis(),plugin.getName(),kind,outcome,operation,actor,from,to,asset,quantity,money,details);
            journals.computeIfAbsent(path,AuditJournal::new).append(record);warnings.remove(path);return true;
        }catch(Exception ex){long now=System.currentTimeMillis();if(now-warnings.getOrDefault(path,0L)>60_000){warnings.put(path,now);plugin.getLogger().log(Level.SEVERE,"AUDIT GAP: запись "+operation+" / "+step+" не сохранена. Проверьте каталог "+path+". Финансовое действие не повторяется.",ex);}return false;}
    }
    public static synchronized void bank(Plugin plugin,String kind,String outcome,AuditRecord.Party actor,AuditRecord.Party from,AuditRecord.Party to,double amount,String details){
        Path path=plugin.getDataFolder().toPath().resolve("audit");String operation=UUID.randomUUID().toString();
        try{var r=new AuditRecord(AuditRecord.id(plugin.getName(),operation,"bank"),System.currentTimeMillis(),plugin.getName(),kind,outcome,operation,actor,from,to,"",0,money(amount),details);journals.computeIfAbsent(path,AuditJournal::new).appendUnique(r);}
        catch(Exception ex){long now=System.currentTimeMillis();if(now-warnings.getOrDefault(path,0L)>60_000){warnings.put(path,now);plugin.getLogger().log(Level.SEVERE,"AUDIT GAP: банковская запись "+operation+" не сохранена; платёж не повторяется",ex);}}
    }
    public static void require(boolean recorded)throws java.io.IOException{if(!recorded)throw new java.io.IOException("Квитанция сохранена; очистка отложена до записи административного аудита");}
    public static AuditRecord.Party town(UUID id){if(id==null)return AuditRecord.Party.unknown();var t=TownyAPI.getInstance().getTown(id);return new AuditRecord.Party("TOWN",id.toString(),t==null?"Удалённый город":t.getName(),id.toString());}
    public static AuditRecord.Party player(UUID id){if(id==null)return AuditRecord.Party.unknown();var r=TownyAPI.getInstance().getResident(id);var t=r==null?null:r.getTownOrNull();return new AuditRecord.Party("PLAYER",id.toString(),r==null?"Неизвестный игрок":r.getName(),t==null?"":t.getUUID().toString());}
    public static AuditRecord.Party account(com.palmergames.bukkit.towny.object.economy.Account a){if(a==null)return AuditRecord.Party.unknown();var owner=a.getEconomyHandler();if(owner instanceof Town t)return town(t.getUUID());if(owner instanceof Resident r)return player(r.getUUID());if(owner instanceof Nation n)return new AuditRecord.Party("NATION",n.getUUID().toString(),n.getName(),"");return new AuditRecord.Party("ACCOUNT",a.getUUID()==null?"":a.getUUID().toString(),a.getName(),"");}
    public static String money(double value){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Некорректная сумма");return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    public static String cents(long value){return BigDecimal.valueOf(value,2).toPlainString();}
    public static String item(ItemStack item){if(item==null)return "";var copy=item.clone();copy.setAmount(1);return copy.getType().name()+":"+Base64.getEncoder().encodeToString(copy.serializeAsBytes());}
    /** Net changes per exact item for an exclusive inventory session, including shift-click/drag. */
    public static void inventory(Plugin plugin,String session,UUID actor,UUID town,String storage,ItemStack[] before,ItemStack[] after){
        var counts=new TreeMap<String,Long>();for(var i:before)if(i!=null&&!i.getType().isAir())counts.merge(item(i),-(long)i.getAmount(),Long::sum);for(var i:after)if(i!=null&&!i.getType().isAir())counts.merge(item(i),(long)i.getAmount(),Long::sum);
        var city=town(town);var person=player(actor);counts.forEach((asset,delta)->{if(delta!=0)record(plugin,"inventory:"+asset,session,"STORAGE_PLAYER","OBSERVED",person,delta>0?person:city,delta>0?city:person,asset,Math.abs(delta),"","Склад "+storage+"; изменение содержимого за сессию");});
    }
}
