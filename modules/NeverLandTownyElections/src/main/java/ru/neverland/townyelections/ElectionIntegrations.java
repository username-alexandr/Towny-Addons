package ru.neverland.townyelections;

import java.util.*;
import ru.neverland.core.ApiServices;

public final class ElectionIntegrations {
    private static final String GOV = "ru.neverland.governance.api.TownyGovernanceApi";
    private static ApiServices.Connection governance() { return ApiServices.require("NeverLandTownyGovernance",GOV,"officeCatalog","officeHolders","applyElection","electionReceipt"); }
    public static String form(UUID town) throws ReflectiveOperationException {
        var c=ApiServices.connect("NeverLandTownyIdeologies","ru.neverland.townyideologies.api.TownyIdeologiesApi",1,"governmentForm");
        if(c.state()==ApiServices.State.NOT_INSTALLED)return "DEMOCRACY";
        Object raw=c.invoke("governmentForm",new Class<?>[]{UUID.class},town);
        if(!(raw instanceof String value)||!Set.of("DEMOCRACY","MONARCHY","AUTOCRACY").contains(value))throw new IllegalStateException("Invalid government API response");
        return value;
    }
    @SuppressWarnings("unchecked") public static Map<String,Integer> catalog() throws ReflectiveOperationException {
        Object raw=governance().invoke("officeCatalog",new Class<?>[0]);
        if(!(raw instanceof Map<?,?> map)||map.entrySet().stream().anyMatch(e->!(e.getKey() instanceof String)||!(e.getValue() instanceof Integer n)||n<1))throw new IllegalStateException("Invalid offices API");
        return (Map<String,Integer>)raw;
    }
    public static String receipt(UUID town) throws ReflectiveOperationException { return (String)governance().invoke("electionReceipt",new Class<?>[]{UUID.class},town); }
    @SuppressWarnings("unchecked") public static Map<String,List<UUID>> holders(UUID town) throws ReflectiveOperationException { return (Map<String,List<UUID>>)governance().invoke("officeHolders",new Class<?>[]{UUID.class},town); }
    public static void apply(UUID town,UUID election,Map<String,List<UUID>> winners) throws ReflectiveOperationException {
        governance().invoke("applyElection",new Class<?>[]{UUID.class,UUID.class,Map.class},town,election,winners);
    }
}
