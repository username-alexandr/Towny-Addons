package ru.neverland.townysieges;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;
public final class CampSettingsDefaultsSmoke {
 public static void main(String[] args)throws Exception{var defaults=new YamlConfiguration();try(var in=CampSettingsDefaultsSmoke.class.getResourceAsStream("/config.yml")){defaults.load(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));}var direct=CampSupplies.Settings.load(defaults);if(direct.cargo().isEmpty()||direct.army().isEmpty())throw new AssertionError("fresh defaults");var legacy=new YamlConfiguration();legacy.set("tnt-raids.enabled",true);legacy.setDefaults(defaults);var merged=CampSupplies.Settings.load(legacy);if(!direct.equals(merged))throw new AssertionError("legacy configuration receives the same explicit defaults");System.out.println("CampSettingsDefaultsSmoke PASS: fresh and old configs with defaults produce identical complete costs");}
}
