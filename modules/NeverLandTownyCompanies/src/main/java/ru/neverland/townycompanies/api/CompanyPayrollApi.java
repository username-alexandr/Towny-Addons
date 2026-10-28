package ru.neverland.townycompanies.api;
import org.bukkit.entity.Player;
import java.util.*;
/** Only completed, verified Jobs actions may be offered. Does not create currency. */
public interface CompanyPayrollApi extends ru.neverland.core.ApiContract {
    default Set<String> capabilities(){return Set.of("recordWork");}
    void recordWork(Player player,UUID proof,String profession,int level);
}
