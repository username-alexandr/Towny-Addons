package ru.neverland.townybuilds.integration;

import java.util.UUID;
import ru.neverland.core.ApiServices;

/** Optional public Events API; an unavailable installed provider must never erase repair debts. */
public final class FireRepairAccess {
    private FireRepairAccess() { }
    public static boolean blocked(UUID town, ru.neverland.townybuilds.construction.ConstructionSite site, ru.neverland.townybuilds.construction.BlueprintPlan plan) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if(site==null || plan==null || plan.blocks().isEmpty())return blocked(town);
        int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(var offset:plan.blocks().keySet()) {var l=site.location(null,offset);minX=Math.min(minX,l.getBlockX());minZ=Math.min(minZ,l.getBlockZ());maxX=Math.max(maxX,l.getBlockX());maxZ=Math.max(maxZ,l.getBlockZ());}
        var api=ApiServices.connect("NeverLandTownyEvents","ru.neverland.mintevents.api.MintTownyEventsApi",1,"hasFireDamageInArea");
        if(api.state()==ApiServices.State.NOT_INSTALLED)return false;
        if(!api.ready())return blocked(town);
        try {return !Boolean.FALSE.equals(api.invoke("hasFireDamageInArea",new Class<?>[]{UUID.class,UUID.class,int.class,int.class,int.class,int.class},town,site.worldId(),minX,minZ,maxX,maxZ));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError error){return true;}
    } }
    public static boolean blocked(UUID town) { synchronized (ru.neverland.core.SuiteState.lock()) {
        var api = ApiServices.connect("NeverLandTownyEvents", "ru.neverland.mintevents.api.MintTownyEventsApi", 1, "hasFireDamage");
        if (api.state() == ApiServices.State.NOT_INSTALLED) return false;
        if (!api.ready()) return true;
        try {
            return !Boolean.FALSE.equals(api.invoke("hasFireDamage", new Class<?>[]{UUID.class}, town));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return true;
        }
    } }
}
