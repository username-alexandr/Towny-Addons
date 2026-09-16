package ru.neverland.townyseasons;

import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.plugin.Plugin;

/** Read-only documented SeasonsAPI; no dependency on provider internals or world mutation. */
public final class RealisticSeasonsAccess {
    private Plugin provider;
    private Method instance, season;
    private String status="NOT_QUERIED";
    public String status() { return status; }
    public Season read(World world) {
        var current=Bukkit.getPluginManager().getPlugin("RealisticSeasons");
        if(current==null)throw failure("NOT_INSTALLED",null);
        if(!current.isEnabled())throw failure("DISABLED",null);
        try {
            if(current!=provider) {
                var type=Class.forName("me.casperge.realisticseasons.api.SeasonsAPI",true,current.getClass().getClassLoader());
                var factory=type.getMethod("getInstance");var query=type.getMethod("getSeason",World.class);
                if(!Modifier.isStatic(factory.getModifiers())||!query.getReturnType().isEnum())throw new NoSuchMethodException("SeasonsAPI signature");
                instance=factory;season=query;provider=current;
            }
        } catch(ReflectiveOperationException|LinkageError e) { provider=null;throw failure("INCOMPATIBLE_API",e); }
        try {
            Object api=instance.invoke(null);if(api==null)throw new IllegalStateException("API unavailable");
            Object value=season.invoke(api,world);
            if(!(value instanceof Enum<?> e))throw new IllegalStateException("Season unavailable in world");
            Season result=Season.parse(e.name());status="READY";return result;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { throw failure("INVOCATION_ERROR",e); }
    }
    private IllegalStateException failure(String state,Throwable cause) {
        status=state;return new IllegalStateException("RealisticSeasons: "+state,cause);
    }
}
