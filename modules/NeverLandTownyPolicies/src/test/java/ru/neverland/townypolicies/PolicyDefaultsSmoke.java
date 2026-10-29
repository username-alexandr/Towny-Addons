package ru.neverland.townypolicies;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townypolicies.config.PoliciesSettings;

/** Missing bundled options inherit their quest gates; explicit administrator choices remain authoritative. */
public final class PolicyDefaultsSmoke {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static YamlConfiguration resource(String name){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(PolicyDefaultsSmoke.class.getClassLoader().getResourceAsStream(name)),StandardCharsets.UTF_8));}
    private static YamlConfiguration inherited(String text)throws Exception{var old=new YamlConfiguration();old.loadFromString(text);old.setDefaults(resource("policies.yml"));old.options().copyDefaults(true);return old;}
    private static void denied(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){check(true,message);return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var config=resource("config.yml");var expected=PoliciesSettings.load(config,resource("policies.yml"));var old=inherited("policies:\n  sanitation:\n    name: Медицина моего города\n");var actual=PoliciesSettings.load(config,old);int gated=0;
        for(var group:expected.groups().values())for(var option:group.options().values())if(!option.unlock().isEmpty()){gated++;check(actual.groups().get(group.id()).options().get(option.id()).unlock().equals(option.unlock()),"inherited quest requirement retained for "+group.id()+"/"+option.id());}
        check(gated==4,"fixture covers all four packaged quest-gated modes");
        check(actual.groups().get("sanitation").name().equals("Медицина моего города"),"partial group preserves custom administrator title");
        check(old.get("policies.sanitation.options.sanitary.requires-unlock",null)==null,"default resolution does not rewrite the original settings");
        var field=resource("policies.yml");field.set("policies.sanitation.options.sanitary.requires-unlock",null);field.setDefaults(resource("policies.yml"));field.options().copyDefaults(true);check(PoliciesSettings.load(config,field).groups().get("sanitation").options().get("sanitary").unlock().equals("sanitation_reform"),"missing field in existing mode inherits original earned unlock");
        var blank=inherited("policies:\n  sanitation:\n    options:\n      sanitary:\n        requires-unlock: ''\n");check(PoliciesSettings.load(config,blank).groups().get("sanitation").options().get("sanitary").unlock().isEmpty(),"explicit administrator removal of optional unlock remains authoritative");
        var disabled=inherited("policies:\n  sanitation:\n    enabled: false\n");check(!PoliciesSettings.load(config,disabled).groups().get("sanitation").enabled(),"explicit disabled group stays disabled");
        var empty=inherited("policies:\n  sanitation:\n    options:\n      sanitary:\n        production-projects: []\n");check(PoliciesSettings.load(config,empty).groups().get("sanitation").options().get("sanitary").production().isEmpty(),"explicit empty optional project list stays empty");
        var invalid=inherited("policies:\n  sanitation:\n    options:\n      sanitary:\n        requires-unlock: 'BAD UNLOCK'\n");denied(()->PoliciesSettings.load(config,invalid),"explicit malformed unlock is rejected");
        var missing=inherited("policies:\n  sanitation:\n    options:\n      sanitary:\n        requires: []\n");denied(()->PoliciesSettings.load(config,missing),"inherited unlock cannot bypass explicit missing Quests dependency");
        System.out.println("PolicyDefaultsSmoke PASS "+checks+" inherited upgrade checks");
    }
}
