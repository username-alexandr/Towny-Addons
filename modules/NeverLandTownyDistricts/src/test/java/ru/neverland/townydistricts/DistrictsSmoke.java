package ru.neverland.townydistricts;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townydistricts.config.DistrictSettings;
import ru.neverland.townydistricts.data.DistrictRepository;
import ru.neverland.townydistricts.model.*;
import ru.neverland.townydistricts.model.DistrictRules.Building;
import java.util.*;
import java.nio.file.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class DistrictsSmoke {
    private static final UUID TOWN=UUID.randomUUID(), WORLD=UUID.randomUUID();
    public static void main(String[] args)throws Exception {
        var config=resource("config.yml");var settings=DistrictSettings.load(config,resource("projects.yml"));
        check(settings.projects().size()==91 && DistrictType.values().length==7,"all projects and seven types");
        Set<Cell> area=Cell.rectangle(cell(0,0),cell(3,1),8);var district=new District(TOWN,"works","Заводской",DistrictType.INDUSTRIAL,area);
        DistrictRules.validate(district,List.of(),area,8);
        check(Cell.connected(area),"rectangle connected");
        check(Cell.rectangle(cell(-1,-1),cell(-2,-2),4).size()==4,"negative coordinates and reversed selection");
        check(!Cell.connected(Set.of(cell(0,0),cell(1,1))),"diagonals not adjacent");
        check(!Cell.connected(Set.of(cell(0,0),new Cell(UUID.randomUUID(),0,0))),"worlds separate");
        fail(()->Cell.rectangle(cell(0,0),cell(1000000000,1000000000),128),"oversized rectangle rejected before iteration");
        fail(()->Cell.rectangle(cell(0,0),new Cell(UUID.randomUUID(),0,0),128),"cross-world selection rejected");
        fail(()->DistrictRules.validate(district,List.of(),Set.of(cell(0,0)),8),"foreign land rejected atomically");
        fail(()->DistrictRules.validate(district,List.of(new District(UUID.randomUUID(),"other","Другой",DistrictType.PORT,Set.of(cell(0,0)))),area,8),"overlap across towns rejected");
        fail(()->DistrictRules.validate(district,List.of(new District(TOWN,"other","Другой",DistrictType.PORT,Set.of(cell(0,0)))),area,8),"overlap within town rejected");
        DistrictRules.validate(district,List.of(district),area,8);
        fail(()->DistrictRules.validate(new District(TOWN,"parts","Части",DistrictType.PORT,Set.of(cell(0,0),cell(2,0))),List.of(),area,8),"disconnected mutation rejected");
        fail(()->DistrictRules.validate(district,List.of(),area,7),"district limit");
        var buildings=List.of(building("forge",1,5),building("foundry",17,5),building("warehouse",33,5));
        var evaluation=eval(settings,List.of(district),buildings,area);
        check(evaluation.industrialCombinations().equals(Set.of("works")),"industrial combination detected");
        for(String id:List.of("forge","foundry","warehouse"))close(evaluation.multipliers().get(id),1.35,"combination applied once and additively");
        var incomplete=eval(settings,List.of(district),List.of(building("forge",1,4),buildings.get(1),buildings.get(2)),area);
        check(!incomplete.multipliers().containsKey("forge")&&incomplete.industrialCombinations().isEmpty(),"unfinished project no bonus/combination");
        close(incomplete.multipliers().get("foundry"),1.15,"matching building still gets base bonus");
        var first=new District(TOWN,"one","Первый",DistrictType.INDUSTRIAL,Set.of(cell(0,0)));
        var second=new District(TOWN,"two","Второй",DistrictType.INDUSTRIAL,Set.of(cell(1,0),cell(2,0)));
        check(eval(settings,List.of(first,second),buildings,area).industrialCombinations().isEmpty(),"separate industrial districts do not combine");
        var wrong=new District(TOWN,"homes","Жилой",DistrictType.RESIDENTIAL,area);
        check(eval(settings,List.of(wrong),buildings,area).multipliers().isEmpty(),"wrong district neutral");
        check(eval(settings,List.of(),buildings,area).multipliers().isEmpty(),"deleted district removes bonuses");
        check(eval(settings,List.of(district),buildings,Set.of(cell(0,0))).multipliers().isEmpty(),"lost territory disables stale combination");
        var straddling=new Building("forge",WORLD,15,1,16,4,5,5);
        check(eval(settings,List.of(first),List.of(straddling),area).districts().isEmpty(),"whole footprint must fit, not only origin");
        var negative=new Building("forge",WORLD,-17,-1,-1,0,5,5);
        check(negative.cells(16,6).orElseThrow().size()==4,"negative building footprint uses floor division");
        check(straddling.cells(32,1).orElseThrow().size()==1,"nonstandard Towny cell size respected");
        var capped=DistrictRules.evaluate(List.of(district),buildings,settings.types(),area,16,1,1,1.5,settings.requires());
        close(capped.multipliers().get("forge"),1.5,"maximum multiplier respected");
        for(var type:DistrictType.values()){
            String id=settings.projects().entrySet().stream().filter(e->e.getValue().type()==type).findFirst().orElseThrow().getKey();
            var d=new District(TOWN,"test",type.title,type,Set.of(cell(0,0)));
            close(eval(settings,List.of(d),List.of(building(id,1,5)),Set.of(cell(0,0))).multipliers().get(id),1.15,"type gets effective matching bonus: "+type);
        }
        check(DistrictRules.output(1,1.35,0.1,64)==2,"fractional production bonus awarded");
        check(DistrictRules.output(1,1.35,0.9,64)==1,"fractional production is not rounded up every time");
        check(DistrictRules.output(1,1.35,0,1)==1,"furnace result slot never overflows");
        check(DistrictRules.output(3,1.35,0,4)==4,"larger result capped to free space");
        check(DistrictRules.output(1,Double.NaN,0,64)==1,"invalid multiplier neutral");
        config.set("bonuses.matching-building",Double.NaN);fail(()->DistrictSettings.load(config,resource("projects.yml")),"NaN rejected");
        persistence(district);
        System.out.println("DistrictsSmoke OK: seven types, 91 profiles, ownership, overlaps, complete footprints, combinations, capacity-safe output, persistence");
    }
    private static void persistence(District d)throws Exception{
        Path dir=Files.createTempDirectory("districts-smoke-");Path path=dir.resolve("data.yml");
        try{
            var repo=new DistrictRepository(path,16);repo.load();repo.put(d);
            var loaded=new DistrictRepository(path,16);loaded.load();check(loaded.get(TOWN,"works").orElseThrow().equals(d),"restart retains district and cells");
            fail(()->new DistrictRepository(path,32).load(),"changed Towny cell size refuses reinterpretation");
            loaded.delete(TOWN,"works");var empty=new DistrictRepository(path,16);empty.load();check(empty.all().isEmpty(),"delete persists");
            Files.delete(path);Files.createDirectory(path);
            fail(()->empty.put(d),"failed storage write reported");check(empty.all().isEmpty(),"failed save cannot publish mutation");
            Files.delete(path);Files.writeString(path,"schema: 1\ntowns: [broken\n");String original=Files.readString(path);
            fail(()->new DistrictRepository(path,16).load(),"bad YAML rejected");check(Files.readString(path).equals(original),"bad database preserved");
        }finally{try(var files=Files.walk(dir)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}}
    }
    private static DistrictRules.Evaluation eval(DistrictSettings s,List<District> districts,List<Building> buildings,Set<Cell> owned){return DistrictRules.evaluate(districts,buildings,s.types(),owned,16,s.matching(),s.combination(),s.maximum(),s.requires());}
    private static Building building(String id,int x,int level){return new Building(id,WORLD,x,1,x+4,4,level,5);}
    private static Cell cell(int x,int z){return new Cell(WORLD,x,z);}
    private static YamlConfiguration resource(String name)throws Exception{try(var stream=DistrictsSmoke.class.getResourceAsStream("/"+name)){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8));}}
    private interface Action{void run()throws Exception;}
    private static void fail(Action a,String message)throws Exception{boolean failed=false;try{a.run();}catch(Exception ex){failed=true;}check(failed,message);}
    private static void close(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-9,message);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
