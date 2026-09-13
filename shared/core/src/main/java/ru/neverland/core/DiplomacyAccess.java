package ru.neverland.core;

import java.util.*;

/** Optional diplomacy: an installed but unavailable provider never grants an exception. */
public final class DiplomacyAccess {
    private static final String PLUGIN="NeverLandTownyDiplomacy",API="ru.neverland.townydiplomacy.api.TownyDiplomacyApi";
    private DiplomacyAccess() { }
    public static boolean available() {
        var c=ApiServices.connect(PLUGIN,API,1,"healthy");
        if(c.state()==ApiServices.State.NOT_INSTALLED)return true;if(!c.ready())return false;
        try{return Boolean.TRUE.equals(c.invoke("healthy",new Class<?>[0]));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return false;}
    }
    private static boolean restriction(String method,UUID a,UUID b) {
        if(a==null||b==null||a.equals(b))return false;
        var c=ApiServices.connect(PLUGIN,API,1,method);
        if(c.state()==ApiServices.State.NOT_INSTALLED)return false;
        if(!c.ready())return true;
        try { Object value=c.invoke(method,new Class<?>[]{UUID.class,UUID.class},a,b);return !(value instanceof Boolean flag)||flag; }
        catch(ReflectiveOperationException|RuntimeException|LinkageError ex) { return true; }
    }
    public static boolean tradeBlocked(UUID a,UUID b) { return restriction("tradeBlocked",a,b); }
    public static boolean hostileBlocked(UUID a,UUID b) { return restriction("hostileBlocked",a,b); }
    public static double tariffMultiplier(UUID a,UUID b,UUID tariffTown) {
        var c=ApiServices.connect(PLUGIN,API,1,"tariffMultiplier");
        if(c.state()==ApiServices.State.NOT_INSTALLED)return 1;
        if(!c.ready())throw new IllegalStateException("Система дипломатии недоступна");
        try {
            Object value=c.invoke("tariffMultiplier",new Class<?>[]{UUID.class,UUID.class,UUID.class},a,b,tariffTown);
            if(value instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>=0&&n.doubleValue()<=1)return n.doubleValue();
            throw new IllegalStateException("Некорректная дипломатическая пошлина");
        } catch(ReflectiveOperationException|LinkageError ex) { throw new IllegalStateException("Система дипломатии недоступна",ex); }
    }
    public static Set<String> relations(UUID a,UUID b) {
        var c=ApiServices.connect(PLUGIN,API,1,"relations");
        if(c.state()==ApiServices.State.NOT_INSTALLED)return Set.of();if(!c.ready())return Set.of("UNAVAILABLE");
        try { Object value=c.invoke("relations",new Class<?>[]{UUID.class,UUID.class},a,b);if(value instanceof Set<?> set&&set.stream().allMatch(v->v instanceof String)) {
            var result=new HashSet<String>();set.forEach(v->result.add((String)v));return Set.copyOf(result);
        } } catch(ReflectiveOperationException|RuntimeException|LinkageError ignored) { }
        return Set.of("UNAVAILABLE");
    }
}
