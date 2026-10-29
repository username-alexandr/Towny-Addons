package ru.neverland.townypolicies;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townypolicies.config.*;
import ru.neverland.townypolicies.model.*;
import ru.neverland.townypolicies.model.PolicyPresets.*;
import ru.neverland.townypolicies.data.PoliciesRepository;
public final class PresetsSmoke {
    static int checks;static final UUID actor=UUID.randomUUID();
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static <T>T done(CompletionStage<T> future)throws Exception{return future.toCompletableFuture().get(4,TimeUnit.SECONDS);}
    static YamlConfiguration read(String resource)throws Exception{var y=new YamlConfiguration();try(var input=PresetsSmoke.class.getResourceAsStream("/"+resource)){y.load(new java.io.InputStreamReader(Objects.requireNonNull(input),java.nio.charset.StandardCharsets.UTF_8));}return y;}
    static Plan plan(CityPolicies state,PoliciesSettings policies,Preset preset,List<Conflict> conflicts,Map<String,String> blockers,long at,boolean admin){return PolicyPresets.plan(state,policies.groups(),preset,conflicts,blockers,at,policies.cooldownMillis(),actor,"Мэр",admin);}
    public static void main(String[] args)throws Exception{
        var settings=PoliciesSettings.load(read("config.yml"),read("policies.yml"));var catalog=PresetSettings.load(read("presets.yml"),settings);var empty=CityPolicies.empty();long now=1_000_000;
        check(catalog.enabled()&&new ArrayList<>(catalog.presets().keySet()).equals(List.of("development","defense","recovery")),"three readable presets preserve configured order");
        var development=plan(empty,settings,catalog.presets().get("development"),List.of(),Map.of(),now,false);check(development.ready()&&development.changed().size()==2&&development.after().revision()==2,"development changes only the two differing groups in one image");
        check(development.after().history().size()==2&&development.after().next("industry")==now+86400000&&empty.choices().isEmpty(),"each changed policy keeps its original cooldown and immutable before state");
        check(PolicyPresets.multiplier(settings.groups(),development.selected(),"foundry",false)==1.15&&PolicyPresets.multiplier(settings.groups(),development.selected(),"foundry",true)==1.25,"industry forecast matches actual bounded production and upkeep");
        check(PolicyPresets.multiplier(settings.groups(),development.selected(),"agrarian_complex",false)==1.15&&PolicyPresets.multiplier(settings.groups(),development.selected(),"army",true)==1,"growth costs are targeted, never copied onto unrelated buildings");
        var defense=plan(empty,settings,catalog.presets().get("defense"),List.of(),Map.of(),now,false);check(defense.ready()&&defense.warnings().size()==1,"overlapping support and mobilization are explained as a real tradeoff");
        check(Math.abs(PolicyPresets.multiplier(settings.groups(),defense.selected(),"foundry",false)-.9775)<1e-10&&PolicyPresets.multiplier(settings.groups(),defense.selected(),"army",true)==1.5,"combined effects multiply once and show the actual net result");
        var blocked=plan(empty,settings,catalog.presets().get("development"),List.of(),Map.of("industry","Содержание сейчас недоступно"),now,false);check(!blocked.ready()&&blocked.after().equals(empty)&&blocked.blockers().stream().anyMatch(s->s.contains("недоступно")),"one unavailable group prevents all partial policy changes");
        var recovery=plan(development.after(),settings,catalog.presets().get("recovery"),List.of(),Map.of(),now+1,false);check(!recovery.ready()&&recovery.after().equals(development.after()),"preset is not a cooldown bypass");
        check(plan(development.after(),settings,catalog.presets().get("recovery"),List.of(),Map.of(),now+86400000,false).ready(),"ordinary cooldown boundary permits recovery");
        check(plan(development.after(),settings,catalog.presets().get("recovery"),List.of(),Map.of(),now+1,true).ready(),"explicit administrator path can bypass waiting");
        var conflict=new Conflict("server_rule",Map.of("farmers","targeted","industry","focused"),"Эти меры нельзя сочетать по правилам сервера");var incompatible=plan(empty,settings,catalog.presets().get("development"),List.of(conflict),Map.of(),now,false);check(!incompatible.ready()&&incompatible.after().equals(empty)&&incompatible.blockers().contains(conflict.reason()),"configured multi-group conflict blocks whole set even when dependencies are ready");
        check(!plan(empty,settings,catalog.presets().get("recovery"),List.of(),Map.of(),now,false).ready(),"already selected preset does not create a new revision or reset waiting");
        var disabled=read("presets.yml");disabled.set("enabled",false);disabled.set("presets",List.of("bad"));check(!PresetSettings.load(disabled,settings).enabled(),"release toggle disables presets without requiring valid definitions");
        var invalid=read("presets.yml");invalid.set("presets.development.modes.industry","absent");boolean rejected=false;try{PresetSettings.load(invalid,settings);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"unknown modes are rejected before any city edit");
        var folder=Files.createTempDirectory("preset-commit-");var file=folder.resolve("policies-data.yml");var repo=new PoliciesRepository(file);repo.load();UUID city=UUID.randomUUID();done(repo.putAsync(city,development.after()));done(repo.prepareClose());var restarted=new PoliciesRepository(file);restarted.load();check(restarted.get(city).equals(development.after()),"whole selection, per-group dates and history survive a single acknowledged write and restart");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"preserve");var desired=plan(restarted.get(city),settings,catalog.presets().get("recovery"),List.of(),Map.of(),now+86400000,false).after();boolean failed=false;try{done(restarted.putAsync(city,desired));}catch(ExecutionException expected){failed=true;}check(failed&&restarted.get(city).equals(development.after()),"real failed snapshot grants none of the proposed recovery changes");done(restarted.prepareClose());
        System.out.println("PresetsSmoke PASS "+checks+" checks");
    }
}
