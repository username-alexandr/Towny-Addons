package ru.neverland.townyspecialization.integration;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.neverland.townyspecialization.service.*;
import java.util.Locale;
public final class SpecializationExpansion extends PlaceholderExpansion {
    private final SpecializationService service;private final String version;public SpecializationExpansion(SpecializationService service,String version){this.service=service;this.version=version;}
    @Override public String getIdentifier(){return "nltspecialization";}@Override public String getAuthor(){return "Alexander Sokolov";}@Override public String getVersion(){return version;}@Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer p,String parameters){if(p==null)return "";var s=service.residentSpecialization(p.getUniqueId()).orElse(null);if(s==null)return "";String key=parameters.toLowerCase(Locale.ROOT);var profile=service.settings().profiles().get(s.state().specialization());if(key.startsWith("bonus_"))return EffectNames.value(key.substring(6),service.bonus(s.townId(),key.substring(6)));
        return switch(key){case "name"->profile==null?"Не выбрана":profile.name();case "id"->s.state().specialization();case "town_level"->String.valueOf(s.townLevel());case "hall_level"->String.valueOf(s.hallLevel());case "building"->profile==null?"Нет":profile.buildingName();case "building_level"->String.valueOf(s.uniqueLevel());case "cooldown"->String.valueOf(Math.max(0,(s.state().nextChangeAt()-System.currentTimeMillis()+999)/1000));case "status"->s.status();default->null;};}
}
