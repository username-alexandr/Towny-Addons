package ru.neverland.townypopulation;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townypopulation.config.PopulationSettings;
import ru.neverland.townypopulation.data.PopulationRepository;
import ru.neverland.townypopulation.model.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class PopulationSmoke {
    public static void main(String[] args) throws Exception {
        var config=resource("config.yml"); var buildings=resource("buildings.yml");
        var settings=PopulationSettings.load(config,buildings); var rules=settings.rules();
        check(settings.buildings().size()==91,"all 80 buildings and 11 wonders have population profiles");
        var startup=settings.capacity(id->0);
        check(startup.housing()==20 && startup.food()==20 && startup.water()==20,"starter settlement");
        check(PopulationMath.evaluate(10,startup,rules).change()>0,"starter town grows");
        check(settings.capacity(id->id.equals("residential_quarter")?4:0).housing()==20,"unfinished housing has no capacity");
        check(settings.capacity(id->id.equals("residential_quarter")?5:0).housing()==140,"completed housing opens 120 places");
        check(settings.capacity(id->id.equals("residential_quarter")?999:0).housing()==140,"levels capped without duplication");
        check(settings.capacity(id->id.equals("bakery")?5:0).food()==100,"bakery provides food");
        check(settings.capacity(id->id.equals("agrarian_complex")?5:0).food()==220,"agriculture provides food");
        check(settings.capacity(id->id.equals("water_tower")?5:0).water()==220,"tower provides water");
        check(settings.capacity(id->id.equals("reservoir")?5:0).water()==170,"reservoir provides water");
        check(settings.capacity(id->id.equals("world_tree")?1:0).happiness()==10,"wonder activates at stage one");

        var supplied=new Capacity(200,100,200,200,0);
        var healthy=PopulationMath.evaluate(100,supplied,rules);
        check(healthy.workforce()==60 && healthy.employed()==60 && healthy.unemployed()==0,"employment uses workforce");
        check(healthy.change()>0 && healthy.happiness()==70,"supplied population grows");
        check(PopulationMath.evaluate(100,new Capacity(200,0,200,200,0),rules).change()==0,"unemployment stops low-happiness growth");
        check(PopulationMath.evaluate(100,new Capacity(200,0,200,200,20),rules).happiness()==60,"amenities improve happiness");
        check(PopulationMath.evaluate(100,new Capacity(200,100,80,200,0),rules).change()==0,"moderate food shortage pauses growth");
        check(PopulationMath.evaluate(100,new Capacity(200,100,200,80,0),rules).change()==0,"moderate water shortage pauses growth");
        check(PopulationMath.evaluate(100,new Capacity(200,100,74,200,0),rules).change()<0,"critical food shortage shrinks town");
        check(PopulationMath.evaluate(100,new Capacity(200,100,200,74,0),rules).change()<0,"critical water shortage shrinks town");
        check(PopulationMath.evaluate(100,new Capacity(200,100,75,200,0),rules).change()==0,"critical threshold boundary");
        check(PopulationMath.evaluate(100,new Capacity(100,100,200,200,0),rules).change()==0,"housing capacity stops growth");
        var overcrowded=PopulationMath.advance(new PopulationState(100,0,0,0),new Capacity(50,100,200,200,0),rules,100);
        check(overcrowded.population()<100 && overcrowded.population()>50,"lost housing causes gradual decline");

        var state=new PopulationState(1,0,0,0);
        state=PopulationMath.advance(state,startup,rules,1);
        check(state.population()==1 && state.remainder()>0,"fractional growth is retained");
        state=PopulationMath.advance(state,startup,rules,2);
        check(state.population()==2,"small settlements eventually grow");
        for(int i=0;i<1000;i++) state=PopulationMath.advance(state,startup,rules,i+3);
        check(state.population()==20 && state.remainder()==0,"sustainable capacity never exceeded");
        var empty=new Capacity(0,0,0,0,0);
        for(int i=0;i<100;i++) state=PopulationMath.advance(state,empty,rules,2000+i);
        check(state.population()==0 && state.remainder()==0,"total shortage never makes negative population");
        for(int i=0;i<5;i++) state=PopulationMath.advance(state,startup,rules,3000+i);
        check(state.population()>0,"restored infrastructure repopulates empty town");
        var changed=PopulationMath.advance(new PopulationState(100,0.9,0,0),empty,rules,1);
        check(changed.lastChange()==-10,"growth remainder cannot offset new decline");
        var zero=PopulationMath.evaluate(0,empty,rules);
        check(Double.isFinite(zero.happiness()) && zero.unemployment()==0 && zero.change()==0,"zero denominators handled");
        config.set("simulation.growth-rate",0); config.set("simulation.decline-rate",0);
        var disabled=PopulationSettings.load(config,buildings);
        check(PopulationMath.evaluate(100,supplied,disabled.rules()).change()==0,"zero growth disables growth");
        check(PopulationMath.evaluate(100,empty,disabled.rules()).change()==0,"zero decline disables decline");
        config.set("simulation.food-per-person",Double.NaN);
        expectFailure(()->PopulationSettings.load(config,buildings),"NaN config rejected");
        var custom=resource("buildings.yml");
        custom.set("buildings.custom_house.name","Дом поселенцев"); custom.set("buildings.custom_house.minimum-level",1);
        custom.set("buildings.custom_house.maximum-level",3); custom.set("buildings.custom_house.housing",10);
        var customSettings=PopulationSettings.load(resource("config.yml"),custom);
        check(customSettings.capacity(id->id.equals("custom_house")?2:0).housing()==40,"custom project levels contribute");
        YamlConfiguration old=new YamlConfiguration(); old.set("buildings.bakery.food",0);
        old.setDefaults(buildings); old.options().copyDefaults(true);
        var inherited=PopulationSettings.load(resource("config.yml"),old);
        check(inherited.buildings().size()==91 && inherited.buildings().get("bakery").capacity().food()==0,"new defaults inherited; admin zero preserved");
        var districtHousing=settings.capacity(id->id.equals("residential_quarter")?5:0,id->1.15);
        check(districtHousing.housing()==158,"district bonus changes real housing while starter capacity stays unscaled");
        check(settings.capacity(id->id.equals("residential_quarter")?4:0,id->1.35).housing()==20,"district cannot activate unfinished housing");
        check(settings.capacity(id->id.equals("bakery")?5:0,id->1.15).food()==112,"district improves real food supply");
        check(settings.capacity(id->id.equals("water_tower")?5:0,id->Double.NaN).water()==220,"invalid district multiplier neutral");
        persistence();
        System.out.println("PopulationSmoke OK: 91 profiles, growth/decline, shortages, employment, fractions, custom settings, restart and corrupt database");
    }
    private static void persistence() throws Exception {
        Path dir=Files.createTempDirectory("population-smoke-");
        try {
            Path file=dir.resolve("population-data.yml"); UUID first=UUID.randomUUID(), second=UUID.randomUUID();
            var repository=new PopulationRepository(file); repository.load();
            repository.put(first,new PopulationState(17,0.4,1,100)); repository.put(second,new PopulationState(55,-0.3,-1,100)); repository.save();
            var reloaded=new PopulationRepository(file); reloaded.load();
            check(reloaded.get(first).equals(repository.get(first)),"population/fraction/history survive restart");
            reloaded.rebase(1000000);
            check(reloaded.get(first).population()==17 && reloaded.get(first).lastCycle()==1000000 && reloaded.get(first).remainder()==0.4,"offline time is rebased without population loss");
            reloaded.retain(Set.of(first)); reloaded.save();
            var deleted=new PopulationRepository(file); deleted.load();
            check(deleted.get(second)==null,"deleted town data removed");
            Files.writeString(file,"schema: 1\ntowns: [broken\n");
            String corrupt=Files.readString(file);
            expectFailure(()->new PopulationRepository(file).load(),"corrupt YAML aborts load");
            check(Files.readString(file).equals(corrupt),"corrupt database not overwritten");
        } finally {
            try(var paths=Files.walk(dir)) { for(var p:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
    }
    private static YamlConfiguration resource(String name) throws Exception {
        try(var stream=PopulationSmoke.class.getResourceAsStream("/"+name)) {
            if(stream==null) throw new AssertionError("missing "+name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }
    }
    interface Action { void run() throws Exception; }
    private static void expectFailure(Action action,String reason) throws Exception {
        boolean failed=false; try { action.run(); } catch(Exception ex) { failed=true; } check(failed,reason);
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
