package ru.neverland.townypower.api;
import java.util.*;
public interface TownyPowerApi {
    boolean powered(UUID town,String project);
    String status(UUID town,String project);
    Optional<PowerSnapshot> power(UUID town);
    Optional<PowerSnapshot> residentPower(UUID resident);
    Collection<PowerSnapshot> towns();
}
