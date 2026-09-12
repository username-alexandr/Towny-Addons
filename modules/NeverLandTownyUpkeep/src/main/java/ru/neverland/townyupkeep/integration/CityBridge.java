package ru.neverland.townyupkeep.integration;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import ru.neverland.townyupkeep.model.PaymentProcessor.Gateway;
import java.util.*;
import java.lang.reflect.*;
public final class CityBridge implements Gateway {
    private Object resource(String method,Class<?>[] types,Object... args)throws Exception{
        try{return ru.neverland.core.ApiServices.call("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi",method,types,args);}
        catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw ex;}
    }
    public void verify(){
        ru.neverland.core.ApiServices.require("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi","reserveResources","settleResources","forgetReservation","reservationStatus");
        ru.neverland.core.ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","operationalLevel","buildingFootprints");
    }
    public Map<String,Integer> buildings(UUID town)throws Exception{
        Map<?,?> footprints=(Map<?,?>)ru.neverland.core.ApiServices.call("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","buildingFootprints",new Class<?>[]{UUID.class},town);Map<String,Integer> out=new HashMap<>();
        for(var e:footprints.entrySet()){var b=e.getValue();int level=num(b,"completedLevel");if(level>0&&owned(town,b)&&ru.neverland.integration.SpecializationAccess.allowed(town,e.getKey().toString()))out.put(e.getKey().toString(),Math.min(5,level));}return Map.copyOf(out);
    }
    private static int num(Object o,String method)throws Exception{return ((Number)o.getClass().getMethod(method).invoke(o)).intValue();}
    private boolean owned(UUID town,Object b)throws Exception{
        World world=Bukkit.getWorld((UUID)b.getClass().getMethod("worldId").invoke(b));if(world==null)return false;int size=Coord.getCellSize();if(size<1)return false;
        int x0=Math.floorDiv(num(b,"minX"),size),x1=Math.floorDiv(num(b,"maxX"),size),z0=Math.floorDiv(num(b,"minZ"),size),z1=Math.floorDiv(num(b,"maxZ"),size);
        long width=(long)x1-x0+1,depth=(long)z1-z0+1;if(width<1||depth<1||width>4096||depth>4096||width*depth>4096)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){Town owner=TownyAPI.getInstance().getTown(new Location(world,(double)x*size,world.getMinHeight(),(double)z*size));if(owner==null||!town.equals(owner.getUUID()))return false;}return true;
    }
    @Override public boolean reserve(UUID id,UUID town,Map<String,Long> amounts)throws Exception{return (Boolean)resource("reserveResources",new Class<?>[]{UUID.class,UUID.class,Map.class},id,town,amounts);}
    @Override public void settle(UUID id,boolean consume)throws Exception{
        String status=(String)resource("reservationStatus",new Class<?>[]{UUID.class},id);
        if(!consume&&status.equals("NONE"))return;resource("settleResources",new Class<?>[]{UUID.class,boolean.class},id,consume);
    }
    @Override public boolean canPay(UUID id,long cents){Town town=TownyAPI.getInstance().getTown(id);return town!=null&&TownyEconomyHandler.isActive()&&town.getAccount().canPayFromHoldings(cents/100.0);}
    @Override public boolean withdraw(UUID id,long cents,UUID invoice){Town town=TownyAPI.getInstance().getTown(id);return town!=null&&TownyEconomyHandler.isActive()&&ru.neverland.integration.TreasuryAccess.withdraw(town,"infrastructure","upkeep",cents/100.0,"NeverLand Upkeep "+invoice);}
    @Override public boolean withdraw(UUID id,long cents,UUID invoice,String project){Town town=TownyAPI.getInstance().getTown(id);return town!=null&&TownyEconomyHandler.isActive()&&ru.neverland.integration.TreasuryAccess.withdraw(town,"project/"+project,"upkeep",cents/100.0,"NeverLand Upkeep "+invoice);}
    @Override public void forget(UUID id)throws Exception{resource("forgetReservation",new Class<?>[]{UUID.class},id);}
}
