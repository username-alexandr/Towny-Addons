package ru.neverland.townypower.api;
import java.util.*;
public interface TownyPowerApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("power", "powered", "residentPower", "status", "towns", "energy", "uptime"); }
    default Map<String,Object> uptime(UUID town,String project){throw new UnsupportedOperationException("История времени питания недоступна");}
    default Map<String,Object> energy(UUID town){throw new UnsupportedOperationException("Накопители не поддерживаются этой версией");}
    boolean powered(UUID town,String project);
    String status(UUID town,String project);
    Optional<PowerSnapshot> power(UUID town);
    Optional<PowerSnapshot> residentPower(UUID resident);
    Collection<PowerSnapshot> towns();
}
