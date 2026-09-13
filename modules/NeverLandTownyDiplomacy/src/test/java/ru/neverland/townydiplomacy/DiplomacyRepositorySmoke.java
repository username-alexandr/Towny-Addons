package ru.neverland.townydiplomacy;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DiplomacyRepositorySmoke {
    interface Work{void run()throws Exception;}static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static void deny(Work work)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError("Invalid state accepted");}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("diplomacy-smoke");
        try{
            Path file=dir.resolve("state.yml");var repo=new DiplomacyRepository(file);repo.load();
            var t=DiplomacyRulesSmoke.active(TreatyType.TRADE,DiplomacyRulesSmoke.A,DiplomacyRulesSmoke.B);
            var audit=new DiplomacyRepository.Audit(t.id(),t.first(),t.second(),t.created(),"actor","ACCEPT");
            var incident=new DiplomacyRepository.Incident(UUID.randomUUID(),DiplomacyRulesSmoke.C,t.second(),Set.of(t.first()),t.created(),"world 1 2 3");
            repo.commit(Map.of(t.id(),t),List.of(audit),List.of(incident));byte[] saved=Files.readAllBytes(file);
            var restarted=new DiplomacyRepository(file);restarted.load();
            check(restarted.all().equals(repo.all())&&restarted.history().equals(List.of(audit))&&restarted.incidents().equals(List.of(incident)),"restart preserves full terms, consent, notice and incident");
            deny(()->restarted.all().clear());
            Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("obstruction"),"keep");
            deny(()->repo.commit(Map.of(t.id(),t.end()),List.of(),List.of()));
            check(!repo.writable()&&repo.all().get(t.id()).equals(t),"failed write locks repository and retains committed memory");
            Files.delete(file.resolve("obstruction"));Files.delete(file);Files.write(file,saved);
            deny(()->repo.commit(Map.of(),List.of(),List.of()));repo.load();check(repo.writable(),"explicit successful load restores writes");
            for(String key:List.of("schema","treaties."+t.id()+".first","treaties."+t.id()+".notice-period","incidents."+incident.id()+".defenders","history")){
                Files.write(file,saved);var y=YamlConfiguration.loadConfiguration(file.toFile());y.set(key,key.equals("schema")?2:"corrupt");y.save(file.toFile());
                var bad=new DiplomacyRepository(file);deny(bad::load);check(!bad.writable(),"corrupt record fails closed: "+key);
            }
            var y=new YamlConfiguration();y.load(Path.of("src/main/resources/config.yml").toFile());
            check(DiplomacySettings.load(y).equals(DiplomacySettings.defaults()),"shipped configuration is explicit and valid");
            y.set("trade-discount-percent",101);deny(()->DiplomacySettings.load(y));y.set("trade-discount-percent",25.0);deny(()->DiplomacySettings.load(y));
            y.set("trade-discount-percent",25);y.set("termination-notice-hours",-1);deny(()->DiplomacySettings.load(y));
            System.out.println("DiplomacyRepositorySmoke OK: "+checks+" atomic persistence, strict loading and configuration checks");
        }finally{try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
