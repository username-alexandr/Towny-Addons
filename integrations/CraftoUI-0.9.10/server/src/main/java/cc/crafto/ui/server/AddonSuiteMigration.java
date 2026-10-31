package cc.crafto.ui.server;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Add newly supported integration paths without restoring excluded modules or replacing custom handlers. */
final class AddonSuiteMigration {

    private static final Map<String,String> ANCHORS=Map.ofEntries(
        Map.entry("NeverLandTownyArchaeology","ru.neverland.archaeology.gui.ArchaeologyMenuHolder"),
        Map.entry("NeverLandTownyArmy","ru.neverland.townyarmy.ArmyMenu$Holder"),
        Map.entry("NeverLandTownyBuilds","ru.neverland.townybuilds.gui.MenuManager$DetailsHolder"),
        Map.entry("NeverLandTownyCamps","ru.neverland.mintcamps.gui.MenuManager$MenuHolder"),
        Map.entry("NeverLandTownyChronicles","ru.neverland.townychronicles.gui.ChronicleMenuHolder"),
        Map.entry("NeverLandTownyCitizens","ru.neverland.townycitizens.CitizensMenus$Holder"),
        Map.entry("NeverLandTownyCompanies","ru.neverland.townycompanies.CompanyMenus$View"),
        Map.entry("NeverLandTownyContracts","ru.neverland.mintcontracts.gui.ContractMenuHolder"),
        Map.entry("NeverLandTownyCouncil","ru.neverland.townycouncil.CouncilMenus$Holder"),
        Map.entry("NeverLandTownyCrime","ru.neverland.townycrime.CrimeMenu$Holder"),
        Map.entry("NeverLandTownyDiplomacy","ru.neverland.townydiplomacy.DiplomacyMenus$Holder"),
        Map.entry("NeverLandTownyElections","ru.neverland.townyelections.ElectionsMenus$Holder"),
        Map.entry("NeverLandTownyEnvironment","ru.neverland.townyenvironment.gui.EnvironmentMenu$View"),
        Map.entry("NeverLandTownyEvents","ru.neverland.mintevents.gui.EventMenuHolder"),
        Map.entry("NeverLandTownyJobs","ru.neverland.townyjobs.JobsMenus$View"),
        Map.entry("NeverLandTownyJustice","ru.neverland.townyjustice.JusticeMenu$Holder"),
        Map.entry("NeverLandTownyMarket","ru.neverland.townymarket.MarketMenus$View"),
        Map.entry("NeverLandTownyPopulation","ru.neverland.townypopulation.gui.PopulationMenu$View"),
        Map.entry("NeverLandTownyQuests","ru.neverland.townyquests.gui.QuestMenu$View"),
        Map.entry("NeverLandTownyResearch","ru.neverland.townyresearch.gui.ResearchMenu$View"),
        Map.entry("NeverLandTownySeasons","ru.neverland.townyseasons.SeasonMenu$Holder"),
        Map.entry("NeverLandTownySiegesPlus","ru.neverland.townysieges.SiegeMenu$Holder"),
        Map.entry("NeverLandTownySpecialization","ru.neverland.townyspecialization.gui.SpecializationMenu$View"),
        Map.entry("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.gui.TreasuryMenu$View"));
    static void apply(Path root)throws IOException{
        apply(root,"0.8.19");apply(root,"0.8.20");
    }
    private static void apply(Path root,String version)throws IOException{
        merge(root,version,"addons.json",AddonSuiteMigration::menus);
        merge(root,version,"command-ui.json",AddonSuiteMigration::commands);
        merge(root,version,"quick-actions.json",(current,old,defaults)->{
            for(var e:defaults.entrySet())if(!old.has(e.getKey())&&!current.has(e.getKey()))current.add(e.getKey(),e.getValue().deepCopy());
        });
    }
    private interface Merger {void apply(JsonObject current,JsonObject old,JsonObject defaults);}
    private static void merge(Path root,String version,String name,Merger operation)throws IOException{
        Path file=root.resolve(name);if(!Files.isRegularFile(file)||Files.size(file)>262144)return;
        try{
            var current=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var before=current.deepCopy();
            operation.apply(current,resource("migrations/"+version+"/"+name),resource(name));
            if(!before.equals(current))DefaultMenuMigration.replace(root,file,version,name,
                (new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(current)+"\n").getBytes(StandardCharsets.UTF_8));
        }catch(JsonParseException|IllegalStateException|UnsupportedOperationException|ClassCastException ignored){/* ConfigLoader reports malformed custom data. */}
    }
    private static JsonObject resource(String name)throws IOException{
        try(var in=AddonSuiteMigration.class.getClassLoader().getResourceAsStream(name)){
            if(in==null)throw new FileNotFoundException(name);
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static Map<String,JsonObject> modules(JsonObject value){
        Map<String,JsonObject> out=new HashMap<>();
        if(!value.has("modules")||!value.get("modules").isJsonArray())throw new IllegalStateException("Missing module list");
        for(var item:value.getAsJsonArray("modules")){var entry=item.getAsJsonObject();String plugin=str(entry,"plugin","");if(out.put(plugin,entry)!=null)throw new IllegalStateException("Duplicate module");}
        return out;
    }
    private static void menus(JsonObject current,JsonObject old,JsonObject defaults){
        var previous=modules(old);var next=modules(defaults);
        for(var e:modules(current).entrySet()){
            var entry=e.getValue();String anchor=ANCHORS.get(e.getKey());
            if(anchor==null||!previous.containsKey(e.getKey())||!next.containsKey(e.getKey()))continue;
            var holders=entry.getAsJsonArray("holders");if(holders==null)continue;
            if(entry.has("holderPermissions")&&!entry.get("holderPermissions").isJsonObject())continue;
            var was=previous.get(e.getKey()).getAsJsonArray("holders");var desired=next.get(e.getKey());
            for(var holder:desired.getAsJsonArray("holders"))if(!was.contains(holder)&&!holders.contains(holder)){
                String required=holder.getAsString().equals("ru.neverland.mintcontracts.orders.ResourceAdmin$Screen")?"ru.neverland.mintcontracts.orders.PublicOrders$Board":anchor;
                if(!holders.contains(new JsonPrimitive(required)))continue;
                holders.add(holder.deepCopy());
                if(desired.has("holderPermissions")&&desired.getAsJsonObject("holderPermissions").has(holder.getAsString())){
                    if(!entry.has("holderPermissions"))entry.add("holderPermissions",new JsonObject());
                    var permissions=entry.getAsJsonObject("holderPermissions");
                    if(!permissions.has(holder.getAsString()))permissions.add(holder.getAsString(),desired.getAsJsonObject("holderPermissions").get(holder.getAsString()).deepCopy());
                }
            }
        }
    }
    private static void commands(JsonObject current,JsonObject old,JsonObject defaults){
        var previous=modules(old);var next=modules(defaults);
        for(var e:modules(current).entrySet()){
            if(!previous.containsKey(e.getKey())||!next.containsKey(e.getKey()))continue;
            var entry=e.getValue();var was=previous.get(e.getKey());var desired=next.get(e.getKey());
            boolean unchanged=entry.equals(was);
            var forms=entry.getAsJsonArray("forms");if(forms==null||forms.isEmpty()&&!was.getAsJsonArray("forms").isEmpty())continue;
            if(unchanged&&!Objects.equals(was.get("permission"),desired.get("permission")))entry.add("permission",desired.get("permission").deepCopy());
            var ids=new HashSet<String>();for(var form:forms)ids.add(str(form.getAsJsonObject(),"id",""));
            var oldIds=new HashSet<String>();for(var form:was.getAsJsonArray("forms"))oldIds.add(str(form.getAsJsonObject(),"id",""));
            for(var item:desired.getAsJsonArray("forms")){
                var form=item.getAsJsonObject();String id=str(form,"id","");if(oldIds.contains(id)||ids.contains(id))continue;
                if(entry.has("roots")&&!entry.getAsJsonArray("roots").isEmpty()&&!entry.getAsJsonArray("roots").contains(form.get("root")))continue;
                forms.add(form.deepCopy());ids.add(id);
            }
        }
    }
}
