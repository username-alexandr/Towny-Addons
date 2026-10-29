package ru.neverland.townyarmy;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import static ru.neverland.townyarmy.ArmyModel.*;

/** Upgrade fixtures use the same setDefaults (without copyDefaults) as the actual plugin. */
public final class ArmyDefaultsSmoke {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static YamlConfiguration resource(){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(ArmyDefaultsSmoke.class.getClassLoader().getResourceAsStream("config.yml")),StandardCharsets.UTF_8));}
    private static YamlConfiguration inherited(String text)throws Exception{var old=new YamlConfiguration();old.loadFromString(text);old.setDefaults(resource());return old;}
    private static void denied(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){check(true,message);return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var base=ArmySettings.load(resource());var old=inherited("headquarters-capacity-per-level: 12\nunits:\n  infantry:\n    title: Моя пехота\n");
        var settings=ArmySettings.load(old);
        check(settings.units().size()==Unit.values().length&&settings.ranks().size()==Rank.values().length,"inherited missing units and ranks upgrade without startup failure");
        check(settings.units().get(Unit.AIR).equals(base.units().get(Unit.AIR)),"whole inherited unit keeps original icon capacity requirements and stock costs");
        check(settings.units().get(Unit.INFANTRY).title().equals("Моя пехота")&&settings.units().get(Unit.INFANTRY).icon()==base.units().get(Unit.INFANTRY).icon(),"partial inherited unit preserves administrator name and missing icon");
        check(settings.hqCapacity()==12&&settings.trainingCost().equals(base.trainingCost()),"explicit capacity overrides and default training values remain exact");
        check(old.get("units.air",null)==null&&old.get("units.infantry.icon",null)==null,"resolving defaults does not modify source tree");
        var icon=inherited("units:\n  air:\n    icon: BOW\n");check(ArmySettings.load(icon).units().get(Unit.AIR).icon()==Material.BOW,"explicit valid replacement icon remains intact");
        var invalid=inherited("units:\n  air:\n    icon: not_a_material\n");denied(()->ArmySettings.load(invalid),"explicit unknown icon remains invalid");
        var blank=inherited("units:\n  air:\n    capacity-project: ''\n");denied(()->ArmySettings.load(blank),"explicit empty required building is rejected");
        var empty=inherited("units:\n  air:\n    requirements: []\n");denied(()->ArmySettings.load(empty),"explicit empty wrong-type requirements are not replaced by defaults");
        var zero=inherited("units:\n  air:\n    per-level: 0\n");denied(()->ArmySettings.load(zero),"explicit invalid zero capacity remains invalid");
        System.out.println("ArmyDefaultsSmoke PASS "+checks+" inherited upgrade checks");
    }
}
