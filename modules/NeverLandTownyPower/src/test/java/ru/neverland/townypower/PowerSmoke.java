package ru.neverland.townypower;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.data.PowerRepository;
import ru.neverland.townypower.model.*;
import ru.neverland.townypower.model.PowerEngine.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
public final class PowerSmoke {
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    private interface Attempt{void run()throws Exception;}
    private static void fails(Attempt a,String text)throws Exception{try{a.run();}catch(Exception expected){return;}throw new AssertionError(text);}
    private static YamlConfiguration read(String file)throws Exception{var y=new YamlConfiguration();try(var in=PowerSmoke.class.getResourceAsStream("/"+file)){y.load(new InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8));}return y;}
    private static Building built(int level){return new Building(level,true,true,1);}
    public static void main(String[] args)throws Exception{
        var settings=PowerSettings.load(read("config.yml"),read("buildings.yml"));var profiles=settings.profiles();var empty=TownPowerState.empty();
        check(profiles.size()==94,"94 catalog profiles");check(profiles.values().stream().filter(PowerProfile::producer).count()==4,"four generators");check(profiles.values().stream().filter(PowerProfile::consumer).count()==15,"15 advanced consumers");
        for(var p:profiles.values()){check(p.name().matches(".*[А-Яа-яЁё].*"),"Russian name: "+p.id());check(Material.matchMaterial(p.icon())!=null,"valid icon");if(p.demand().size()==5)check(p.demand(1)==0&&p.demand(2)==0,"early development stays unpowered");}
        var before=PowerEngine.calculate(profiles,Map.of("mill",built(2),"foundry",built(2)),empty);check(before.generation()==0&&before.buildings().get("foundry").powered(),"early stages do not require power");
        var first=PowerEngine.calculate(profiles,Map.of("mill",built(3),"foundry",built(3)),empty);check(first.generation()==8&&first.supplied()==0&&!first.buildings().get("foundry").powered(),"mill alone cannot power advanced foundry");
        Map<String,Building> mid=new HashMap<>(Map.of("water_wheel",built(3),"foundry",built(3),"pumping_station",built(3)));
        var shortGrid=PowerEngine.calculate(profiles,mid,empty);check(shortGrid.generation()==30&&shortGrid.demand()==35&&shortGrid.supplied()==15&&shortGrid.deficit()==20&&shortGrid.spare()==15,"whole-building allocation retains unusable spare");check(shortGrid.buildings().get("pumping_station").powered()&&!shortGrid.buildings().get("foundry").powered(),"water has first priority");
        var preferred=PowerEngine.calculate(profiles,mid,empty.priority("foundry",0));check(preferred.supplied()==20&&preferred.buildings().get("foundry").powered()&&!preferred.buildings().get("pumping_station").powered(),"city priority overrides default");
        mid.put("water_wheel",built(4));var recovered=PowerEngine.calculate(profiles,mid,empty);check(recovered.supplied()==35&&recovered.deficit()==0&&recovered.buildings().get("foundry").powered(),"upgrade automatically resumes consumers without previous state latch");
        var stopped=PowerEngine.calculate(profiles,mid,empty.stop("water_wheel",true));check(stopped.generation()==0&&stopped.supplied()==0&&stopped.buildings().get("water_wheel").status()==Status.STOPPED,"manual generator shutdown");check(PowerEngine.calculate(profiles,mid,empty).supplied()==35,"manual restart restores grid");
        var consumerStop=PowerEngine.calculate(profiles,mid,empty.stop("foundry",true));check(consumerStop.demand()==15&&consumerStop.buildings().get("foundry").status()==Status.STOPPED,"stopped consumer releases capacity");
        for(var unavailable:List.of(new Building(4,true,false,1),new Building(4,false,true,1),built(0))){mid.put("water_wheel",unavailable);var grid=PowerEngine.calculate(profiles,mid,empty);check(grid.generation()==0&&grid.supplied()==0,"unpaid, unowned, incomplete generators cannot produce");}
        mid.put("water_wheel",new Building(3,true,true,1.5));check(PowerEngine.calculate(profiles,mid,empty).generation()==45,"district generation bonus");mid.put("water_wheel",new Building(3,true,true,Double.NaN));check(PowerEngine.calculate(profiles,mid,empty).generation()==30,"nonfinite bonus is neutral");mid.put("water_wheel",new Building(3,true,true,100));check(PowerEngine.calculate(profiles,mid,empty).generation()==90,"bonus upper bound");
        Map<String,Building> metropolis=new HashMap<>();profiles.forEach((id,p)->metropolis.put(id,built(p.generation().size())));var large=PowerEngine.calculate(profiles,metropolis,empty);check(large.generation()==2114&&large.demand()==820&&large.supplied()==820,"late-game network budget");
        var random=new Random(129812);for(int cycle=0;cycle<1000;cycle++){Map<String,Building> town=new HashMap<>();for(String id:profiles.keySet())town.put(id,new Building(random.nextInt(6),random.nextBoolean(),random.nextBoolean(),1+random.nextDouble()));var grid=PowerEngine.calculate(profiles,town,empty);check(grid.supplied()<=grid.generation()&&grid.supplied()<=grid.demand(),"conservation");check(grid.equals(PowerEngine.calculate(profiles,town,empty)),"stateless deterministic allocation; no energy accrual");for(var a:grid.buildings().values())check(a.supplied()==0||a.supplied()==a.demand(),"no partial supply");}
        var excluded=read("buildings.yml");excluded.set("buildings.foundry.enabled",false);var ex=PowerSettings.load(read("config.yml"),excluded);check(PowerEngine.calculate(ex.profiles(),Map.of("foundry",built(5)),empty).buildings().get("foundry").powered(),"admin can disable energy requirement");
        var invalid=read("buildings.yml");invalid.set("buildings.generator.demand",List.of(0,0,1,2,3));fails(()->PowerSettings.load(read("config.yml"),invalid),"generator cannot consume its own network");invalid.set("buildings.generator.demand",List.of(0,0,-1,0,0));fails(()->PowerSettings.load(read("config.yml"),invalid),"negative power rejected");
        fails(()->PowerSettings.number("NaN"),"NaN rejected");fails(()->PowerSettings.number(1.5),"fractional power rejected");fails(()->empty.priority("foundry",101),"priority bounds");fails(()->empty.stop("bad.id",true),"YAML path injection rejected");
        var dir=Files.createTempDirectory("power-smoke");var path=dir.resolve("power.yml");var repo=new PowerRepository(path);repo.load();var townA=UUID.randomUUID();var townB=UUID.randomUUID();var stateA=empty.stop("generator",true).priority("foundry",0);repo.replace(Map.of(townA,stateA,townB,empty.priority("research",99)));var restarted=new PowerRepository(path);restarted.load();check(restarted.towns().equals(repo.towns()),"restart preserves priorities and switches, city isolation");check(!Files.readString(path).contains("generation"),"no persisted free power");
        Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("keep"),"keep");fails(()->repo.replace(Map.of()),"failed atomic replace");check(repo.towns().get(townA).equals(stateA),"write failure preserves authoritative memory");
        var bad=dir.resolve("bad.yml");Files.writeString(bad,"schema: 1\ntowns: [broken");var broken=new PowerRepository(bad);fails(broken::load,"strict YAML parse");fails(()->broken.replace(Map.of(townA,stateA)),"failed load cannot overwrite data");check(Files.readString(bad).contains("[broken"),"damaged source preserved");
        OperationGateSmoke.run();
        System.out.println("PowerSmoke OK: 94 profiles, four sources, 15 consumers, shortage recovery, priorities, shutdown, maintenance, ownership, districts, 1000 capacity cycles and atomic persistence");
    }
}
