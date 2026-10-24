package ru.neverland.townycouncil;
import java.nio.file.*;
import java.util.*;
public final class AccessRulesSmoke {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    interface Work{void run()throws Exception;}
    static void reject(Work work,String label)throws Exception{try{work.run();}catch(Exception expected){return;}throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        UUID mayor=UUID.randomUUID(),resident=UUID.randomUUID(),town=UUID.randomUUID();
        var initial=AccessRules.empty(mayor);var role=initial.change("economy",null,TownAction.BUDGET,true);
        check(role.override(resident,"economy",TownAction.BUDGET),"role grant");
        var denied=role.change("",resident,TownAction.BUDGET,false);
        check(!denied.override(resident,"economy",TownAction.BUDGET),"personal denial takes precedence");
        check(denied.change("",resident,TownAction.BUDGET,null).override(resident,"economy",TownAction.BUDGET),"reset inherits role");
        check(denied.override(resident,"",TownAction.TRADE)==null,"unconfigured action inherits original rules");
        var personal=role.change("economy",null,TownAction.BUDGET,false).change("",resident,TownAction.BUDGET,true);
        check(personal.override(resident,"economy",TownAction.BUDGET),"personal allow takes precedence");
        check(initial.revision()==0&&denied.revision()==2&&initial.roles().isEmpty(),"immutable revisions");
        reject(()->role.roles().get("economy").put("army",true),"nested map immutable");
        reject(()->initial.change("",mayor,TownAction.ARMY,false),"cannot restrict mayor");
        reject(()->initial.change("admin",null,TownAction.ARMY,true),"cannot invent role");
        reject(()->TownAction.parse("minecraft.command.op"),"cannot invent server permission");
        reject(()->new AccessRules(mayor,1,Map.of("economy",Map.of("BUDGET",true)),Map.of()),"noncanonical persisted action rejected");
        check(personal.without(resident).residents().isEmpty(),"resident departure removes overrides");
        Path file=Files.createTempDirectory("council-access-").resolve("access.yml");var repo=new AccessRepository(file);repo.load();
        repo.commit(Map.of(town,denied),"mayor denied budget");var restart=new AccessRepository(file);restart.load();
        check(restart.cities().equals(repo.cities())&&restart.history().equals(repo.history()),"permissions and audit survive restart together");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("obstruction"),"preserve");
        reject(()->restart.commit(Map.of(town,personal),"allow"),"write error rejected");
        check(!restart.writable()&&restart.cities().get(town).equals(denied),"failed write locks and retains prior denial");
        Files.delete(file.resolve("obstruction"));Files.delete(file);
        reject(()->restart.commit(Map.of(),"retry"),"no automatic write retry after failure");
        Files.writeString(file,"schema: 2\ncities: {}\nhistory: []\n");reject(()->new AccessRepository(file).load(),"unknown schema rejected");
        Files.writeString(file,"schema: 1\ncities:\n  invalid: false\nhistory: []\n");reject(()->new AccessRepository(file).load(),"corrupt city not silently discarded");
        System.out.println("AccessRulesSmoke PASS: precedence, inheritance, closed actions, immutable state, restart, corrupt data and disk faults");
    }
}
