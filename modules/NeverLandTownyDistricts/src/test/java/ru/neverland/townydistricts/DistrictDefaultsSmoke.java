package ru.neverland.townydistricts;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townydistricts.config.DistrictSettings;
import ru.neverland.townydistricts.model.DistrictType;

/** Real Bukkit inherited defaults, including old catalogs missing entire newly packaged rows. */
public final class DistrictDefaultsSmoke {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static YamlConfiguration resource(String name){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(DistrictDefaultsSmoke.class.getClassLoader().getResourceAsStream(name)),StandardCharsets.UTF_8));}
    private static YamlConfiguration inherited(String name,String text)throws Exception{var old=new YamlConfiguration();old.loadFromString(text);old.setDefaults(resource(name));old.options().copyDefaults(true);return old;}
    private static void denied(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){check(true,message);return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var config=inherited("config.yml","limits:\n  districts-per-town: 7\nproduction:\n  industrial-furnaces: false\n");
        var old=inherited("projects.yml","projects:\n  forge:\n    name: Моя кузница\n  custom_shop:\n    name: Мастерская\n    icon: CHEST\n    district: commercial\n");
        check(old.getConfigurationSection("projects.town_hall").get("district").equals("administrative")&&old.getConfigurationSection("projects.town_hall").getString("district","").isEmpty(),"fixture reproduces inherited row and explicit getter default conflict");
        var settings=DistrictSettings.load(config,old);
        check(settings.projects().size()==102,"all packaged projects and custom project survive old catalog upgrade");
        check(settings.projects().get("town_hall").type()==DistrictType.ADMINISTRATIVE&&settings.projects().get("town_hall").icon().equals("BELL")&&settings.projects().get("town_hall").name().equals("Ратуша"),"whole inherited project preserves its type name and icon");
        check(settings.projects().get("forge").name().equals("Моя кузница")&&settings.projects().get("forge").type()==DistrictType.INDUSTRIAL&&settings.projects().get("forge").icon().equals("ANVIL"),"partial project keeps administrator name and inherits missing fields");
        check(settings.maxDistricts()==7&&!settings.furnaces(),"explicit numeric override and false flag are preserved");
        check(old.get("projects.town_hall.district",null)==null&&config.get("bonuses.matching-building",null)==null,"resolution does not mutate source configuration");
        var invalid=inherited("projects.yml","projects:\n  town_hall:\n    district: not_a_district\n");denied(()->DistrictSettings.load(config,invalid),"explicit unknown district is rejected instead of replaced by defaults");
        var blank=inherited("projects.yml","projects:\n  town_hall:\n    district: ''\n");denied(()->DistrictSettings.load(config,blank),"explicit empty required type stays invalid");
        var empty=inherited("config.yml","bonuses:\n  industrial-requires: []\n");denied(()->DistrictSettings.load(empty,resource("projects.yml")),"explicit empty required list is not silently replaced by bundled combination");
        var disabled=inherited("config.yml","production:\n  agricultural-crops: false\n");check(!DistrictSettings.load(disabled,resource("projects.yml")).crops(),"explicit disabled crop production remains disabled");
        System.out.println("DistrictDefaultsSmoke PASS "+checks+" inherited upgrade checks");
    }
}
