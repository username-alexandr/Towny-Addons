package ru.neverland.townypolicies.integration;
import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import ru.neverland.townypolicies.service.PoliciesService;
import java.util.*;
public final class PoliciesExpansion extends PlaceholderExpansion {
    private final PoliciesService service;private final String version;public PoliciesExpansion(PoliciesService service,String version){this.service=service;this.version=version;}
    @Override public String getIdentifier(){return "nltpolicies";}@Override public String getAuthor(){return "Alexander Sokolov";}@Override public String getVersion(){return version;}@Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer p,String raw){if(p==null)return "";var s=service.residentPolicies(p.getUniqueId()).orElse(null);if(s==null)return "";String key=raw.toLowerCase(Locale.ROOT);if(key.equals("happiness"))return String.valueOf(service.effect(s.townId(),"happiness"));if(key.equals("tax_multiplier"))return String.valueOf(service.taxMultiplier(s.townId()));if(key.equals("revision"))return String.valueOf(s.state().revision());for(var g:service.settings().groups().values()){String mode=s.state().mode(g);var option=g.options().get(mode);if(key.equals(g.id()))return option==null?"Режим отсутствует":option.name();if(key.equals(g.id()+"_id"))return mode;if(key.equals(g.id()+"_status"))return s.status().get(g.id());if(key.equals(g.id()+"_cooldown"))return String.valueOf(Math.max(0,(s.state().next(g.id())-System.currentTimeMillis()+999)/1000));}return null;}
}
