package cc.crafto.ui.server;

import com.google.gson.JsonObject;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Shared policy for existing configurations, API menus, search and action checks. */
final class NavigationPolicy {
    private static final Set<String> TOWN_MENUS=Set.of("town","society","development","security");
    private static final Set<String> TOWN_COMMANDS=Set.of(
        "town builds","town inv","town contracts","town trade","town events","town ideologies",
        "townydistricts","townypopulation","townyspecialization","townyresearch","townypolicies",
        "townyresources","townypower","townyupkeep","townylogistics","townyenvironment",
        "townytreasury","townyjobs","townyquests","townyachievements","chronicles","townycitizens",
        "reputation","townycouncil","elections","governance","governance council","townydiplomacy",
        "townycrime","townyjustice","townymilitary","espionage","townysieges");
    static boolean requiresTown(JsonObject node){
        if(node==null)return false;
        if(node.has("requiresTown"))return bool(node,"requiresTown",false);
        if(node.has("components")&&TOWN_MENUS.contains(str(node,"id","")))return true;
        return switch(str(node,"type","")){
            case "city"->true;
            case "civic"->cc.crafto.ui.TownUi.requiresTown(str(node,"route","city"));
            case "open"->TOWN_MENUS.contains(str(node,"menu",""));
            case "player-command"->{String cmd=command(str(node,"command",""),true).toLowerCase(Locale.ROOT);yield TOWN_COMMANDS.stream().anyMatch(base->cmd.equals(base)||cmd.startsWith(base+" "));}
            default->false;
        };
    }
    static boolean townMismatch(JsonObject node,boolean member){return node!=null&&(requiresTown(node)&&!member||bool(node,"requiresNoTown",false)&&member);}
    private static String command(String value,boolean unqualified){
        String text=value.strip().replaceFirst("^/","").replaceAll("\\s+"," ");
        int space=text.indexOf(' ');String root=(space<0?text:text.substring(0,space)).toLowerCase(Locale.ROOT);
        if(unqualified&&root.contains(":"))root=root.substring(root.indexOf(':')+1);
        return root+(space<0?"":text.substring(space));
    }
    /** Only fixed navigation destinations are merged; arbitrary handlers never are. */
    static String destination(JsonObject action){
        if(action==null)return "";
        return switch(str(action,"type","")){
            case "open"->"menu:"+str(action,"menu","");
            case "city"->"city";
            case "civic"->"civic:"+str(action,"route","");
            case "settings"->"client:settings";
            case "commands"->str(action,"form","").isBlank()?"client:commands":"command-form:"+str(action,"form","");
            case "close"->"client:close";
            case "refresh"->"menu:refresh";
            case "player-command"->bool(action,"closeBefore",false)?"command:"+str(action,"requiredPlugin","")+":"+command(str(action,"command",""),false):"";
            default->"";
        };
    }
}
