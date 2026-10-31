package cc.crafto.ui.server;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;
final class CityContractReader {
    record Result(List<CitySnapshot.Row> rows,double pending){}
    static Result read(YamlConfiguration yaml,String townId,UUID player){
        if(yaml.getInt("schema",-1)!=3)throw new IllegalArgumentException("Unsupported town contract schema");
        for(String path:List.of("towns","pending","pending.players","payments","towns."+townId,"towns."+townId+".active"))
            if(yaml.contains(path)&&!yaml.isConfigurationSection(path))throw new IllegalArgumentException("Invalid town contract section: "+path);
        List<CitySnapshot.Row> rows=new ArrayList<>();
        var active=townId.isEmpty()?null:yaml.getConfigurationSection("towns."+townId+".active");
        if(active!=null)for(String id:active.getKeys(false)){
            if(rows.size()>=32)break;var row=active.getConfigurationSection(id);if(row==null)throw new IllegalArgumentException("Invalid town contract row");if(row.getBoolean("admin-cancelled"))continue;
            int goal=row.getInt("goal",-1),progress=row.getInt("progress",-1);if(goal<=0||progress<0||progress>goal)throw new IllegalArgumentException("Invalid town contract progress");
            String state=row.getString("settlement-status","");String label=CitySnapshot.clean(row.getString("terms.name",row.getString("template",id)));
            rows.add(new CitySnapshot.Row(label+" — "+progress+" / "+goal+(state.isEmpty()?"":" • расчёт награды")));
        }
        Object stored=yaml.get("pending.players."+player);if(stored!=null&&!(stored instanceof Number))throw new IllegalArgumentException("Invalid pending reward type");
        double pending=yaml.getDouble("pending.players."+player);if(!Double.isFinite(pending)||pending<0)throw new IllegalArgumentException("Invalid pending reward");
        // Match ContractRepository.pendingPlayer: only this player's uncompleted REWARD payments.
        var payments=yaml.getConfigurationSection("payments");long cents=0;
        if(payments!=null)for(String id:payments.getKeys(false)){
            var payment=payments.getConfigurationSection(id);if(payment==null)throw new IllegalArgumentException("Invalid municipal payment");
            if(!"REWARD".equals(payment.getString("kind"))||!player.toString().equals(payment.getString("account")))continue;
            String phase=payment.getString("phase","");if(!Set.of("READY","PENDING","DONE","REJECTED").contains(phase))throw new IllegalArgumentException("Unknown municipal payment phase");
            if(!Set.of("READY","PENDING").contains(phase))continue;
            if(!payment.isInt("cents")&&!payment.isLong("cents"))throw new IllegalArgumentException("Invalid municipal reward amount");
            long amount=payment.getLong("cents");if(amount<=0||amount>100_000_000_000L)throw new IllegalArgumentException("Invalid municipal reward amount");
            cents=Math.addExact(cents,amount);
        }
        pending+=cents/100.0;
        if(rows.isEmpty())rows.add(new CitySnapshot.Row("Активных городских заданий нет."));
        return new Result(List.copyOf(rows),pending);
    }
}
