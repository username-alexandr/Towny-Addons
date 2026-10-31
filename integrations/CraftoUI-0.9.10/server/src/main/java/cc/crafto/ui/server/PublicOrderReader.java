package cc.crafto.ui.server;

import cc.crafto.ui.ContractHudState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Read-only adapter for NeverLandTownyContracts atomic persisted snapshots (schemas 1 and 2).
 * No private-field reflection, no writes to addon data; call exclusively off game threads. */
final class PublicOrderReader {
    record Order(String id,String title,int progress,int goal,String type,String target,ContractHudState.Status status) {
        String deliveryMaterial(){return type.equals("DELIVERY")?target:"";}
    }
    private static final Set<String> TYPES=Set.of("DELIVERY","MINING","HUNTING","FISHING");
    static Map<UUID,List<Order>> read(Path directory,Set<UUID> audience) throws Exception {
        Path source=directory.resolve("public-orders.yml");
        if(!Files.exists(source))return Map.of();
        YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString(bounded(source,16*1024*1024));
        int schema=yaml.getInt("schema",-1);
        if(schema!=1&&schema!=2)throw new IOException("Unsupported public order schema");
        ConfigurationSection rows=yaml.getConfigurationSection("orders");
        if(rows==null){if(yaml.contains("orders"))throw new IOException("Invalid orders section");return Map.of();}
        Map<UUID,List<Order>> result=new HashMap<>();
        for(String key:rows.getKeys(false)){
            ConfigurationSection r=Objects.requireNonNull(rows.getConfigurationSection(key));
            UUID owner=UUID.fromString(Objects.requireNonNull(r.getString("player")));
            if(!audience.contains(owner)||!"ACTIVE".equals(r.getString("state")))continue;
            UUID id=UUID.fromString(key);
            String type=Objects.requireNonNull(r.getString("type"));if(!TYPES.contains(type))throw new IOException("Unknown order type");
            int goal=r.getInt("goal",-1),progress=r.getInt("progress",-1);
            if(goal<=0||progress<0||progress>goal)throw new IOException("Invalid progress");
            String title=Objects.requireNonNull(r.getString("title")).replaceAll("(?i)§[0-9a-fk-orx]","").replaceAll("[\\p{Cntrl}]"," ").strip();
            if(title.length()>160)title=title.substring(0,157)+"…";
            if(title.isBlank())throw new IOException("Empty title");
            ContractHudState.Status status=progress>=goal?ContractHudState.Status.READY:ContractHudState.Status.ACTIVE;
            Path payment=directory.resolve("order-payments").resolve(id+".properties");
            if(Files.exists(payment)){
                Properties p=new Properties();p.load(new StringReader(bounded(payment,256*1024)));
                if(!"1".equals(p.getProperty("schema"))||!id.toString().equals(p.getProperty("id"))||!owner.toString().equals(p.getProperty("player")))throw new IOException("Invalid payment identity");
                String state=Objects.requireNonNull(p.getProperty("state"));
                if(Set.of("PAID","VOID").contains(state))continue;
                status=switch(state){case "HELD"->ContractHudState.Status.HELD;case "PENDING","SENDING"->ContractHudState.Status.PENDING;case "REVIEW"->ContractHudState.Status.REVIEW;case "BLOCKED"->ContractHudState.Status.BLOCKED;default->throw new IOException("Unknown payment state");};
                // Reserved rewards are no longer open objectives, matching the addon's open(order).
                progress=goal;
            }
            String target=Objects.requireNonNull(r.getString("target"));
            if(!target.matches("[A-Z][A-Z0-9_]{0,100}"))throw new IOException("Invalid target");
            result.computeIfAbsent(owner,k->new ArrayList<>()).add(new Order(id.toString(),title,progress,goal,type,target,status));
        }
        result.replaceAll((id,orders)->{Collections.reverse(orders);return List.copyOf(orders.subList(0,Math.min(orders.size(),ContractHudState.MAX_ENTRIES)));});
        return Map.copyOf(result);
    }
    private static String bounded(Path path,int limit)throws IOException {
        // Read at most limit+1, even if an external writer grows the file after stat.
        try(InputStream in=Files.newInputStream(path)){byte[] bytes=in.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("Snapshot too large");return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}
    }
}
