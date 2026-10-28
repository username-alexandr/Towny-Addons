package ru.neverland.townybuilds.construction;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.DataStore;

/** Startup imports only. Runtime preview, repair and rollback never read scheme files or change versions. */
public final class BlueprintCatalog {
    private static volatile BlueprintCatalog active=new BlueprintCatalog();
    private final Map<String,String> current=new HashMap<>();
    private final Map<String,SchematicArchive.Family> versions=new HashMap<>();
    private final Map<String,String> issues=new LinkedHashMap<>();
    private final LinkedHashMap<String,BlueprintPlan> plans=new LinkedHashMap<>(16,.75f,true);
    private long cells,maxCells=16000000;private int planBlocks,maxPlanBlocks=100000;
    private static final Set<Material> FORBIDDEN=Set.of(Material.BEDROCK,Material.BARRIER,Material.COMMAND_BLOCK,Material.CHAIN_COMMAND_BLOCK,Material.REPEATING_COMMAND_BLOCK,Material.STRUCTURE_BLOCK,Material.STRUCTURE_VOID,Material.JIGSAW,Material.SPAWNER,Material.TRIAL_SPAWNER,Material.VAULT,Material.END_PORTAL,Material.END_PORTAL_FRAME,Material.END_GATEWAY,Material.NETHER_PORTAL,Material.TNT);
    private static String key(String project,BuildingStyle style){return project+":"+style.id();}
    private BlueprintCatalog(){}
    public static void initialize(JavaPlugin plugin,DataStore data)throws IOException{try(var guard=SuiteState.guard()){
        Path root=plugin.getDataFolder().toPath();if(!Files.exists(root.resolve("blueprints.yml")))plugin.saveResource("blueprints.yml",false);
        var next=new BlueprintCatalog();var config=SafeYaml.load(root.resolve("blueprints.yml"));
        if(SafeYaml.intValue(config,"schema",1)!=1)throw new IOException("Неизвестная схема blueprints.yml");
        next.maxCells=SafeYaml.longValue(config,"memory.maximum-cells",16000000);next.maxPlanBlocks=SafeYaml.intValue(config,"memory.preview-blocks",100000);
        if(next.maxCells<SchematicReader.MAX_VOLUME||next.maxCells>64000000||next.maxPlanBlocks<1000||next.maxPlanBlocks>1000000)throw new IOException("Неверные ограничения памяти blueprints.yml");
        Path sources=root.resolve("blueprints/source"),archives=root.resolve("blueprints/versions");Files.createDirectories(sources);Files.createDirectories(archives);
        var generator=new BuildingBlueprintGenerator();
        // Load referenced historical versions before new optional imports, preserving their priority in memory.
        for(var town:data.towns().values()){
            for(var site:town.constructionSites().values())if(!site.blueprintSet().isEmpty())next.requireVersion(archives,site.blueprintSet(),site.projectId(),BuildingStyle.ofArchitecture(site.architectureVersion()),generator);
            for(var draft:town.placements().entrySet())if(!draft.getValue().blueprintSet().isEmpty())next.requireVersion(archives,draft.getValue().blueprintSet(),draft.getKey(),null,generator);
        }
        var projects=SafeYaml.section(config,"projects");if(projects!=null)for(String project:projects.getKeys(false)){
            var themes=SafeYaml.section(projects,project);if(themes==null)continue;
            for(String theme:themes.getKeys(false)){
                String label=project+":"+theme;
                try{
                    BuildingStyle style=BuildingStyle.parse(theme);label=key(project,style);if(next.current.containsKey(label)||next.issues.containsKey(label))throw new IOException("Один стиль здания указан дважды");int maximum=generator.maximumStage(project);if(maximum==0)throw new IOException("Неизвестное здание "+project);
                    var section=SafeYaml.section(themes,theme);if(section==null)throw new IOException("Нет раздела levels");var levels=SafeYaml.section(section,"levels");if(levels==null||levels.getKeys(false).size()!=maximum)throw new IOException("Нужны все уровни здания: 1.."+maximum);
                    var models=new TreeMap<Integer,SchematicArchive.Level>();
                    for(int level=1;level<=maximum;level++){
                        String index=Integer.toString(level),file;List<?> origin=List.of();
                        if(levels.isString(index))file=SafeYaml.stringValue(levels,index);
                        else{var entry=SafeYaml.section(levels,index);if(entry==null)throw new IOException("Нет уровня "+level);file=SafeYaml.stringValue(entry,"file");if(entry.contains("origin")){if(!(entry.get("origin") instanceof List<?> list))throw new IOException("origin: [x, z]");origin=list;}}
                        Path source=sources.resolve(file).normalize();if(!source.startsWith(sources)||!Files.isRegularFile(source)||!source.toRealPath().startsWith(sources.toRealPath())||!file.endsWith(".schem"))throw new IOException("Файл .schem не найден внутри blueprints/source");
                        var model=SchematicReader.read(source);int x=model.width()/2,z=model.length()/2;
                        if(!origin.isEmpty()){if(origin.size()!=2||!(origin.get(0) instanceof Integer)||!(origin.get(1) instanceof Integer))throw new IOException("origin: [x, z], две целые координаты внутри схемы");x=(int)origin.get(0);z=(int)origin.get(1);}
                        models.put(level,normalize(model,x,z,project));
                    }
                    validateUpgrades(models);
                    var family=new SchematicArchive.Family(project,style.id(),models);String pin=SchematicArchive.id(family);if(!next.versions.containsKey(pin))next.checkBudget(family);
                    SchematicArchive.save(archives,family);next.add(pin,family);next.current.put(key(project,style),pin);
                    long ignored=family.levels().values().stream().mapToLong(SchematicArchive.Level::ignoredTiles).sum();
                    if(ignored>0)plugin.getLogger().warning(label+": импортируются только блоки; NBT контейнеров, надписей и других блоков не переносится ("+ignored+").");
                }catch(Exception error){next.issues.put(label,error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());plugin.getLogger().warning("Схемы "+label+" недоступны: "+next.issues.get(label));}
            }
        }
        active=next;plugin.getLogger().info("Семейств схем: "+next.current.size()+"; сохранённых версий: "+next.versions.size()+"; ошибок новых схем: "+next.issues.size());
    }}
    private void requireVersion(Path directory,String pin,String project,BuildingStyle style,BuildingBlueprintGenerator generator)throws IOException{
        var family=versions.get(pin);if(family==null){family=SchematicArchive.load(directory,pin);add(pin,family);}
        if(!family.project().equals(project)||style!=null&&!family.style().equals(style.id())||family.levels().size()!=generator.maximumStage(project))throw new IOException("Сохранённая версия не соответствует зданию: "+project+" / "+pin);
        for(var level:family.levels().values())for(String state:level.palette())try{Bukkit.createBlockData(state);}catch(Exception error){throw new IOException("Minecraft не распознаёт блок в закреплённой версии "+pin,error);}
    }
    private void checkBudget(SchematicArchive.Family family)throws IOException{if(family.cells()>maxCells-cells)throw new IOException("Превышен общий объём схем. Увеличьте memory.maximum-cells в blueprints.yml");}
    private void add(String pin,SchematicArchive.Family family)throws IOException{if(versions.containsKey(pin))return;checkBudget(family);versions.put(pin,family);cells+=family.cells();}
    private static SchematicArchive.Level normalize(SchematicReader.Model model,int originX,int originZ,String project)throws IOException{
        var canonical=new HashMap<Integer,String>();var ordered=new TreeSet<String>();
        for(var entry:model.palette().entrySet()){
            org.bukkit.block.data.BlockData data;try{data=Bukkit.createBlockData(entry.getValue());}catch(Exception error){throw new IOException("Minecraft не распознаёт состояние блока: "+entry.getValue(),error);}
            Material material=data.getMaterial();if(FORBIDDEN.contains(material))throw new IOException("В схеме запрещён блок "+material.name());
            if(data instanceof org.bukkit.block.data.type.Leaves leaves)leaves.setPersistent(true);
            String state=material.isAir()?"minecraft:air":data.getAsString();canonical.put(entry.getKey(),state);ordered.add(state);
        }
        var palette=List.copyOf(ordered);var positions=new HashMap<String,Integer>();for(int n=0;n<palette.size();n++)positions.put(palette.get(n),n);
        short[] cells=new short[model.blocks().size()];int nonAir=0,lecterns=0;
        for(int n=0;n<cells.length;n++){String state=canonical.get(model.blocks().get(n));cells[n]=(short)(int)positions.get(state);if(!state.equals("minecraft:air"))nonAir++;if(state.startsWith("minecraft:lectern["))lecterns++;}
        if(nonAir<1||nonAir>30000)throw new IOException("В одном уровне должно быть от 1 до 30000 непустых блоков");
        if(project.equals("town_hall")&&lecterns!=1)throw new IOException("В каждом уровне ратуши нужна ровно одна кафедра");
        return new SchematicArchive.Level(model.width(),model.height(),model.length(),originX,originZ,model.dataVersion(),model.blockEntities(),palette,cells);
    }
    /** Existing structure stays protected during an upgrade. Reject incompatible exports before issuing any draft. */
    private static void validateUpgrades(SortedMap<Integer,SchematicArchive.Level> models)throws IOException{
        SchematicArchive.Level previous=null;
        for(var entry:models.entrySet()){
            var next=entry.getValue();
            if(previous!=null)for(int y=0;y<previous.height();y++)for(int z=0;z<previous.length();z++)for(int x=0;x<previous.width();x++){
                String state=previous.palette().get(previous.code(x+z*previous.width()+y*previous.width()*previous.length()));if(state.equals("minecraft:air"))continue;
                int nx=x-previous.originX()+next.originX(),nz=z-previous.originZ()+next.originZ();
                if(nx<0||nx>=next.width()||nz<0||nz>=next.length()||y>=next.height()||!state.equals(next.palette().get(next.code(nx+nz*next.width()+y*next.width()*next.length()))))throw new IOException("Уровень "+entry.getKey()+" должен сохранять блоки предыдущего уровня. Улучшение может расширять здание и добавлять этажи, но не удалять защищённые блоки.");
            }
            previous=next;
        }
    }
    public static String currentPin(String project,BuildingStyle style){try(var guard=SuiteState.guard()){
        var library=active;String key=key(project,style);if(library.issues.containsKey(key))throw new IllegalStateException("План этого здания сейчас недоступен. Сообщите администратору.");return library.current.getOrDefault(key,"");
    }}
    public static BlueprintPlan sitePlan(ConstructionSite site,int level,BuildingBlueprintGenerator fallback){try(var guard=SuiteState.guard()){
        return site.blueprintSet().isEmpty()?fallback.generateForArchitecture(site.projectId(),level,site.architectureVersion()):active.plan(site.blueprintSet(),site.projectId(),level);
    }}
    public static BlueprintPlan preview(String project,int level,ConstructionSite site,BuildingStyle style,String selectedPin,BuildingBlueprintGenerator fallback){try(var guard=SuiteState.guard()){
        if(site!=null)return sitePlan(site,level,fallback);String pin=selectedPin==null?currentPin(project,style):selectedPin;
        return pin.isEmpty()?fallback.generateForStyle(project,level,style):active.plan(pin,project,level);
    }}
    private BlueprintPlan plan(String pin,String project,int number){
        String key=pin+":"+number;var cached=plans.get(key);if(cached!=null)return cached;
        var family=versions.get(pin);if(family==null||!family.project().equals(project))throw new IllegalStateException("Сохранённый план здания недоступен. Сообщите администратору.");
        var level=family.levels().get(number);if(level==null)throw new IllegalStateException("В сохранённом плане нет этого уровня здания");
        var palette=new ArrayList<BlueprintBlock>();for(String state:level.palette()){
            var data=Bukkit.createBlockData(state);palette.add(data.getMaterial().isAir()?null:new BlueprintBlock(data.getMaterial(),BlockRole.RESIDENT,number,null,null,null,null,state));
        }
        var blocks=new LinkedHashMap<BlockOffset,BlueprintBlock>();
        for(int y=0;y<level.height();y++)for(int z=0;z<level.length();z++)for(int x=0;x<level.width();x++){
            var block=palette.get(level.code(x+z*level.width()+y*level.width()*level.length()));if(block!=null)blocks.put(new BlockOffset(x-level.originX(),y,z-level.originZ()),block);
        }
        var plan=new BlueprintPlan(project,number,"План здания · "+number+" ур.",blocks);
        while(!plans.isEmpty()&&planBlocks+blocks.size()>maxPlanBlocks){var old=plans.remove(plans.keySet().iterator().next());planBlocks-=old.blocks().size();}
        if(blocks.size()<=maxPlanBlocks){plans.put(key,plan);planBlocks+=blocks.size();}return plan;
    }
    public static List<String> diagnostics(){try(var guard=SuiteState.guard()){
        var library=active;var lines=new ArrayList<String>();lines.add("Схемы: "+library.current.size()+" активных семейств; "+library.versions.size()+" версий; "+library.cells+" ячеек в памяти.");
        library.current.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->lines.add(e.getKey()+" → "+e.getValue()));library.issues.forEach((key,error)->lines.add("Ошибка "+key+": "+error));return List.copyOf(lines);
    }}
}
