package cc.crafto.ui.server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.UUID;

/** Validate the fields the UI consumes; addon release numbers are not a data format. */
final class CityBuildSchema {
    static ConfigurationSection town(YamlConfiguration yaml,UUID id){
        if(!yaml.getKeys(false).isEmpty()&&!yaml.isConfigurationSection("towns"))throw new IllegalArgumentException("Missing builds towns section");
        String path="towns."+id;
        section(yaml,path);var town=yaml.getConfigurationSection(path);if(town==null)return null;
        section(town,"levels");section(town,"construction");
        var levels=town.getConfigurationSection("levels");
        if(levels!=null)for(String key:levels.getKeys(false))if(!levels.isInt(key)||levels.getInt(key)<0)throw new IllegalArgumentException("Invalid building level");
        var sites=town.getConfigurationSection("construction");
        if(sites!=null)for(String key:sites.getKeys(false)){
            if(!sites.isConfigurationSection(key))throw new IllegalArgumentException("Invalid construction site");
            var site=sites.getConfigurationSection(key);
            if(site.contains("active")&&!site.isBoolean("active"))throw new IllegalArgumentException("Invalid construction state");
            if(!site.getBoolean("active"))continue;
            for(String field:new String[]{"x","y","z","completed-stage","target-stage"})if(!site.isInt(field))throw new IllegalArgumentException("Missing site field: "+field);
            if(site.getInt("target-stage")<1||site.getInt("completed-stage")<0||site.getInt("completed-stage")>site.getInt("target-stage"))throw new IllegalArgumentException("Invalid construction stage");
            UUID.fromString(site.getString("world",""));
        }
        return town;
    }
    static int architecture(ConfigurationSection site,int current){
        return architecture(site,current,null);
    }
    /** Builds encodes the saved palette in the architecture ID; let its public API decode it. */
    static int architecture(ConfigurationSection site,int current,Class<?> styleApi){
        if(site.contains("architecture-version")&&!site.isInt("architecture-version"))throw new IllegalArgumentException("Invalid architecture version");
        int value=site.getInt("architecture-version",1);
        String pin=blueprintSet(site);boolean pinned=value>=10000;
        if(value<1||pinned!=!pin.isEmpty())throw new IllegalArgumentException("Unsupported blueprint identity: "+value);
        int local=pinned?value-10000:value,base=local;
        if(styleApi!=null)try{base=((Number)styleApi.getMethod("baseArchitecture",int.class).invoke(null,value)).intValue();}
        catch(ReflectiveOperationException|RuntimeException e){throw new IllegalArgumentException("Unsupported building style: "+value,e);}
        else {if(local/1000>2)throw new IllegalArgumentException("Unsupported style: "+value);base=local%1000;}
        if(base<1||base>current)throw new IllegalArgumentException("Unsupported architecture: "+value);
        return value;
    }
    static String blueprintSet(ConfigurationSection site){
        if(site.contains("blueprint-set")&&!site.isString("blueprint-set"))throw new IllegalArgumentException("Invalid blueprint identity");
        String pin=site.getString("blueprint-set","");
        if(!pin.isEmpty()&&!pin.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid blueprint identity");
        return pin;
    }
    private static void section(ConfigurationSection root,String path){if(root.contains(path)&&!root.isConfigurationSection(path))throw new IllegalArgumentException("Invalid builds section: "+path);}
}
