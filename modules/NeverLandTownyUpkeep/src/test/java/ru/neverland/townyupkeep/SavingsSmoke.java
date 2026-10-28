package ru.neverland.townyupkeep;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyupkeep.data.UpkeepRepository;
import ru.neverland.townyupkeep.model.*;
import ru.neverland.townyupkeep.model.Entry.*;
public final class SavingsSmoke {
    static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    interface Work{void run()throws Exception;}static void reject(Work work,String label)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError(label);}
    static Map<String,Object> row(String id,long money,boolean known){return Map.of("project",id,"cents",money,"resources",Map.of("wood",money*10),"known",known);}
    public static void main(String[] args)throws Exception{
        check(BillingPreview.charges(0,3600,86400)==24,"due now is counted once, horizon end excluded");check(BillingPreview.charges(3600,3600,86400)==23,"paid first hour not charged again");check(BillingPreview.charges(86400,3600,86400)==0,"payment exactly after forecast excluded");check(BillingPreview.charges(86399,3600,86400)==1,"last second included");
        check(BillingPreview.charges(0,60,604800)==10080,"largest supported period count bounded");reject(()->BillingPreview.charges(-1,60,86400),"negative due rejected");reject(()->BillingPreview.charges(0,60,604801),"unsupported horizon rejected");
        var summary=BillingPreview.summary(List.of(row("farm",1000,true),row("guard",3000,true)),Set.of("farm"));check(summary.get("beforeCents").equals(4000L)&&summary.get("afterCents").equals(3000L)&&summary.get("savingCents").equals(1000L),"saving subtracts only selected building");check(((Map<?,?>)summary.get("afterResources")).get("wood").equals(30000L),"resource saving uses exact same selected subset");check(Boolean.FALSE.equals(BillingPreview.summary(List.of(row("farm",0,false)),Set.of("farm")).get("known")),"uncertain invoice cannot promise an exact forecast");reject(()->BillingPreview.summary(List.of(row("farm",1,true)),Set.of("missing")),"unknown stop request rejected");
        var root=Files.createTempDirectory("upkeep-savings-");Path file=root.resolve("data.yml");var repo=new UpkeepRepository(file);repo.load();var key=new Key(UUID.randomUUID(),"farm");repo.save(100,Map.of(key,Entry.grace(3700)));repo.suspend(110,key,true);
        check(repo.paused().contains(key)&&repo.entries().get(key).due()==3700,"stop persists without extending or refunding paid time");var restart=new UpkeepRepository(file);restart.load();check(restart.clock()==110&&restart.paused().equals(Set.of(key)),"suspension survives complete repository reload");restart.suspend(4000,key,false);check(!restart.paused().contains(key)&&restart.entries().get(key).due()==3700,"resume after paid period cannot grant new grace");
        var pending=new Entry(false,4000,"Paying",new Invoice(UUID.randomUUID(),new Cost(1000,Map.of()),3600,Phase.MONEY_PENDING));restart.save(4000,Map.of(key,pending));reject(()->restart.suspend(4000,key,true),"ambiguous external payment cannot be hidden by pausing");check(restart.entries().get(key).invoice().phase()==Phase.MONEY_PENDING&&!restart.paused().contains(key),"pending receipt stays inspectable after denied pause");
        restart.save(4000,Map.of(key,Entry.grace(5000)));var y=new YamlConfiguration();y.load(file.toFile());y.set("schema",1);y.set("paused",null);y.save(file.toFile());var old=new UpkeepRepository(file);old.load();check(old.paused().isEmpty()&&old.entries().get(key).due()==5000,"schema 1 migration keeps paid interval");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"keep");reject(()->old.suspend(4001,key,true),"actual replacement failure rejected");check(old.paused().isEmpty()&&old.clock()==4000,"failed pause cannot change live effects before durable save");
        System.out.println("SavingsSmoke PASS "+checks+" checks");
    }
}
