package cc.crafto.ui.server;

import cc.crafto.ui.MenuSchema;
import cc.crafto.ui.MenuReflow;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cc.crafto.ui.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationCatalogTest {
    @TempDir Path root;
    static final List<String> MENUS=List.of("main.json","hub.json","town.json","economy.json","development.json","society.json","security.json","adventures.json","management.json","profile.json","help.yml");
    private byte[] resource(String path)throws Exception{try(var in=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(in,path);return in.readAllBytes();}}
    private void install(String path,byte[] bytes)throws Exception{Path target=root.resolve(path);Files.createDirectories(target.getParent());Files.write(target,bytes);}
    private ConfigLoader.Catalog catalog()throws Exception{
        for(String file:MENUS)install("menus/"+file,resource("menus/"+file));
        for(String file:List.of("actions.json","addons.json","quick-actions.json","command-ui.json","themes/classic.json","themes/dark.json"))install(file,resource(file));
        return ConfigLoader.load(root);
    }
    @Test void everyAddonAndMenuIsReachableAndEveryButtonResolves()throws Exception{
        var c=catalog();Set<String> visited=new HashSet<>(),addons=new HashSet<>();Deque<String> pending=new ArrayDeque<>();pending.add("hub");
        while(!pending.isEmpty()){
            String id=pending.remove();if(!visited.add(id))continue;var menu=c.menus().get(id);assertNotNull(menu,id);
            MenuSchema.visit(menu.getAsJsonArray("components"),n->{
                if(!str(n,"type","").equals("button"))return;
                String actionId=str(n,"action","");var action=c.actions().get(actionId);assertNotNull(action,id+"/"+str(n,"id",""));
                if(str(action,"type","").equals("open"))pending.add(str(action,"menu",""));
                if(actionId.startsWith("addons:"))addons.add(actionId.substring(7));
            });
        }
        assertEquals(c.addons().entries().keySet(),addons,"No addon may disappear during regrouping");
        var expected=new HashSet<>(c.menus().keySet());expected.remove("main");assertEquals(expected,visited);
    }
    @Test void ordersAreARealCommandInTheTradingCategory()throws Exception{
        var c=catalog();var action=c.actions().get("quick:orders");
        assertEquals("player-command",str(action,"type",""));assertEquals("orders",str(action,"command",""));
        assertEquals("neverland.orders.use",str(action,"permission",""));assertEquals("NeverLandTownyContracts",str(action,"requiredPlugin",""));
        assertFalse(action.has("route"));assertTrue(bool(action,"closeBefore",false));
        var grid=MenuSchema.find(c.menus().get("economy"),"modules");
        assertEquals("quick:orders",str(grid.getAsJsonArray("children").get(0).getAsJsonObject(),"action",""));
        assertEquals("hub:economy",str(MenuSchema.find(c.menus().get("hub"),"sections").getAsJsonArray("children").get(0).getAsJsonObject(),"action",""));
    }
    @Test void builtInBottomActionsNeverOverlapPagerOrFrameAfterReflow()throws Exception{
        var c=catalog();
        for(var menu:c.menus().values())for(int width:new int[]{240,304,420,620})for(int height:new int[]{164,240,400}){
            var view=MenuReflow.fit(menu,width,height);int w=integer(view,"width",620),h=integer(view,"height",400);
            int reserved=integer(view,"pageCount",1)>1?122:0;
            for(var element:view.getAsJsonArray("components")){
                var node=element.getAsJsonObject();if(!str(node,"layoutRole","").equals("footer"))continue;
                int x=integer(node,"x",0),buttonW=integer(node,"w",0),buttonH=integer(node,"h",0);
                assertTrue(x>=14&&x+buttonW<=w-14-reserved,str(view,"id","")+" / "+width);
                assertTrue(h>=150&&buttonH<=22&&h-31+buttonH<=h-8,str(view,"id","")+" / "+height);
            }
        }
    }
    @Test void councilAndMinistersAreDifferentDestinations()throws Exception{
        var c=catalog();var menu=c.menus().get("society");
        var office=c.actions().get(str(MenuSchema.find(menu,"governance-council"),"action",""));
        var ministers=c.actions().get(str(MenuSchema.find(menu,"council"),"action",""));
        assertEquals("governance council",str(office,"command",""));assertEquals("townycouncil",str(ministers,"command",""));
        assertNotEquals(str(office,"requiredPlugin",""),str(ministers,"requiredPlugin",""));
    }
    @Test void commandAndNativeHolderPermissionsStayIndependent()throws Exception{
        var c=catalog();var contracts=c.addons().entries().get("contracts");var builds=c.addons().entries().get("builds");
        assertEquals("mintcontracts.use",str(c.actions().get("addons:contracts"),"permission",""));
        assertEquals("neverland.orders.use",contracts.holderPermission("ru.neverland.mintcontracts.orders.PublicOrders$Board"));
        assertEquals("mintcontracts.use",contracts.holderPermission("ru.neverland.mintcontracts.gui.ContractMenuHolder"));
        assertEquals("neverlandtownybuilds.use",str(c.actions().get("addons:builds"),"permission",""));
        assertEquals("neverlandtownybuilds.army.use",builds.holderPermission("ru.neverland.townybuilds.army.ArmyService$ArmyHolder"));
    }
    @Test void movingButtonsPreservesExistingFavoriteIds()throws Exception{
        var c=catalog();var old=JsonParser.parseString(new String(resource("migrations/0.7.1/addons.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String,String> expected=new HashMap<>(),observed=new HashMap<>();
        for(var value:old.getAsJsonArray("modules")){var a=value.getAsJsonObject();expected.put("addons:"+str(a,"id",""),"button:"+str(a,"category","")+"/"+str(a,"id",""));}
        for(var menu:c.menus().values())MenuSchema.visit(menu.getAsJsonArray("components"),n->{String a=str(n,"action","");if(a.startsWith("addons:")){assertNull(observed.put(a,str(n,"directoryId","")));}});
        assertEquals(expected,observed);
        assertFalse(bool(c.menus().get("main"),"searchable",true));
    }
    @Test void allGridsFitWithoutHiddenButtonsAndLabelsMatchActions()throws Exception{
        var c=catalog();Map<String,String> names=new HashMap<>();
        for(var menu:c.menus().values())MenuSchema.visit(menu.getAsJsonArray("components"),n->{
            if(str(n,"type","").equals("grid")){
                int rows=(n.getAsJsonArray("children").size()+integer(n,"columns",1)-1)/integer(n,"columns",1);
                assertTrue(rows*integer(n,"rowHeight",0)+Math.max(0,rows-1)*integer(n,"gap",0)<=integer(n,"h",0),str(menu,"id","")+"/"+str(n,"id",""));
            }
            String a=str(n,"action","");if(a.startsWith("addons:")||a.startsWith("quick:")){
                String label=str(n,"text","");String before=names.putIfAbsent(a,label);if(before!=null)assertEquals(before,label,a);
                assertFalse(str(n,"tooltip","").isBlank(),a);
            }
        });
    }
    @Test void upgradeCoversEveryOldDefaultAndIsIdempotent()throws Exception{
        catalog();List<String> files=new ArrayList<>(List.of("addons.json","quick-actions.json","command-ui.json"));
        for(String name:List.of("main.json","hub.json","town.json","economy.json","adventures.json","society.json","management.json","help.yml"))files.add("menus/"+name);
        for(String file:files){byte[] old=resource("migrations/0.7.1/"+file);install(file,file.endsWith(".json")?JsonParser.parseString(new String(old,StandardCharsets.UTF_8)).toString().getBytes(StandardCharsets.UTF_8):old);}
        Map<String,byte[]> before=new HashMap<>();for(String file:files)before.put(file,Files.readAllBytes(root.resolve(file)));
        DefaultMenuMigration.apply(root);DefaultMenuMigration.apply(root);
        for(String file:files){assertArrayEquals(resource(file),Files.readAllBytes(root.resolve(file)),file);assertArrayEquals(before.get(file),Files.readAllBytes(root.resolve("backups/0.7.1/"+file)),file);}
        assertNotNull(ConfigLoader.load(root).actions().get("hub:development"));
    }
    @Test void customizedConfigRemainsAndGetsAnExplicitUpdateExample()throws Exception{
        catalog();String custom=new String(resource("migrations/0.7.1/menus/hub.json"),StandardCharsets.UTF_8).replace("NEVERLAND","Наш сервер");install("menus/hub.json",custom.getBytes(StandardCharsets.UTF_8));
        JsonObject links=JsonParser.parseString(new String(resource("migrations/0.7.1/quick-actions.json"),StandardCharsets.UTF_8)).getAsJsonObject();links.getAsJsonObject("orders").addProperty("command","customorders");install("quick-actions.json",links.toString().getBytes(StandardCharsets.UTF_8));
        DefaultMenuMigration.apply(root);
        var expected=JsonParser.parseString(custom).getAsJsonObject();for(var node:expected.getAsJsonArray("components"))if(node.getAsJsonObject().has("children"))node.getAsJsonObject().getAsJsonArray("children").asList().removeIf(e->str(e.getAsJsonObject(),"action","").equals("hub:society"));
        assertEquals(expected,ConfigLoader.read(root.resolve("menus/hub.json")));
        assertArrayEquals(resource("menus/hub.json"),Files.readAllBytes(root.resolve("updates/0.7.2/menus/hub.json")));
        var saved=ConfigLoader.read(root.resolve("quick-actions.json"));assertEquals("customorders",str(saved.getAsJsonObject("orders"),"command",""));assertTrue(saved.has("governance-council"));
    }
    @Test void choosingAnotherThemeDoesNotPreventNavigationUpgrade()throws Exception{
        catalog();var old=JsonParser.parseString(new String(resource("migrations/0.7.1/menus/town.json"),StandardCharsets.UTF_8)).getAsJsonObject();old.addProperty("theme","classic");install("menus/town.json",old.toString().getBytes(StandardCharsets.UTF_8));
        DefaultMenuMigration.apply(root);var result=ConfigLoader.read(root.resolve("menus/town.json"));assertEquals("classic",str(result,"theme",""));assertEquals("Мой город",str(result,"title",""));assertNotNull(MenuSchema.find(result,"warehouse"));
        assertEquals(old,ConfigLoader.read(root.resolve("backups/0.7.1/menus/town.json")));
    }
}
