package cc.crafto.ui.server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import java.util.*;
/** Bounded disk snapshots; invoked by the async scheduler, never an entity/region task. */
final class CivicData {
    record Contract(String id,String title,String type,int goal,int progress,Map<String,Integer> contributions){}
    record CompanyInvite(String id,String name,String town,long expiry){}
    record Snapshot(List<Contract> contracts,List<CompanyInvite> invites,List<String> roles,List<String> treasury,String warning,boolean contractsKnown){}
    private volatile AddonFiles files=new AddonFiles();
    /** Swap caches without waiting for an in-progress disk read on the entity thread. */
    void invalidate(){files=new AddonFiles();}
    Snapshot read(TownyCivic.Context c,Map<String,Plugin> addons,boolean contracts,boolean companies,boolean treasury){
        List<Contract> tasks=new ArrayList<>();List<CompanyInvite> invitations=new ArrayList<>();List<String> roles=new ArrayList<>(),history=new ArrayList<>(),errors=new ArrayList<>();
        boolean contractsKnown=false;
        if(contracts&&c.town()!=null)try{
            var y=read(addons.get("NeverLandTownyContracts"),"contract-data.yml",3);var root=y.getConfigurationSection("towns."+c.town()+".active");
            if(root!=null)for(String id:root.getKeys(false)){
                UUID.fromString(id);var s=root.getConfigurationSection(id);if(s==null||s.getBoolean("admin-cancelled"))continue;
                Map<String,Integer> contribution=new HashMap<>();var values=s.getConfigurationSection("contributions");if(values!=null)for(String player:values.getKeys(false))contribution.put(player,Math.max(0,values.getInt(player)));
                tasks.add(new Contract(id,clean(s.getString("terms.name",s.getString("template",id))),s.getString("terms.type",""),s.getInt("goal"),s.getInt("progress"),Map.copyOf(contribution)));if(tasks.size()>=128)break;
            }
            contractsKnown=true;
        }catch(Exception e){errors.add("Данные городских контрактов недоступны.");}
        if(companies)try{
            var y=read(addons.get("NeverLandTownyCompanies"),"companies.yml",1);var root=y.getConfigurationSection("companies");
            if(root!=null)for(String id:root.getKeys(false)){
                UUID.fromString(id);var s=root.getConfigurationSection(id);if(s==null||s.getBoolean("closed"))continue;
                long expiry=s.getLong("invitations."+c.player());String name=clean(s.getString("name",id));
                if(expiry>System.currentTimeMillis())invitations.add(new CompanyInvite(id,name,s.getString("town",""),expiry));
                String role=s.getString("members."+c.player());if(role!=null)roles.add(name+": "+switch(role){case "OWNER"->"Владелец";case "MANAGER"->"Управляющий";default->"Сотрудник";});
            }
        }catch(Exception e){errors.add("Данные приглашений компаний недоступны.");}
        if(treasury&&c.town()!=null)try{
            var y=read(addons.get("NeverLandTownyTreasuryPlus"),"treasury-data.yml",1);history.addAll(y.getStringList("cities."+c.town()+".history").stream().map(CivicData::clean).limit(128).toList());
        }catch(Exception e){errors.add("Журнал казны недоступен.");}
        return new Snapshot(List.copyOf(tasks),List.copyOf(invitations),List.copyOf(roles),List.copyOf(history),String.join(" ",errors),contractsKnown);
    }
    private YamlConfiguration read(Plugin p,String file,int schema)throws Exception{
        if(p==null)throw new IllegalArgumentException();var y=files.read(p.getDataFolder().toPath().resolve(file));if(y.getInt("schema",schema)!=schema)throw new IllegalArgumentException();return y;
    }
    static String clean(String text){String value=text.replaceAll("(?i)(?:[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx])", "").replaceAll("[\\p{Cntrl}]", " ").strip();return value.length()>1000?value.substring(0,1000):value;}
}
