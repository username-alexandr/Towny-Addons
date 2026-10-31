package cc.crafto.ui.server;

import cc.crafto.ui.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AddonSuiteMigrationTest {
    @TempDir Path root;
    JsonObject resource(String name)throws Exception{try(var in=getClass().getResourceAsStream("/"+name)){return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}}
    JsonObject old(String name)throws Exception{return resource("migrations/0.8.19/"+name);}
    JsonObject module(JsonObject config,String plugin){for(var e:config.getAsJsonArray("modules"))if(e.getAsJsonObject().get("plugin").getAsString().equals(plugin))return e.getAsJsonObject();throw new AssertionError(plugin);}
    void install(String name,JsonObject value)throws Exception{var f=root.resolve(name);Files.createDirectories(f.getParent());Files.writeString(f,value.toString());}
    JsonObject saved(String name)throws Exception{return JsonParser.parseString(Files.readString(root.resolve(name))).getAsJsonObject();}
    @Test void previousDefaultsUpgradeWithExactBackupAndRemainIdempotent()throws Exception{
        var names=List.of("addons.json","command-ui.json","quick-actions.json","menus/development.json","menus/security.json","menus/economy.json");var before=new HashMap<String,byte[]>();
        for(String name:names){install(name,old(name));before.put(name,Files.readAllBytes(root.resolve(name)));}
        DefaultMenuMigration.apply(root);DefaultMenuMigration.apply(root);
        for(String name:names){assertEquals(resource(name),saved(name),name);assertArrayEquals(before.get(name),Files.readAllBytes(root.resolve("backups/0.8.19/"+name)),name);}
    }
    @Test void suite52UpgradePreservesExplicitExclusionsAndNewHolderPermissions()throws Exception{
        var config=resource("migrations/0.8.20/addons.json");var market=module(config,"NeverLandTownyMarket");market.addProperty("label","Наш рынок");
        module(config,"NeverLandTownyResearch").add("holders",new JsonArray());install("addons.json",config);AddonSuiteMigration.apply(root);
        var result=AddonCatalog.parse(saved("addons.json"));String holder="ru.neverland.townymarket.personal.LimitService$View";
        assertEquals("Наш рынок",result.entries().get("market").label());assertTrue(result.entries().get("market").holders().contains(holder));
        assertEquals("neverlandtownymarket.personal",result.entries().get("market").holderPermission(holder));assertTrue(result.entries().get("research").holders().isEmpty());
        assertTrue(Files.exists(root.resolve("backups/0.8.19/addons.json"))||Files.exists(root.resolve("backups/0.8.20/addons.json")));
    }
    @Test void suite52UnmodifiedDefaultsAndDevelopmentEntryMigrate()throws Exception{
        for(String name:List.of("addons.json","command-ui.json","quick-actions.json","menus/development.json"))install(name,resource("migrations/0.8.20/"+name));
        DefaultMenuMigration.apply(root);DefaultMenuMigration.apply(root);
        for(String name:List.of("addons.json","command-ui.json","quick-actions.json","menus/development.json")){assertEquals(resource(name),saved(name));assertTrue(Files.exists(root.resolve("backups/0.8.20/"+name)));}
        var development=saved("menus/development.json");assertEquals("ui:planner",MenuSchema.find(development,"planner").get("action").getAsString());
    }
    @Test void releasedMainMenuGainsTownCreationWithoutDuplicatingOnReload()throws Exception{
        for(String name:List.of("menus/main.json","menus/hub.json","command-ui.json"))install(name,resource("migrations/0.9.0/"+name));
        DefaultMenuMigration.apply(root);DefaultMenuMigration.apply(root);
        for(String name:List.of("menus/main.json","menus/hub.json","command-ui.json")){assertEquals(resource(name),saved(name));assertTrue(Files.exists(root.resolve("backups/0.9.0/"+name)));}
        assertEquals("ui:create-town",MenuSchema.find(saved("menus/hub.json"),"create-town").get("action").getAsString());
    }
    @Test void customizedAddonLabelsCommandsPermissionsAndThemeSurvive()throws Exception{
        var config=old("addons.json");config.addProperty("theme","classic");config.addProperty("width",800);
        var siege=module(config,"NeverLandTownySiegesPlus");siege.addProperty("label","Наши рейды");siege.addProperty("command","our:sieges");siege.addProperty("permission","our.raids");
        install("addons.json",config);AddonSuiteMigration.apply(root);var result=saved("addons.json");var updated=module(result,"NeverLandTownySiegesPlus");
        assertEquals("classic",result.get("theme").getAsString());assertEquals(800,result.get("width").getAsInt());
        assertEquals("Наши рейды",updated.get("label").getAsString());assertEquals("our:sieges",updated.get("command").getAsString());assertEquals("our.raids",updated.get("permission").getAsString());
        assertTrue(updated.getAsJsonArray("holders").contains(new JsonPrimitive("ru.neverland.townysieges.RaidHours$View")));
    }
    @Test void excludedOwnerAndAnchorsStayExcluded()throws Exception{
        var config=old("addons.json");config.getAsJsonArray("modules").remove(module(config,"NeverLandTownyCompanies"));
        module(config,"NeverLandTownyChronicles").add("holders",new JsonArray());install("addons.json",config);AddonSuiteMigration.apply(root);
        var result=AddonCatalog.parse(saved("addons.json"));assertFalse(result.entries().containsKey("companies"));assertTrue(result.entries().get("chronicles").holders().isEmpty());
    }
    @Test void personalOrdersDoNotReenableExcludedMunicipalContractMenu()throws Exception{
        var config=old("addons.json");module(config,"NeverLandTownyContracts").getAsJsonArray("holders").remove(new JsonPrimitive("ru.neverland.mintcontracts.gui.ContractMenuHolder"));
        install("addons.json",config);AddonSuiteMigration.apply(root);var holders=AddonCatalog.parse(saved("addons.json")).entries().get("contracts").holders();
        assertTrue(holders.contains("ru.neverland.mintcontracts.orders.ResourceAdmin$Screen"));assertFalse(holders.contains("ru.neverland.mintcontracts.service.BuildOrders$View"));
    }
    @Test void differentOwnerCannotInheritTrustedHolderExtensions()throws Exception{
        var config=old("addons.json");module(config,"NeverLandTownySiegesPlus").addProperty("plugin","OtherPlugin");install("addons.json",config);AddonSuiteMigration.apply(root);
        assertEquals(module(config,"OtherPlugin"),module(saved("addons.json"),"OtherPlugin"));
    }
    @Test void existingCustomPermissionOnNewHolderIsNotOverwritten()throws Exception{
        var config=old("addons.json");var b=module(config,"NeverLandTownyBuilds");String holder="ru.neverland.townybuilds.construction.CityStyles$View";
        b.getAsJsonArray("holders").add(holder);b.getAsJsonObject("holderPermissions").addProperty(holder,"our.architect");install("addons.json",config);AddonSuiteMigration.apply(root);
        assertEquals("our.architect",AddonCatalog.parse(saved("addons.json")).entries().get("builds").holderPermission(holder));
    }
    @Test void commandCustomizationsAndConflictingFormIdsArePreserved()throws Exception{
        var config=old("command-ui.json");var company=module(config,"NeverLandTownyCompanies");company.addProperty("permission","our.company");
        var custom=Protocol.object("id","payroll","label","Наши выплаты","root","townycompanies","prefix","payments","fields",List.of());company.getAsJsonArray("forms").add(custom);
        install("command-ui.json",config);AddonSuiteMigration.apply(root);var module=CommandCatalog.parse(saved("command-ui.json")).modules().get("NeverLandTownyCompanies");
        assertEquals("our.company",module.permission());assertEquals(1,module.forms().stream().filter(f->f.id().equals("payroll")).count());
        assertEquals("payments",module.forms().stream().filter(f->f.id().equals("payroll")).findFirst().orElseThrow().prefix());assertTrue(module.forms().stream().anyMatch(f->f.id().equals("payroll-rate")));
    }
    @Test void rootRestrictionsAndExplicitlyEmptyFormsRemainEffective()throws Exception{
        var config=old("command-ui.json");module(config,"NeverLandTownyBuilds").add("roots",Protocol.JSON.toJsonTree(List.of("townyarmy")));
        module(config,"NeverLandTownyCompanies").add("forms",new JsonArray());install("command-ui.json",config);AddonSuiteMigration.apply(root);
        var result=CommandCatalog.parse(saved("command-ui.json"));assertTrue(result.modules().get("NeverLandTownyBuilds").forms().isEmpty());assertTrue(result.modules().get("NeverLandTownyCompanies").forms().isEmpty());
    }
    @Test void defaultModulePermissionsNoLongerHideUnrelatedAddonCommands()throws Exception{
        install("command-ui.json",old("command-ui.json"));AddonSuiteMigration.apply(root);var result=CommandCatalog.parse(saved("command-ui.json"));
        assertEquals("neverlandtownybuilds.use",result.modules().get("NeverLandTownyBuilds").permission());assertEquals("",result.modules().get("NeverLandTownyContracts").permission());
    }
    @Test void customQuickDestinationSurvivesAndNewDestinationsAreUnique()throws Exception{
        var config=old("quick-actions.json");config.getAsJsonObject("orders").addProperty("command","ourorders");install("quick-actions.json",config);AddonSuiteMigration.apply(root);AddonSuiteMigration.apply(root);
        var result=saved("quick-actions.json");assertEquals("ourorders",result.getAsJsonObject("orders").get("command").getAsString());assertTrue(result.has("company-payroll"));assertTrue(result.has("raid-hours"));
        var commands=new HashSet<String>();for(var e:result.entrySet())assertTrue(commands.add(e.getValue().getAsJsonObject().get("command").getAsString()),e.getKey());
    }
    @Test void malformedCustomJsonIsLeftUntouched()throws Exception{
        for(String broken:List.of("{\"modules\":[","{\"modules\":null}","{\"modules\":{}}")){
            Files.writeString(root.resolve("addons.json"),broken);AddonSuiteMigration.apply(root);assertEquals(broken,Files.readString(root.resolve("addons.json")));
        }
    }
    @Test void nativeControlsHaveTheirActualAddonPermissionsAndAreNotInventories()throws Exception{
        var catalog=AddonCatalog.parse(resource("addons.json"));
        assertEquals("neverlandtownybuilds.style",catalog.entries().get("builds").holderPermission("ru.neverland.townybuilds.construction.CityStyles$View"));
        assertEquals("neverland.orders.resources",catalog.entries().get("contracts").holderPermission("ru.neverland.mintcontracts.orders.ResourceAdmin$Screen"));
        assertEquals("mintcontracts.buildorders",catalog.entries().get("contracts").holderPermission("ru.neverland.mintcontracts.service.BuildOrders$View"));
        for(var e:catalog.entries().values())for(String holder:e.holders())if(holder.contains("CityStyles$")||holder.contains("BuildOrders$")||holder.contains("CompanyPayroll$")||holder.contains("RaidHours$"))assertFalse(e.inventoryHolders().contains(holder));
    }
    @Test void newFormsBuildExactOwnedCommandsAndKeepZeroSalaryValid()throws Exception{
        var catalog=CommandCatalog.parse(resource("command-ui.json"));var company=catalog.modules().get("NeverLandTownyCompanies");
        var rate=company.forms().stream().filter(f->f.id().equals("payroll-rate")).findFirst().orElseThrow();
        assertEquals("townycompanies",rate.root());assertEquals("payroll rate farmer 0",rate.arguments(Protocol.object("profession","farmer","amount","0")));
        var thank=catalog.modules().get("NeverLandTownyChronicles").forms().stream().filter(f->f.id().equals("thank")).findFirst().orElseThrow();
        assertEquals("thank Sasha Спасибо за помощь городу",thank.arguments(Protocol.object("player","Sasha","message","Спасибо за помощь городу")));
        assertThrows(IllegalArgumentException.class,()->rate.arguments(Protocol.object("profession","farmer\nstop","amount","1")));
    }
    @Test void oneUseViewsReopenOnlyTrustedReadOnlyEntrypoints(){
        String hours="ru.neverland.townysieges.RaidHours$View",style="ru.neverland.townybuilds.construction.CityStyles$View";
        assertFalse(NativeBack.reusable(hours,false));assertFalse(NativeBack.reusable(style,false));
        assertEquals("townysieges hours",NativeBack.reopen("NeverLandTownySiegesPlus",hours));assertEquals("townystyle",NativeBack.reopen("NeverLandTownyBuilds",style));
        assertEquals("",NativeBack.reopen("OtherPlugin",hours));assertEquals("",NativeBack.reopen("NeverLandTownySiegesPlus",style));assertEquals("",NativeBack.reopen("NeverLandTownyBuilds","untrusted.Editor"));
    }
}
