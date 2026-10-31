package cc.crafto.ui;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** Obtain through Bukkit ServicesManager; compileOnly this JAR, never shade it. */
public interface CraftoUIApi {
    void registerMenu(Plugin owner, JsonObject definition);
    void registerAction(Plugin owner, String id, String permission, ActionHandler handler);
    void registerDataProvider(Plugin owner, String namespace, DataProvider provider);
    void unregister(Plugin owner);
    /** Thread-safe entry points. Actual player work is submitted to EntityScheduler. */
    void openMenu(Player player, String menuId);
    void closeMenu(Player player);
    void refresh(Player player);
    boolean hasClient(UUID playerId);

    /** Called on the owning player's entity thread. No blocking I/O permitted. */
    @FunctionalInterface interface ActionHandler { ActionResult execute(ActionContext context) throws Exception; }
    record ActionContext(Player player, CraftoUIApi api, String menuId, String componentId, JsonObject arguments) {}
    record ActionResult(boolean success, String message) {
        public static ActionResult ok(String text) { return new ActionResult(true,text); }
        public static ActionResult error(String text) { return new ActionResult(false,text); }
    }
    /** Called asynchronously. Only the immutable snapshot is available; never access Bukkit world/player state here. */
    @FunctionalInterface interface DataProvider { CompletionStage<Map<String,String>> load(PlayerSnapshot player); }
    record PlayerSnapshot(UUID uuid, String name, Map<String,String> values) {
        public PlayerSnapshot { values=Map.copyOf(values); }
    }
}
