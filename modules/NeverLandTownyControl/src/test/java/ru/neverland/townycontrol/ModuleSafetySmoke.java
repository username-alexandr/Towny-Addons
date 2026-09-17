package ru.neverland.townycontrol;
import ru.neverland.core.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
public final class ModuleSafetySmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var graph=new ModuleGraph(Map.of("A",Set.of("B"),"B",Set.of("A"),"C",Set.of("B"),"D",Set.of()));
        check(graph.closure(Set.of("A")).equals(Set.of("A","B","C")),"cycles/dependent closure");
        check(graph.closure(Set.of("D")).equals(Set.of("D")),"independent plugin stays enabled");
        Path dir=Files.createTempDirectory("module-pause-");Path state=dir.resolve("modules.yml");
        var empty=ModulePauseStore.load(state);
        var disabled=ModulePauseStore.request(empty,Set.of("NeverLandTownyEvents"),Set.of("NeverLandTownyEvents","NeverLandTownyBuilds"),1000);
        ModulePauseStore.save(state,disabled);check(ModulePauseStore.load(state).equals(disabled),"durable group");
        var repeated=ModulePauseStore.request(disabled,disabled.requests(),disabled.modules().keySet(),2000);
        check(repeated.equals(disabled),"repeat disable preserves original pause time");
        var enabled=ModulePauseStore.request(disabled,Set.of(),Set.of(),3000);ModulePauseStore.save(state,enabled);
        check(!enabled.modules().get("NeverLandTownyEvents").disabled()&&enabled.modules().get("NeverLandTownyEvents").pausedAt()==1000,"enable retains clock until restart");
        ModulePauseStore.resumed(state,"NeverLandTownyEvents",1000);
        check(ModulePauseStore.load(state).modules().get("NeverLandTownyEvents").pausedAt()==0,"resume acknowledgement");
        Path plugin=dir.resolve("Events");Files.createDirectories(plugin);
        String before="active:\n  city:\n    ends-at: 9000\n    paused-at: 2000\n    last-raid-wave: 0\n    started-at: 100\n    progress: 37\n    goal: 90\n    raid:\n      wave: 4\ntowns:\n  city:\n    history:\n    - ended-at: 10\n      success: false\nshields:\n  city: 6000\n";
        Path file=plugin.resolve("events-data.yml");Files.writeString(file,before);
        ModuleTimers.resume(plugin,"NeverLandTownyEvents",3000,8000);
        String first=Files.readString(file);var y=new YamlConfiguration();y.loadFromString(first);
        check(y.getLong("active.city.ends-at")==14000&&y.getLong("active.city.paused-at")==7000,"remaining paused time preserved");
        check(y.getLong("active.city.started-at")==100&&y.getInt("active.city.progress")==37&&y.getInt("active.city.raid.wave")==4,"identity/progress preserved");
        check(y.getLong("shields.city")==11000&&y.getLong("active.city.last-raid-wave")==0,"shield/sentinel");
        ModuleTimers.resume(plugin,"NeverLandTownyEvents",3000,99999);
        check(Files.readString(file).equals(first),"recovery cannot shift twice");
        // Crash after journal persistence but before first file replacement.
        Files.writeString(file,before);ModuleTimers.resume(plugin,"NeverLandTownyEvents",3000,99999);
        check(Files.readString(file).equals(first),"journal replay uses original duration");
        Files.writeString(file,first+"unrelated: changed\n");
        try {ModuleTimers.resume(plugin,"NeverLandTownyEvents",3000,99999);throw new AssertionError("external modification overwritten");}catch(java.io.IOException expected){}
        String contracts="towns:\n  a:\n    active:\n      b:\n        expires-at: 8000\n        created-at: 500\n        escrow: 12345\npayments:\n  tx:\n    created: 300\n    phase: DEBIT_PENDING\n";
        var c=new YamlConfiguration();c.loadFromString(ModuleTimers.shifted(contracts,ModuleTimers.rules("NeverLandTownyContracts").get("contract-data.yml"),5000));
        check(c.getLong("towns.a.active.b.expires-at")==13000&&c.getLong("towns.a.active.b.escrow")==12345&&c.getLong("payments.tx.created")==300&&c.getString("payments.tx.phase").equals("DEBIT_PENDING"),"escrow and unknown bank outcome unchanged");
        Path trade=dir.resolve("Trade");Files.createDirectories(trade);
        Files.writeString(trade.resolve("trade-data.yml"),"caravans:\n  a:\n    arrives-at: 12000\n    departed-at: 3000\n    escrow: 120\n");
        Files.writeString(trade.resolve("contracts-data.yml"),"contracts:\n  c:\n    due: 15000\n    expires: 20000\n    deliveries: 4\n");
        ModuleTimers.resume(trade,"NeverLandTownyTrade",5000,8000);
        String one=Files.readString(trade.resolve("trade-data.yml"));
        // Simulate partial multi-file application: replay both safely.
        Files.writeString(trade.resolve("trade-data.yml"),"caravans:\n  a:\n    arrives-at: 12000\n    departed-at: 3000\n    escrow: 120\n");
        ModuleTimers.resume(trade,"NeverLandTownyTrade",5000,9000);check(Files.readString(trade.resolve("trade-data.yml")).equals(one),"partial multi-file recovery");
        try {ModuleTimers.shifted("due: 9223372036854775807\n",List.of("due"),1);throw new AssertionError("overflow accepted");}catch(ArithmeticException expected){}
        try {ModuleTimers.shifted("due: broken\n",List.of("due"),1);throw new AssertionError("corrupt timer accepted");}catch(IllegalArgumentException expected){}
        check(ModuleTimers.rules("NeverLandTownyUpkeep").isEmpty(),"online upkeep clock is never treated as an epoch timestamp");
        String reputation="last-decay: '2026-09-15'\nrelations:\n- last-changed: 3000\n  score: 70\n  history:\n  - timestamp: 1000\n";
        var rep=new YamlConfiguration();rep.loadFromString(ModuleTimers.shifted(reputation,ModuleTimers.rules("NeverLandTownyReputation").get("data.yml"),5000));
        check(rep.getString("last-decay").equals("2026-09-15")&&((Number)rep.getMapList("relations").get(0).get("last-changed")).longValue()==8000,"reputation grace clock shifts, daily date remains a string");
        var treaty=new YamlConfiguration();treaty.loadFromString(ModuleTimers.shifted("treaties:\n  a:\n    phase: ACTIVE\n    type: alliance\n    activated: 3000\n    expires: 12000\n    duration: 9000\n  b:\n    phase: ENDED\n    expires: 12000\n",ModuleTimers.rules("NeverLandTownyDiplomacy").get("diplomacy.yml"),5000));
        check(treaty.getLong("treaties.a.expires")-treaty.getLong("treaties.a.activated")==treaty.getLong("treaties.a.duration"),"diplomacy signed duration invariant");
        check(treaty.getLong("treaties.b.expires")==12000,"ended treaty history unchanged");
        System.out.println("ModuleSafetySmoke PASS: graph, durable group, time freeze, journal recovery, corruption and financial preservation");
    }
}
