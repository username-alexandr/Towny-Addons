package ru.neverland.core;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Permission plus live, town-scoped appointment. Never substitute a minister for the mayor. */
public final class CouncilAccess {
    private CouncilAccess() { }
    /** Use after checking town membership and mayor/admin authority. Live denies precede legacy ranks. */
    public static boolean check(Player player, UUID town, String action, boolean legacy) { synchronized(SuiteState.lock()) {
        if(player==null||town==null)return false;
        var c=ApiServices.connect("NeverLandTownyCouncil","ru.neverland.townycouncil.api.TownyCouncilApi",1,"allows");
        if(c.state()==ApiServices.State.NOT_INSTALLED)return legacy;
        if(!c.ready())return false;
        try {
            Object caps=c.invoke("capabilities",new Class<?>[]{});
            if(caps instanceof java.util.Set<?> set&&set.contains("decision")) {
                Object decision=c.invoke("decision",new Class<?>[]{UUID.class,UUID.class,String.class},town,player.getUniqueId(),action);
                return "ALLOW".equals(decision)||"DEFAULT".equals(decision)&&legacy;
            }
            return legacy||allows(player,town,action);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return false;}
    } }
    public static boolean allows(Player player, UUID town, String action) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (player == null || town == null) return false;
        String permission = "neverlandtownycouncil.action." + action;
        if (!player.hasPermission(permission)) return false;
        var c = ApiServices.connect("NeverLandTownyCouncil", "ru.neverland.townycouncil.api.TownyCouncilApi", 1, "allows");
        if (!c.ready()) return false;
        try { return Boolean.TRUE.equals(c.invoke("allows", new Class<?>[]{UUID.class, UUID.class, String.class}, town, player.getUniqueId(), permission)); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return false; }
    } }
}
