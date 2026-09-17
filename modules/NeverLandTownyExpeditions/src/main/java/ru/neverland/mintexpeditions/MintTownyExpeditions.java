package ru.neverland.mintexpeditions;

import java.io.File;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintexpeditions.command.AdminCommand;
import ru.neverland.mintexpeditions.command.ExpeditionCommand;
import ru.neverland.mintexpeditions.gui.ExpeditionMenuManager;
import ru.neverland.mintexpeditions.integration.BuildBridge;
import ru.neverland.mintexpeditions.integration.CampFacade;
import ru.neverland.mintexpeditions.integration.ItemsAdderHook;
import ru.neverland.mintexpeditions.integration.TownyHook;
import ru.neverland.mintexpeditions.listener.ExpeditionListener;
import ru.neverland.mintexpeditions.model.ActiveExpedition;
import ru.neverland.mintexpeditions.model.ExpeditionDefinition;
import ru.neverland.mintexpeditions.model.ExpeditionStatus;
import ru.neverland.mintexpeditions.service.ExpeditionRegistry;
import ru.neverland.mintexpeditions.service.ExpeditionRepository;
import ru.neverland.mintexpeditions.service.ExpeditionService;
import ru.neverland.mintexpeditions.service.MessageService;
import ru.neverland.mintexpeditions.service.RussianItemNames;
import ru.neverland.mintexpeditions.service.SiteService;
import ru.neverland.mintexpeditions.util.LegacyDataMigrator;

public final class MintTownyExpeditions extends JavaPlugin {
   private static final List<String> TOWN_COMMANDS = List.of("expeditions", "expedition");
   private MessageService messages;
   private ItemsAdderHook itemsAdder;
   private RussianItemNames itemNames;
   private ExpeditionRegistry registry;
   private ExpeditionRepository repository;
   private ExpeditionService service;
   private TownyHook towny;

   public void onEnable() {
        if (!ru.neverland.core.ModuleLifecycle.begin(this)) return;

      LegacyDataMigrator.migrate(this, "MintTownyExpeditions");
      this.saveDefaultConfig();
      this.copy("messages.yml");
      this.copy("expeditions.yml");
      this.copy("item-names.yml");
      this.messages = new MessageService(this);
      this.itemsAdder = new ItemsAdderHook();
      this.itemNames = new RussianItemNames(this);
      this.registry = new ExpeditionRegistry(this, this.itemsAdder);
      this.repository = new ExpeditionRepository(this);
      this.repository.load();
      this.towny = new TownyHook();
      CampFacade camps = new CampFacade(this, this.itemNames);
      SiteService sites = new SiteService(this, this.towny, this.repository);

      for (ActiveExpedition expedition : this.repository.active()) {
         ExpeditionDefinition definition = this.registry.get(expedition.definitionId());
         if (definition != null && expedition.status() == ExpeditionStatus.ACTIVE) {
            sites.resume(expedition, definition);
         }
      }

      this.service = new ExpeditionService(this, this.messages, this.registry, this.repository, camps, sites, this.towny, new BuildBridge(this));
      ExpeditionMenuManager menu = new ExpeditionMenuManager(this, this.service, this.itemNames);
      Bukkit.getPluginManager().registerEvents(menu, this);
      Bukkit.getPluginManager().registerEvents(new ExpeditionListener(this.service), this);
      ExpeditionCommand command = new ExpeditionCommand(this.service, menu, this.messages);
      this.bind("expedition", command, command);

      for (String name : TOWN_COMMANDS) {
         if (!this.towny.register(name, command)) {
            this.getLogger().warning("Не удалось зарегистрировать /t " + name + "; используйте /expedition.");
         }
      }

      AdminCommand admin = new AdminCommand(this);
      this.bind("townyexpeditions", admin, admin);
ru.neverland.core.ActivityAdmin.attach(this,"townyexpeditions","mintexpeditions.admin",service::adminTargets);
      this.service.startTasks();
      this.getLogger()
         .info(
            "NeverLandTownyExpeditions "
               + this.getPluginMeta().getVersion()
               + " включён: доступно "
               + this.registry.all().size()
               + " экспедиций, команды /t expeditions готовы."
         );
   }

   public void onDisable() {
        if (!ru.neverland.core.ModuleLifecycle.end(this)) return;

      if (this.service != null) {
         this.service.stopTasks();
      }

      if (this.repository != null && this.repository.writable()) {
         this.repository.save();
      }

      if (this.towny != null) {
         for (String name : TOWN_COMMANDS) {
            this.towny.unregister(name);
         }
      }
   }

   public void reloadAll() {
      this.reloadConfig();
      this.messages.reload();
      this.itemsAdder.reload();
      this.itemNames.reload();
      this.registry.reload();
      this.service.startTasks();
   }

   private void copy(String name) {
      if (!new File(this.getDataFolder(), name).exists()) {
         this.saveResource(name, false);
      }
   }

   private void bind(String name, CommandExecutor executor, TabCompleter completer) {
      PluginCommand command = this.getCommand(name);
      if (command == null) {
         throw new IllegalStateException(name);
      } else {
         command.setExecutor(executor);
         command.setTabCompleter(completer);
      }
   }

   public MessageService messages() {
      return this.messages;
   }

   public ExpeditionService service() {
      return this.service;
   }
}
