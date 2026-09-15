package ru.neverland.townysieges;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.palmergames.bukkit.towny.object.Town;

/** Optional adapter for SiegeWar 3.x public API and its public Siege/SiegeSide model. No controller or private reflection. */
public final class SiegeWarAccess {
    public enum State { NOT_INSTALLED, DISABLED, INCOMPATIBLE_API, INVOCATION_ERROR, READY }
    private State state=State.NOT_INSTALLED;
    private String detail="SiegeWar не установлен";
    private Plugin bound;
    private Class<?> siegeType;
    private Method getSiege, active, battle, side;
    public State state() { return state; }
    public String detail() { return detail; }
    private void status(State next,String text) {
        if (state!=next && next!=State.READY && next!=State.NOT_INSTALLED) Bukkit.getLogger().warning("[NeverLand Sieges+] SiegeWar: "+next+" — "+text);
        state=next; detail=text;
    }
    public boolean connect() {
        Plugin plugin=Bukkit.getPluginManager().getPlugin("SiegeWar");
        if (plugin==null) { bound=null; status(State.NOT_INSTALLED,"SiegeWar не установлен"); return false; }
        if (!plugin.isEnabled()) { bound=null; status(State.DISABLED,"SiegeWar отключён"); return false; }
        if (plugin==bound) return true;
        try {
            if (!plugin.getDescription().getVersion().matches("3\\.[0-9]+\\.[0-9]+(?:[-+].*)?")) throw new IllegalStateException("Поддерживается SiegeWar 3.x; проверена версия 3.6.2");
            var loader=plugin.getClass().getClassLoader();
            var api=Class.forName("com.gmail.goosius.siegewar.SiegeWarAPI",false,loader);
            siegeType=Class.forName("com.gmail.goosius.siegewar.objects.Siege",false,loader);
            var sides=Class.forName("com.gmail.goosius.siegewar.enums.SiegeSide",false,loader);
            getSiege=api.getMethod("getSiegeOrNull",Town.class); active=api.getMethod("isActive",siegeType);
            battle=api.getMethod("isBattleSessionActive"); side=sides.getMethod("getPlayerSiegeSide",siegeType,Player.class);
            if (getSiege.getReturnType()!=siegeType || active.getReturnType()!=boolean.class || battle.getReturnType()!=boolean.class || side.getReturnType()!=sides) throw new NoSuchMethodException("Изменён тип результата SiegeWar API");
            for (Method m:List.of(getSiege,active,battle,side)) if (!Modifier.isStatic(m.getModifiers())) throw new NoSuchMethodException(m.getName());
            bound=plugin; status(State.READY,"SiegeWar "+plugin.getDescription().getVersion()); return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { bound=null; status(State.INCOMPATIBLE_API,e.toString()); return false; }
    }
    public record Context(boolean active, boolean battle, String side) { public static Context none() { return new Context(false,false,"NOBODY"); } }
    public Context context(Town town,Player player) {
        ru.neverland.core.ApiServices.primaryThread();
        if (!connect()) throw new IllegalStateException(detail);
        try {
            Object siege=getSiege.invoke(null,town);
            if (siege==null) { status(State.READY,"SiegeWar "+bound.getDescription().getVersion()); return Context.none(); }
            boolean ongoing=(boolean)active.invoke(null,siege), fighting=(boolean)battle.invoke(null);
            String allegiance=player==null?"NOBODY":((Enum<?>)side.invoke(null,siege,player)).name();
            if (!Set.of("ATTACKERS","DEFENDERS","NOBODY").contains(allegiance)) throw new IllegalStateException("Неизвестная сторона осады");
            status(State.READY,"SiegeWar "+bound.getDescription().getVersion()); return new Context(ongoing,fighting,allegiance);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { status(State.INVOCATION_ERROR,e.toString()); throw new IllegalStateException("Не удалось проверить сторону осады",e); }
    }
}
