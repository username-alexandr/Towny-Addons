package ru.neverland.mintevents.service;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.TownySettings;
import java.io.IOException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.*;

/** Trust-based acknowledgement of a repaired plot. Never reads or writes world blocks. */
public final class FireRepairService {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final EventService events;
    public FireRepairService(JavaPlugin plugin, TownyHook towny, EventService events) { this.plugin=plugin; this.towny=towny; this.events=events; }
    public static boolean admin(Player p) { return p.isOp() || p.hasPermission("mintevents.admin") || p.hasPermission("neverlandtownycontrol.admin"); }
    public List<FireRepairPlot> plots(UUID town) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        int size=TownySettings.getTownBlockSize();
        record Key(UUID world,int x,int z) {}
        Map<Key,List<FireDamage>> groups=new LinkedHashMap<>();
        for(var d:events.fires().damage(town)) groups.computeIfAbsent(new Key(d.world(),Math.floorDiv(d.x(),size),Math.floorDiv(d.z(),size)),k->new ArrayList<>()).add(d);
        return groups.entrySet().stream().map(e->new FireRepairPlot(town,e.getKey().world(),e.getKey().x(),e.getKey().z(),size,e.getValue()))
            .sorted(Comparator.comparing((FireRepairPlot p)->p.world().toString()).thenComparingInt(FireRepairPlot::x).thenComparingInt(FireRepairPlot::z)).toList();
    } }
    public boolean allowed(Player player, FireRepairPlot plot, boolean administrative) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if(administrative) return admin(player);
        if(!player.hasPermission("mintevents.use") || !player.hasPermission("mintevents.repair")) return false;
        Resident resident=towny.resident(player); Town town=towny.town(player);
        if(resident==null || town==null || !town.getUUID().equals(plot.town())) return false;
        var world=Bukkit.getWorld(plot.world()); if(world==null) return false;
        TownBlock block=TownyAPI.getInstance().getTownBlock(new WorldCoord(world.getName(),plot.x(),plot.z()));
        if(block==null || block.getTownOrNull()==null || !block.getTownOrNull().getUUID().equals(plot.town())) return false;
        return resident.equals(town.getMayor()) || resident.equals(block.getResidentOrNull()) || player.hasPermission("mintevents.repair.manage");
    } }
    public int confirm(Player player, FireRepairPlot plot, boolean administrative) throws IOException { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        if(!plugin.isEnabled() || SuiteState.frozen(plugin.getName())) throw new IllegalArgumentException("Ремонт временно недоступен");
        if(!allowed(player,plot,administrative)) throw new IllegalArgumentException("Подтвердить ремонт может владелец участка, мэр или управляющий.");
        if(plot.size()!=TownySettings.getTownBlockSize()) throw new IllegalArgumentException("Границы участка изменились. Откройте меню заново.");
        if(events.fireUnfinished(plot.town())) throw new IllegalArgumentException("Дождитесь завершения пожара. На паузе ремонт тоже не закрывается.");
        if(!plots(plot.town()).contains(plot)) throw new IllegalArgumentException("Список ремонта изменился. Откройте участок заново.");
        String operation=UUID.randomUUID().toString();
        String details=(administrative?"Администратор":"Житель")+" подтвердил ремонт; мир="+plot.world()+"; участок="+plot.x()+","+plot.z()+"; повреждения="+plot.damage().stream().map(d->d.id()+"@"+d.incident()).toList();
        var actor=AuditTrail.player(player.getUniqueId()); var city=AuditTrail.town(plot.town());
        if(!AuditTrail.record(plugin,"prepare",operation,"FIRE_REPAIR","PREPARED",actor,city,city,"",plot.damage().size(),"",details))
            throw new IOException("Не удалось записать подтверждение ремонта");
        int count;
        try { count=events.fires().repository().confirm(plot); }
        catch(IOException | RuntimeException error) {
            AuditTrail.record(plugin,"result",operation,"FIRE_REPAIR","FAILED",actor,city,city,"",plot.damage().size(),"",details); throw error;
        }
        if(!AuditTrail.record(plugin,"result",operation,"FIRE_REPAIR","COMPLETED",actor,city,city,"",count,"",details))
            plugin.getLogger().warning("Ремонт сохранён, итог аудита требует сверки: "+operation);
        return count;
    } }
}
