package ru.neverland.townytaxes.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townytaxes.model.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CivicRepository {
    private final JavaPlugin plugin; private final File file;
    private final Map<UUID, TaxPolicy> policies = new LinkedHashMap<>();
    private final Map<UUID, Sanction> sanctions = new LinkedHashMap<>();
    private final Map<UUID, Agreement> agreements = new LinkedHashMap<>();
    private final Map<UUID, Double> debts = new LinkedHashMap<>();
    private final List<LedgerEntry> ledger = new ArrayList<>();
    private double serverBank; private boolean dirty;
    public CivicRepository(JavaPlugin plugin) { this.plugin=plugin; this.file=new File(plugin.getDataFolder(),"data.yml"); }
    public synchronized void load() {
        policies.clear(); sanctions.clear(); agreements.clear(); debts.clear(); ledger.clear();
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file); serverBank=y.getDouble("server-bank",0);
        ConfigurationSection ps=y.getConfigurationSection("policies"); if(ps!=null)for(String k:ps.getKeys(false))try{String p="policies."+k+".";UUID id=UUID.fromString(k);policies.put(id,new TaxPolicy(id,Domain.Scope.valueOf(y.getString(p+"scope")),uuid(y.getString(p+"target-id")),y.getString(p+"target-name","?"),Domain.TaxType.valueOf(y.getString(p+"type")),y.getDouble(p+"value"),Domain.Scope.valueOf(y.getString(p+"destination-scope")),uuid(y.getString(p+"destination-id")),y.getString(p+"destination-name","Сервер"),y.getLong(p+"interval"),y.getLong(p+"next"),y.getBoolean(p+"enabled",true),y.getString(p+"created-by","console")));}catch(Exception e){plugin.getLogger().warning("Повреждена налоговая политика "+k);}
        ConfigurationSection ss=y.getConfigurationSection("sanctions"); if(ss!=null)for(String k:ss.getKeys(false))try{String p="sanctions."+k+".";UUID id=UUID.fromString(k);sanctions.put(id,new Sanction(id,Domain.Scope.valueOf(y.getString(p+"target-scope")),uuid(y.getString(p+"target-id")),y.getString(p+"target-name","?"),Domain.Scope.valueOf(y.getString(p+"issuer-scope")),uuid(y.getString(p+"issuer-id")),y.getString(p+"issuer-name","Сервер"),Domain.SanctionEffect.valueOf(y.getString(p+"effect")),y.getDouble(p+"value"),y.getLong(p+"created"),y.getLong(p+"expires"),y.getString(p+"reason","Без причины"),y.getString(p+"created-by","console")));}catch(Exception e){plugin.getLogger().warning("Повреждена санкция "+k);}
        ConfigurationSection as=y.getConfigurationSection("agreements"); if(as!=null)for(String k:as.getKeys(false))try{String p="agreements."+k+".";UUID id=UUID.fromString(k);agreements.put(id,new Agreement(id,Domain.Scope.valueOf(y.getString(p+"first-scope")),uuid(y.getString(p+"first-id")),y.getString(p+"first-name","?"),Domain.Scope.valueOf(y.getString(p+"second-scope")),uuid(y.getString(p+"second-id")),y.getString(p+"second-name","?"),Domain.AgreementType.valueOf(y.getString(p+"type")),y.getDouble(p+"value"),y.getLong(p+"created"),y.getLong(p+"expires"),Domain.AgreementStatus.valueOf(y.getString(p+"status","PENDING")),y.getString(p+"created-by","console")));}catch(Exception e){plugin.getLogger().warning("Повреждено соглашение "+k);}
        ConfigurationSection ds=y.getConfigurationSection("debts");if(ds!=null)for(String k:ds.getKeys(false))try{debts.put(UUID.fromString(k),ds.getDouble(k));}catch(Exception ignored){}
        for(Map<?,?> m:y.getMapList("ledger"))try{ledger.add(new LedgerEntry(((Number)m.get("timestamp")).longValue(),String.valueOf(m.get("action")),Domain.Scope.valueOf(String.valueOf(m.get("scope"))),uuid(String.valueOf(m.get("subject-id"))),String.valueOf(m.get("subject-name")),((Number)m.get("amount")).doubleValue(),String.valueOf(m.get("details")),Boolean.parseBoolean(String.valueOf(m.get("success")))));}catch(Exception ignored){}
        dirty=false;
    }
    public synchronized boolean save() {
        YamlConfiguration y=new YamlConfiguration();y.set("server-bank",serverBank);
        for(TaxPolicy v:policies.values()){String p="policies."+v.id()+".";y.set(p+"scope",v.scope().name());y.set(p+"target-id",str(v.targetId()));y.set(p+"target-name",v.targetName());y.set(p+"type",v.type().name());y.set(p+"value",v.value());y.set(p+"destination-scope",v.destinationScope().name());y.set(p+"destination-id",str(v.destinationId()));y.set(p+"destination-name",v.destinationName());y.set(p+"interval",v.intervalMillis());y.set(p+"next",v.nextCollection());y.set(p+"enabled",v.enabled());y.set(p+"created-by",v.createdBy());}
        for(Sanction v:sanctions.values()){String p="sanctions."+v.id()+".";y.set(p+"target-scope",v.targetScope().name());y.set(p+"target-id",str(v.targetId()));y.set(p+"target-name",v.targetName());y.set(p+"issuer-scope",v.issuerScope().name());y.set(p+"issuer-id",str(v.issuerId()));y.set(p+"issuer-name",v.issuerName());y.set(p+"effect",v.effect().name());y.set(p+"value",v.value());y.set(p+"created",v.createdAt());y.set(p+"expires",v.expiresAt());y.set(p+"reason",v.reason());y.set(p+"created-by",v.createdBy());}
        for(Agreement v:agreements.values()){String p="agreements."+v.id()+".";y.set(p+"first-scope",v.firstScope().name());y.set(p+"first-id",str(v.firstId()));y.set(p+"first-name",v.firstName());y.set(p+"second-scope",v.secondScope().name());y.set(p+"second-id",str(v.secondId()));y.set(p+"second-name",v.secondName());y.set(p+"type",v.type().name());y.set(p+"value",v.value());y.set(p+"created",v.createdAt());y.set(p+"expires",v.expiresAt());y.set(p+"status",v.status().name());y.set(p+"created-by",v.createdBy());}
        debts.forEach((id,value)->y.set("debts."+id,value));List<Map<String,Object>> rows=new ArrayList<>();for(LedgerEntry e:ledger){Map<String,Object>m=new LinkedHashMap<>();m.put("timestamp",e.timestamp());m.put("action",e.action());m.put("scope",e.subjectScope().name());m.put("subject-id",str(e.subjectId()));m.put("subject-name",e.subjectName());m.put("amount",e.amount());m.put("details",e.details());m.put("success",e.success());rows.add(m);}y.set("ledger",rows);
        try{y.save(file);dirty=false;return true;}catch(IOException e){plugin.getLogger().severe("Не удалось сохранить data.yml: "+e.getMessage());return false;}
    }
    public void saveIfDirty(){if(dirty)save();} public void changed(){dirty=true;}
    public Collection<TaxPolicy> policies(){return List.copyOf(policies.values());} public Collection<Sanction> sanctions(){return List.copyOf(sanctions.values());} public Collection<Agreement> agreements(){return List.copyOf(agreements.values());} public List<LedgerEntry> ledger(){return List.copyOf(ledger);}
    public TaxPolicy policy(String id){return find(policies,id);}public Sanction sanction(String id){return find(sanctions,id);}public Agreement agreement(String id){return find(agreements,id);}
    public void put(TaxPolicy v){policies.put(v.id(),v);dirty=true;}public void put(Sanction v){sanctions.put(v.id(),v);dirty=true;}public void put(Agreement v){agreements.put(v.id(),v);dirty=true;}
    public boolean removePolicy(String id){TaxPolicy v=policy(id);if(v==null)return false;policies.remove(v.id());dirty=true;return true;}public boolean removeSanction(String id){Sanction v=sanction(id);if(v==null)return false;sanctions.remove(v.id());dirty=true;return true;}
    public double debt(UUID id){return debts.getOrDefault(id,0D);}public void debt(UUID id,double value){if(value<=0.004)debts.remove(id);else debts.put(id,value);dirty=true;}public double serverBank(){return serverBank;}public void addServerBank(double amount){serverBank=Math.max(0,serverBank+amount);dirty=true;}
    public void log(LedgerEntry entry){ledger.add(entry);int max=Math.max(10,plugin.getConfig().getInt("storage.ledger-limit",1000));while(ledger.size()>max)ledger.remove(0);dirty=true;}
    private static UUID uuid(String value){return value==null||value.isBlank()||value.equals("null")?null:UUID.fromString(value);}private static String str(UUID id){return id==null?null:id.toString();}
    private static <T> T find(Map<UUID,T> map,String prefix){if(prefix==null)return null;try{T exact=map.get(UUID.fromString(prefix));if(exact!=null)return exact;}catch(Exception ignored){}String p=prefix.toLowerCase(Locale.ROOT);T found=null;for(Map.Entry<UUID,T>e:map.entrySet())if(e.getKey().toString().startsWith(p)){if(found!=null)return null;found=e.getValue();}return found;}
}
