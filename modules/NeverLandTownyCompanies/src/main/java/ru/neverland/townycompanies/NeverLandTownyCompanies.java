package ru.neverland.townycompanies;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import ru.neverland.townycompanies.api.CompaniesApi;

public final class NeverLandTownyCompanies extends JavaPlugin {
    private CompanyService service;private CompanyMenus menus;
    @Override public void onEnable(){
        saveDefaultConfig();
        try {
            CompanyRepository repo=new CompanyRepository(getDataFolder().toPath().resolve("companies.yml"));repo.load();service=new CompanyService(this,repo);
            for(var kind:CompanyData.Kind.values())service.rate(kind);
            CompanyCommand command=new CompanyCommand(this,service);menus=new CompanyMenus(this,service,command);command.menus(menus);
            getCommand("townycompanies").setExecutor(command);getCommand("townycompanies").setTabCompleter(command);
            TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"companies",command);
            getServer().getPluginManager().registerEvents(menus,this);
            getServer().getServicesManager().register(CompaniesApi.class,service,this,ServicePriority.Normal);
            getServer().getScheduler().runTaskTimer(this,service::tick,1L,1200L);
            getLogger().info("Предприятия города включены: "+repo.state().companies().size());
        }catch(Exception ex){getLogger().severe("Предприятия не запущены: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);}
    }
    public CompaniesApi companies(){return service;}
    @Override public void onDisable(){if(menus!=null)menus.close();TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,"companies");getServer().getServicesManager().unregisterAll(this);}
}
