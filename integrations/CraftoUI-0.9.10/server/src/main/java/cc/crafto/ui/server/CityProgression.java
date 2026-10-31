package cc.crafto.ui.server;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Read-only catalog. The addons remain authoritative for construction and research actions. */
final class CityProgression {
    record Node(String id,String title,String kind,String category,String detail,int level,int maximum,Map<String,Integer> buildings,Map<String,Integer> studies){}
    record Snapshot(List<Node> buildings,List<Node> studies,String problem,String researchProblem) {
        Snapshot(List<Node> buildings,List<Node> studies,String problem){this(buildings,studies,problem,"");}
        static Snapshot unavailable(String reason){return new Snapshot(List.of(),List.of(),reason);}
        void populate(CivicView view,Map<String,Integer> built){
            view.title="Дерево развития города";
            if(!problem.isEmpty()){view.rows.add(CivicView.Row.info("Дерево недоступно",problem));return;}
            var visible=new ArrayList<Node>();
            for(var node:buildings)if(revealed(node,built,Map.of())||frontier(node,buildings,built,Map.of()))visible.add(node);
            var learned=new HashMap<String,Integer>();for(var node:studies)learned.put(node.id(),node.level());
            for(var node:studies)if(revealed(node,built,learned)||frontier(node,buildings,built,learned))visible.add(node);
            if(buildings.stream().anyMatch(n->n.id().equals("market"))&&built.getOrDefault("market",0)>=1)
                visible.add(new Node("market","Городской рынок","content","Торговля","Предложения жителей и получение покупок",1,1,Map.of("market",1),Map.of()));
            if(buildings.stream().anyMatch(n->n.id().equals("merchant_guild"))&&built.getOrDefault("merchant_guild",0)>=1&&built.getOrDefault("market",0)>=2)
                visible.add(new Node("global-market","Международные предложения","content","Торговля","Торговля с другими городами",1,1,Map.of("merchant_guild",1,"market",2),Map.of()));
            view.tree=new com.google.gson.JsonArray();
            int index=0;
            for(var node:visible){
                String action="t"+index++;
                view.actionsForTree.put(action,new CivicView.Action(switch(node.kind()){case "building"->"build";case "research"->"open-research";default->"open-content";},node.id()));
                var requirements=new ArrayList<String>();
                node.buildings().forEach((id,n)->{if(built.getOrDefault(id,0)<n)requirements.add("Здание "+name(buildings,id)+" "+built.getOrDefault(id,0)+"/"+n);});
                node.studies().forEach((id,n)->{if(learned.getOrDefault(id,0)<n)requirements.add("Исследование "+name(studies,id)+" "+learned.getOrDefault(id,0)+"/"+n);});
                String status=node.level()>0?"Открыто · уровень "+node.level():requirements.isEmpty()?"Начальная ветвь":ready(node,built,learned)?"Доступно":"Нужно открыть предыдущие узлы";
                view.tree.add(object("id",node.kind()+":"+node.id(),"title",node.title(),"kind",node.kind(),"category",node.category(),"detail",node.detail(),"unique",node.detail().startsWith("Уникальное здание:"),"status",status,"level",node.level(),"maximum",node.maximum(),"requires",requirements,"parents",parents(node),"action",action));
            }
            view.lines.add("Новые ветви появляются после строительства нужных зданий и изучения предыдущих технологий. Выберите узел для подробностей.");
            if(!researchProblem.isBlank())view.lines.add(researchProblem);
        }
        private static String name(List<Node> nodes,String id){return nodes.stream().filter(n->n.id().equals(id)).map(Node::title).findFirst().orElse(id);}
    }
    static boolean ready(Node node,Map<String,Integer> built,Map<String,Integer> learned){
        return node.buildings().entrySet().stream().allMatch(e->built.getOrDefault(e.getKey(),0)>=e.getValue())&&node.studies().entrySet().stream().allMatch(e->learned.getOrDefault(e.getKey(),0)>=e.getValue());
    }
    static boolean revealed(Node node,Map<String,Integer> built,Map<String,Integer> learned){
        // Existing cities keep already built buildings visible after the catalog is upgraded.
        // New nodes appear only when every required building and study is unlocked.
        return node.level()>0||ready(node,built,learned);
    }
    /** Show only the next locked branches, never the entire future catalog. */
    static boolean frontier(Node node,List<Node> buildings,Map<String,Integer> built,Map<String,Integer> learned){
        for(String id:node.buildings().keySet()){
            if(built.getOrDefault(id,0)>0)return true;
            if(buildings.stream().anyMatch(parent->parent.id().equals(id)&&parent.buildings().isEmpty()&&parent.studies().isEmpty()))return true;
        }
        return node.studies().keySet().stream().anyMatch(id->learned.getOrDefault(id,0)>0);
    }
    private static List<String> parents(Node node){var out=new ArrayList<String>();node.buildings().keySet().forEach(id->out.add("building:"+id));node.studies().keySet().forEach(id->out.add("research:"+id));return out;}
    static Snapshot read(Plugin builds,Plugin research,UUID town,List<DevelopmentView.Building> catalog){
        if(builds==null||!builds.isEnabled())return Snapshot.unavailable("Аддон построек недоступен");
        try{
            var projects=config(builds,"projects.yml").getConfigurationSection("buildings");
            var bundled=bundled(builds,"projects.yml").getConfigurationSection("buildings");
            if(projects==null)throw new IllegalArgumentException("Нет списка зданий");
            var buildingNodes=new ArrayList<Node>();
            for(var item:catalog){
                var section=projects.getConfigurationSection(item.id());if(section==null)continue;
                var detail=section.getStringList("description");
                var defaults=bundled==null?null:bundled.getConfigurationSection(item.id());
                // Builds inherits absent requirements from its bundled catalog when loading an old
                // projects.yml. Mirror that exact rule, otherwise the graph and build checks diverge.
                var required=section.contains("requires")?section.getConfigurationSection("requires"):defaults==null?null:defaults.getConfigurationSection("requires");
                String sectionCategory=section.getString("category",defaults==null?"Базовые":defaults.getString("category","Базовые"));
                buildingNodes.add(new Node(item.id(),item.name(),"building",category(sectionCategory),detail.isEmpty()?"Постройка города":detail.getFirst(),item.current(),item.maximum(),dependencies(required),Map.of()));
            }
            var researchNodes=new ArrayList<Node>();String researchProblem="";
            if(research==null||!research.isEnabled())researchProblem="Исследования недоступны: проверьте аддон NeverLandTownyResearch.";
            else if(town!=null)try{
                Map<String,Integer> learned=learned(research,town);
                var technologies=config(research,"technologies.yml").getConfigurationSection("technologies");
                if(technologies==null)throw new IllegalArgumentException("Нет технологий");
                for(String id:technologies.getKeys(false)){
                    var section=technologies.getConfigurationSection(id);if(section==null||!section.getBoolean("enabled",true))continue;
                    var levels=section.getConfigurationSection("levels");int current=learned.getOrDefault(id,0);
                    if(levels==null||levels.getKeys(false).isEmpty())continue;
                    int maximum=levels.getKeys(false).size(),next=Math.min(maximum,current+1);
                    var level=levels.getConfigurationSection(Integer.toString(next));if(level==null)continue;
                    researchNodes.add(new Node(id,section.getString("name",id),"research","Исследования",section.getString("description",""),current,maximum,dependencies(level.getConfigurationSection("buildings")),dependencies(level.getConfigurationSection("requires"))));
                }
            }catch(Exception|LinkageError e){
                researchNodes.clear();
                researchProblem="Исследования временно недоступны. Здания остаются видны; проверьте аддон Research и technologies.yml.";
            }
            return new Snapshot(List.copyOf(buildingNodes),List.copyOf(researchNodes),"",researchProblem);
        }catch(Exception e){return Snapshot.unavailable("Не удалось прочитать зависимости аддонов. Проверьте projects.yml и technologies.yml.");}
    }
    private static String category(String value){return switch(value.toUpperCase(Locale.ROOT)){case "ENERGY"->"Энергия";case "DEFENSE"->"Оборона";case "PRODUCTION"->"Производство";case "TRADE"->"Торговля";case "INFRASTRUCTURE"->"Инфраструктура";case "SCIENCE"->"Наука";case "CULTURE"->"Культура";case "ADMINISTRATION"->"Управление";case "CIVIC"->"Город";default->value.equals("Базовые")?value:value.replace('_',' ');};}
    private static Map<String,Integer> dependencies(ConfigurationSection section){
        if(section==null)return Map.of();if(section.getKeys(false).size()>32)throw new IllegalArgumentException();
        var out=new TreeMap<String,Integer>();for(String key:section.getKeys(false)){int level=section.getInt(key);if(!key.matches("[a-z0-9_-]{1,64}")||level<1||level>32)throw new IllegalArgumentException();out.put(key,level);}return Map.copyOf(out);
    }
    private static YamlConfiguration config(Plugin plugin,String name)throws IOException{
        Path path=plugin.getDataFolder().toPath().resolve(name);
        try(Reader reader=Files.exists(path)?Files.newBufferedReader(path,StandardCharsets.UTF_8):new InputStreamReader(Objects.requireNonNull(plugin.getResource(name)),StandardCharsets.UTF_8)){
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
    private static YamlConfiguration bundled(Plugin plugin,String name)throws IOException{
        try(Reader reader=new InputStreamReader(Objects.requireNonNull(plugin.getResource(name)),StandardCharsets.UTF_8)){
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
    private static Map<String,Integer> learned(Plugin plugin,UUID town)throws ReflectiveOperationException{
        var type=Class.forName("ru.neverland.townyresearch.api.TownyResearchApi",false,plugin.getClass().getClassLoader());
        var registration=plugin.getServer().getServicesManager().getRegistration(type);
        if(registration==null||registration.getPlugin()!=plugin)throw new IllegalArgumentException("Исследования недоступны");
        var optional=(Optional<?>)type.getMethod("research",UUID.class).invoke(registration.getProvider(),town);
        if(optional.isEmpty())throw new IllegalArgumentException("Состояние исследований ещё загружается");
        Object snapshot=optional.get(),state=snapshot.getClass().getMethod("state").invoke(snapshot);
        var map=(Map<?,?>)state.getClass().getMethod("learned").invoke(state);
        var out=new HashMap<String,Integer>();map.forEach((k,v)->{if(k instanceof String key&&v instanceof Integer n)out.put(key,n);});return Map.copyOf(out);
    }
}
