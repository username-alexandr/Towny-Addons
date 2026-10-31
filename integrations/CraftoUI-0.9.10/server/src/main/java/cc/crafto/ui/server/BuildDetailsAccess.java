package cc.crafto.ui.server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
/** Capability check rather than a version-number comparison (forks may backport the API). */
final class BuildDetailsAccess {
    static boolean supported(Class<?> type){try{type.getMethod("uiOpenDetails",Player.class,String.class);return true;}catch(NoSuchMethodException|LinkageError e){return false;}}
    static String reason(Plugin addon){
        if(addon==null)return "Аддон построек недоступен.";
        if(supported(addon.getClass()))return "";
        return "На сервере Builds "+CivicData.clean(addon.getPluginMeta().getVersion())+" без перехода к отдельной стройке. Нужен 0.8.29+ с API; пока откройте «Все постройки».";
    }
}
