package ru.neverland.townyresearch;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyresearch.config.ResearchSettings;
import ru.neverland.townyresearch.data.ResearchRepository;
import ru.neverland.townyresearch.model.*;
import ru.neverland.integration.ResearchEffects;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import static ru.neverland.townyresearch.model.CityStudy.Phase.*;

public final class ResearchSmoke {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private interface Attempt{void run()throws Exception;}
    private static void fails(Attempt a,String reason)throws Exception{try{a.run();}catch(Exception expected){return;}throw new AssertionError(reason);}
    private static YamlConfiguration read(String name)throws Exception{var y=new YamlConfiguration();try(var in=ResearchSmoke.class.getResourceAsStream("/"+name)){y.load(new InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8));}return y;}
    private static final UUID TOWN=UUID.randomUUID(),OTHER=UUID.randomUUID();
    private static final Map<String,Integer> BUILDINGS=Map.of("great_library",3,"irrigation_station",3);
    private static final Technology TECHNOLOGY=new Technology("irrigation","Ирригация","WATER_BUCKET","Рост урожая",true,List.of(new Technology.Level(750000,10,.1,BUILDINGS,Map.of())));
    /** Failure injection models interruption immediately before a journal write or after a durable ledger mutation. */
    private static final class Fixture implements ResearchProcessor.Store,ResearchProcessor.Gateway {
        final Map<UUID,CityStudy> cities=new HashMap<>();final Map<UUID,String> receipts=new HashMap<>();
        final Map<UUID,Long> costs=new HashMap<>();long money=1000000;int debits,refunds,consumed,failWrite=-1,writes;
        boolean crashReserve,crashSettle,crashForget;
        public CityStudy get(UUID id){return cities.getOrDefault(id,CityStudy.empty());}
        public void put(UUID id,CityStudy s)throws IOException{if(++writes==failWrite)throw new IOException("write failed");cities.put(id,s);}
        public String status(UUID id){return receipts.getOrDefault(id,"NONE");}
        public boolean reserve(UUID id,UUID town,long cost)throws IOException{if(!status(id).equals("NONE"))return true;if(money<cost)return false;money-=cost;debits++;receipts.put(id,"HELD");costs.put(id,cost);if(crashReserve){crashReserve=false;throw new IOException("reserve committed before interruption");}return true;}
        public void settle(UUID id,boolean consume)throws IOException{String target=consume?"CONSUMED":"RELEASED";if(status(id).equals(target))return;if(!status(id).equals("HELD"))throw new IOException("conflicting receipt");receipts.put(id,target);if(consume)consumed++;else{money+=costs.get(id);refunds++;}if(crashSettle){crashSettle=false;throw new IOException("settlement committed before interruption");}}
        public void forget(UUID id)throws IOException{if(status(id).equals("HELD"))throw new IOException("held");receipts.remove(id);if(crashForget){crashForget=false;throw new IOException("cleanup committed before interruption");}}
        ResearchProcessor restart(){return new ResearchProcessor(this,this);}
        void start()throws Exception{restart().begin(TOWN,TECHNOLOGY,BUILDINGS);}
    }
    public static void main(String[] args)throws Exception{
        var settings=ResearchSettings.load(read("config.yml"),read("technologies.yml"));
        check(settings.technologies().size()==7,"seven technologies");
        for(var t:settings.technologies().values()){check(t.levels().size()==3,"three default levels");check(t.name().matches(".*[А-Яа-яЁё].*"),"Russian names");check(Material.matchMaterial(t.icon())!=null,"valid icon");}
        check(ResearchSettings.knowledge("1,125")==1125,"fixed point");fails(()->ResearchSettings.knowledge("0.0001"),"precision rejected");fails(()->ResearchSettings.integer(1.5),"fractional duration rejected");
        var cyclic=read("technologies.yml");cyclic.set("technologies.irrigation.levels.1.requires.medicine",1);fails(()->ResearchSettings.load(read("config.yml"),cyclic),"cycle rejected");
        for(Object bad:List.of(-1,"NaN",1.1)){var y=read("technologies.yml");y.set("technologies.irrigation.levels.1.bonus",bad);fails(()->ResearchSettings.load(read("config.yml"),y),"invalid bonus rejected");}
        var unknown=read("technologies.yml");unknown.set("technologies.irrigation.levels.1.requires.missing",1);fails(()->ResearchSettings.load(read("config.yml"),unknown),"unknown prerequisite rejected");
        var negative=read("technologies.yml");negative.set("technologies.irrigation.levels.1.knowledge",-1);fails(()->ResearchSettings.load(read("config.yml"),negative),"negative cost rejected");
        var missing=new Fixture();fails(()->missing.restart().begin(TOWN,TECHNOLOGY,Map.of()),"buildings enforced");check(missing.debits==0&&missing.cities.isEmpty(),"requirements never charge");
        var dependent=settings.technologies().get("medicine");fails(()->missing.restart().begin(TOWN,dependent,dependent.levels().get(0).buildings()),"technology prerequisite enforced");
        var intent=new Fixture();intent.failWrite=1;fails(intent::start,"intent write failure");check(intent.debits==0&&intent.get(TOWN).active()==null,"no charge before durable intent");
        var waiting=new Fixture();waiting.money=0;waiting.start();check(waiting.get(TOWN).active().phase()==PREPARED&&waiting.debits==0,"wait for knowledge");waiting.money=1000000;waiting.restart().tick(TOWN,5,false);check(waiting.debits==0,"inactive buildings pause before reserve");waiting.restart().tick(TOWN,5,true);check(waiting.debits==1&&waiting.get(TOWN).active().remaining()==10,"waiting resumes without retroactive progress");
        var reserved=new Fixture();reserved.crashReserve=true;fails(reserved::start,"reserve interruption");check(reserved.get(TOWN).active().phase()==PREPARED&&reserved.money==250000,"durable reserve despite interruption");reserved.restart().tick(TOWN,0,true);check(reserved.debits==1&&reserved.get(TOWN).active().phase()==RUNNING,"restart reuses receipt");
        var phaseWrite=new Fixture();phaseWrite.failWrite=2;fails(phaseWrite::start,"running write interruption");phaseWrite.restart().tick(TOWN,0,true);check(phaseWrite.debits==1,"no double spend after failed phase write");
        var normal=new Fixture();normal.start();fails(normal::start,"one city study at a time");normal.restart().tick(TOWN,5,true);normal.restart().tick(TOWN,60,false);normal.restart().tick(TOWN,0,true);check(normal.get(TOWN).active().remaining()==5,"pause and restart never advance offline");check(normal.get(OTHER).equals(CityStudy.empty()),"city isolation");normal.restart().tick(TOWN,5,true);check(normal.money==250000&&normal.consumed==1&&normal.get(TOWN).learned().get("irrigation")==1,"complete and debit exactly once");fails(normal::start,"cleanup blocks next intent");normal.crashForget=true;fails(()->normal.restart().tick(TOWN,0,true),"cleanup interruption");normal.restart().tick(TOWN,0,true);check(normal.get(TOWN).cleanup().isEmpty()&&normal.receipts.isEmpty(),"cleanup retry is safe");fails(normal::start,"maximum level enforced");
        var commit=new Fixture();commit.start();commit.crashSettle=true;fails(()->commit.restart().tick(TOWN,10,true),"consume interruption");check(commit.get(TOWN).active().phase()==COMPLETING&&commit.get(TOWN).learned().isEmpty(),"no benefit before durable completion");fails(()->commit.restart().cancel(TOWN),"completing cannot be cancelled");commit.restart().tick(TOWN,0,true);check(commit.consumed==1&&commit.get(TOWN).learned().get("irrigation")==1,"consumed receipt recovers once");
        var learned=new Fixture();learned.start();learned.failWrite=learned.writes+2;fails(()->learned.restart().tick(TOWN,10,true),"learned write failed");check(learned.get(TOWN).learned().isEmpty()&&learned.consumed==1,"failed write publishes no benefit");learned.restart().tick(TOWN,0,true);check(learned.get(TOWN).learned().get("irrigation")==1&&learned.consumed==1,"complete retry preserves debit");
        var cancel=new Fixture();cancel.start();cancel.restart().tick(TOWN,5,true);cancel.crashSettle=true;fails(()->cancel.restart().cancel(TOWN),"refund interruption");check(cancel.money==1000000&&cancel.get(TOWN).active().phase()==CANCELLING,"full refund committed");cancel.restart().tick(TOWN,0,false);cancel.restart().tick(TOWN,0,false);check(cancel.refunds==1&&cancel.get(TOWN).active()==null&&cancel.get(TOWN).learned().isEmpty(),"cancel restores knowledge once without unlocking");
        var cancelWrite=new Fixture();cancelWrite.start();cancelWrite.failWrite=cancelWrite.writes+2;fails(()->cancelWrite.restart().cancel(TOWN),"refund completion write failure");cancelWrite.restart().tick(TOWN,0,false);check(cancelWrite.refunds==1&&cancelWrite.money==1000000,"refund recovery after journal failure");
        var absent=new Fixture();absent.money=0;absent.start();absent.restart().cancel(TOWN);check(absent.refunds==0&&absent.get(TOWN).active()==null,"waiting cancellation has nothing to refund");
        var conflict=new Fixture();conflict.start();conflict.receipts.clear();fails(()->conflict.restart().tick(TOWN,10,true),"missing reserve cannot unlock");check(conflict.get(TOWN).learned().isEmpty(),"conflict fails closed");
        var accelerated=new Fixture();accelerated.start();accelerated.restart().tick(TOWN,5,true,.1);var half=accelerated.get(TOWN).active();check(half.remaining()==5&&half.fraction()==500,"fractional acceleration is durable");accelerated.restart().tick(TOWN,60,false,.5);check(accelerated.get(TOWN).active().equals(half),"pause does not accrue fractional research");accelerated.restart().tick(TOWN,4,true,.1);check(accelerated.get(TOWN).active().remaining()==1&&accelerated.get(TOWN).active().fraction()==900,"fraction preserved across processor restart");accelerated.restart().tick(TOWN,1,true,0);check(accelerated.get(TOWN).learned().get("irrigation")==1&&accelerated.debits==1,"bonus change retains earned progress without free knowledge");
        var fractionalStudy=new CityStudy(Map.of(),half,Set.of());persistence(fractionalStudy,normal.get(TOWN));
        persistence(reserved.get(TOWN),normal.get(TOWN));effects();BonusBridgeSmoke.run();
        System.out.println("ResearchSmoke OK: 7 technologies / 21 levels, prerequisites, durable intent/reserve/consume/refund/cleanup, restart, pauses, city isolation and all effect bounds");
    }
    private static void persistence(CityStudy active,CityStudy complete)throws Exception{
        var dir=Files.createTempDirectory("research-smoke");try{var path=dir.resolve("research-data.yml");var repo=new ResearchRepository(path);repo.load();repo.put(TOWN,active);repo.put(OTHER,complete);var restart=new ResearchRepository(path);restart.load();check(restart.towns().equals(repo.towns()),"restart roundtrip includes progress, costs, levels and receipts");
            String saved=Files.readString(path);Files.writeString(path,saved.replaceAll("(?m)^\\s*fraction:.*\\R", ""));var legacy=new ResearchRepository(path);legacy.load();check(legacy.get(TOWN).active().fraction()==0&&legacy.get(TOWN).active().remaining()==active.active().remaining(),"old 0.13 schema loads with zero fraction and unchanged remaining time");Files.writeString(path,saved);
            Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("keep"),"keep");fails(()->restart.put(TOWN,complete),"atomic write failure");check(restart.get(TOWN).equals(active)&&Files.readString(path.resolve("keep")).equals("keep"),"failed write preserves committed state");Files.delete(path.resolve("keep"));Files.delete(path);Files.writeString(path,"schema: 1\ntowns: [broken");String corrupt=Files.readString(path);var broken=new ResearchRepository(path);fails(broken::load,"corrupt YAML rejected");fails(()->broken.put(TOWN,complete),"write guard after failed load");check(Files.readString(path).equals(corrupt),"corrupt data not overwritten");
        }finally{try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    private static void effects(){check(ResearchEffects.production(1.15,.3)==1.495&&ResearchEffects.production(3,.5)==3,"exact district/research combination and existing cap");check(ResearchEffects.productionTechnology("foundry").equals("metallurgy")&&ResearchEffects.productionTechnology("alchemy").equals("alchemy")&&ResearchEffects.productionTechnology("warehouse").isEmpty(),"production targets only");check(ResearchEffects.speed(1,.3)==1.3,"courier speed");check(ResearchEffects.minutes(10,3,120,.3)==7&&ResearchEffects.minutes(3,3,120,.3)==3&&ResearchEffects.minutes(1000,3,120,.3)==120,"travel bounds");check(Math.abs(ResearchEffects.delay(.1,.02,.06)-.04)<1e-9&&ResearchEffects.delay(.03,.02,.06)==.02,"navigation percentage points and minimum");check(ResearchEffects.hostileDamage(100,.15,true,true,true)==85,"real wall mitigation");check(ResearchEffects.hostileDamage(100,.15,false,true,true)==100&&ResearchEffects.hostileDamage(100,.15,true,false,true)==100&&ResearchEffects.hostileDamage(100,.15,true,true,false)==100,"foreign city, inactive wall and PvP excluded");check(ResearchEffects.bounded(Double.NaN)==0&&ResearchEffects.bounded(-1)==0&&ResearchEffects.bounded(100)==.5,"safe bonus limits");}
}
