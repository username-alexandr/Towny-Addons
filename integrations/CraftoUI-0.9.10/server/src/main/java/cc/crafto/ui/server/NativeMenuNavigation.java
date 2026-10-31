package cc.crafto.ui.server;
import java.util.*;

/** Only audited, action-tag-only Builds handlers may be grouped or deduplicated. */
final class NativeMenuNavigation {
    record Entry(int slot,String action,boolean project,boolean chest,String reason){}
    record Groups(List<Integer> auxiliary,List<Integer> hidden){}
    static boolean orderBoard(String owner,String holder){return owner.equals("NeverLandTownyContracts")&&holder.equals("ru.neverland.mintcontracts.orders.PublicOrders$Board");}
    static Map<String,Integer> detailsToolbar(String owner,String holder,List<Entry> entries){
        if(!owner.equals("NeverLandTownyBuilds")||!holder.equals("ru.neverland.townybuilds.gui.MenuManager$DetailsHolder"))return Map.of();
        var buttons=new LinkedHashMap<String,Integer>();
        for(var entry:entries){
            if(entry.slot()==17&&"build-orders".equals(entry.action()))buttons.put("order",17);
            if(entry.slot()==24&&"refresh".equals(entry.action()))buttons.put("refresh",24);
        }
        return buttons;
    }
    static int previewSlot(String owner,String holder,int tagged,List<Entry> entries){
        if(tagged>=0)return tagged;if(tagged==-2)return -1;
        // Earlier Builds tagged only the town hall. The real project summary remains
        // at slot 11 in DetailsHolder; the addon still chooses and authorizes the plan.
        return owner.equals("NeverLandTownyBuilds")&&holder.equals("ru.neverland.townybuilds.gui.MenuManager$DetailsHolder")&&entries.stream().anyMatch(e->e.slot()==11&&e.project())?11:-1;
    }
    static Groups group(String owner,String holder,List<Entry> entries){
        if(!owner.equals("NeverLandTownyBuilds")||!(holder.endsWith("$ProjectListHolder")||holder.endsWith("$CategoryHolder")))return new Groups(List.of(),List.of());
        var aux=new ArrayList<Integer>();var hidden=new ArrayList<Integer>();var seen=new HashSet<String>();
        var shortcuts=Set.of("specialization","research","power","policies","upkeep","market","treasury");
        var navigation=Set.of("categories-back","projects-previous","projects-next");
        for(var e:entries){
            if(e.project())continue;
            if(e.action()!=null&&(shortcuts.contains(e.action())||navigation.contains(e.action()))){
                if(!seen.add(e.action()+"\n"+e.reason())){hidden.add(e.slot());continue;}
                if(shortcuts.contains(e.action()))aux.add(e.slot());
            }
            if(holder.endsWith("$ProjectListHolder")&&e.slot()==49&&e.action()==null&&e.chest())aux.add(e.slot());
        }
        return new Groups(List.copyOf(aux),List.copyOf(hidden));
    }
}
