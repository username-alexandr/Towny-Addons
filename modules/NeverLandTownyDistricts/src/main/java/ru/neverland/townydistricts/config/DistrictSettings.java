package ru.neverland.townydistricts.config;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townydistricts.model.DistrictType;
import java.util.*;
public record DistrictSettings(int maxDistricts,int maxCells,int maxSelection,double matching,double combination,
                               double maximum,Set<String> requires,boolean furnaces,boolean crops,Map<String,Project> projects){
    public record Project(String name,String icon,DistrictType type){}
    public DistrictSettings{requires=Set.copyOf(requires);projects=Collections.unmodifiableMap(new LinkedHashMap<>(projects));}
    public Map<String,DistrictType> types(){Map<String,DistrictType> result=new HashMap<>();projects.forEach((k,v)->result.put(k,v.type()));return result;}
    public static DistrictSettings load(YamlConfiguration c,YamlConfiguration catalog){
        Map<String,Project> projects=new LinkedHashMap<>();var section=catalog.getConfigurationSection("projects");
        if(section==null)throw new IllegalArgumentException("Нет раздела projects");
        for(String id:section.getKeys(false)){
            var p=section.getConfigurationSection(id);if(p==null||!id.matches("[a-z0-9_]+"))throw new IllegalArgumentException("Некорректный проект "+id);
            projects.put(id,new Project(p.getString("name",id),p.getString("icon","BRICKS"),DistrictType.parse(p.getString("district",""))));
        }
        var requires=new HashSet<>(c.getStringList("bonuses.industrial-requires"));
        if(requires.isEmpty()||requires.stream().anyMatch(id->!projects.containsKey(id)||projects.get(id).type()!=DistrictType.INDUSTRIAL))
            throw new IllegalArgumentException("Участники промышленной связки должны быть промышленными проектами");
        return new DistrictSettings(integer(c,"limits.districts-per-town",1,128),integer(c,"limits.cells-per-district",1,4096),
                integer(c,"limits.selection-cells",1,4096),number(c,"bonuses.matching-building",0,2),
                number(c,"bonuses.industrial-combination",0,2),number(c,"bonuses.maximum-multiplier",1,3),requires,
                c.getBoolean("production.industrial-furnaces",true),c.getBoolean("production.agricultural-crops",true),projects);
    }
    private static int integer(YamlConfiguration c,String key,int min,int max){double n=number(c,key,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException(key+": требуется целое число");return(int)n;}
    private static double number(YamlConfiguration c,String key,double min,double max){Object raw=c.get(key);if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IllegalArgumentException(key+": требуется число "+min+".."+max);return n.doubleValue();}
}
