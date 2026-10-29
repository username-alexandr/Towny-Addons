package ru.neverland.townyupkeep;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyupkeep.config.UpkeepSettings;

/** Old building catalogs must not give newly inherited buildings free monetary upkeep. */
public final class UpkeepDefaultsSmoke {
    private static int checks;private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static YamlConfiguration resource(String name){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(UpkeepDefaultsSmoke.class.getClassLoader().getResourceAsStream(name)),StandardCharsets.UTF_8));}
    private static YamlConfiguration inherited(String name,String text)throws Exception{var old=new YamlConfiguration();old.loadFromString(text);old.setDefaults(resource(name));old.options().copyDefaults(true);return old;}
    private static void denied(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){check(true,message);return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var config=resource("config.yml");var expected=UpkeepSettings.load(config,resource("buildings.yml"));var old=inherited("buildings.yml","buildings:\n  forge:\n    name: Моя кузница\n");var actual=UpkeepSettings.load(config,old);
        check(actual.profiles().size()==expected.profiles().size(),"missing packaged building profiles inherited");
        check(expected.profiles().get("town_hall").cost().money()==500&&actual.profiles().get("town_hall").equals(expected.profiles().get("town_hall")),"whole inherited town hall retains money resources priority and icon");
        check(actual.profiles().get("forge").name().equals("Моя кузница")&&actual.profiles().get("forge").cost().equals(expected.profiles().get("forge").cost()),"partial old profile keeps name and exact missing price");
        check(old.get("buildings.town_hall.money",null)==null,"resolution does not modify administrator input");
        var free=inherited("buildings.yml","buildings:\n  town_hall:\n    money: 0\n");check(UpkeepSettings.load(config,free).profiles().get("town_hall").cost().money()==0,"explicit free upkeep remains administrator choice");
        var override=inherited("buildings.yml","buildings:\n  town_hall:\n    money: 7.25\n    enabled: false\n    priority: 3\n");var p=UpkeepSettings.load(config,override).profiles().get("town_hall");check(p.cost().money()==725&&!p.enabled()&&p.priority()==3,"explicit money disabled flag and priority preserved exactly");
        var invalid=inherited("buildings.yml","buildings:\n  town_hall:\n    money: -1\n");denied(()->UpkeepSettings.load(config,invalid),"explicit negative money remains invalid");
        var blank=inherited("buildings.yml","buildings:\n  town_hall:\n    money: ''\n");denied(()->UpkeepSettings.load(config,blank),"explicit empty money remains invalid");
        var empty=inherited("buildings.yml","buildings:\n  town_hall:\n    resources: []\n");denied(()->UpkeepSettings.load(config,empty),"explicit wrong-type resource list is not replaced");
        System.out.println("UpkeepDefaultsSmoke PASS "+checks+" inherited upgrade checks");
    }
}
