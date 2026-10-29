package ru.neverland.townyresources;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyresources.config.ResourcesSettings;
public final class ResourcesDefaultsSmoke {
    private static int checks;private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static YamlConfiguration resource(String name){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(ResourcesDefaultsSmoke.class.getClassLoader().getResourceAsStream(name)),StandardCharsets.UTF_8));}
    private static YamlConfiguration inherited(String text)throws Exception{var old=new YamlConfiguration();old.loadFromString(text);old.setDefaults(resource("buildings.yml"));old.options().copyDefaults(true);return old;}
    private static void denied(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){check(true,message);return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var config=resource("config.yml");var expected=ResourcesSettings.load(config,resource("buildings.yml"));var old=inherited("buildings:\n  town_hall:\n    name: Моя ратуша\n");
        check(old.getConfigurationSection("buildings.forge").get("priority").equals(20)&&old.getConfigurationSection("buildings.forge").get("priority",50).equals(50),"fixture reproduces bypassed inherited priority");
        var actual=ResourcesSettings.load(config,old);check(actual.buildings().size()==expected.buildings().size(),"all missing packaged production profiles inherited");
        check(actual.buildings().get("forge").equals(expected.buildings().get("forge")),"whole inherited forge keeps exact priority input output capacity and limits");
        check(actual.buildings().get("town_hall").name().equals("Моя ратуша")&&actual.buildings().get("town_hall").priority()==40,"partial profile keeps administrator title and missing priority");
        check(actual.initial().equals(expected.initial())&&actual.baseCapacity().equals(expected.baseCapacity())&&actual.baseProduction().equals(expected.baseProduction()),"initial stocks and base production are unchanged");
        check(old.get("buildings.forge.priority",null)==null,"input configuration not rewritten");
        var custom=inherited("buildings:\n  forge:\n    priority: 2\n    enabled: false\n    minimum-level: 3\n");var p=ResourcesSettings.load(config,custom).buildings().get("forge");check(p.priority()==2&&!p.enabled()&&p.minimumLevel()==3,"explicit priority disabled flag and minimum level preserved");
        var zero=inherited("buildings:\n  forge:\n    priority: 0\n");check(ResourcesSettings.load(config,zero).buildings().get("forge").priority()==0,"valid explicit zero priority is preserved");
        var bad=inherited("buildings:\n  forge:\n    priority: -1\n");denied(()->ResourcesSettings.load(config,bad),"explicit invalid priority rejected");
        var empty=inherited("buildings:\n  forge:\n    consumes: []\n");denied(()->ResourcesSettings.load(config,empty),"explicit wrong-type resource list not replaced by defaults");
        System.out.println("ResourcesDefaultsSmoke PASS "+checks+" inherited upgrade checks");
    }
}
