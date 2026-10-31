package cc.crafto.ui.server;

import com.google.gson.JsonObject;
import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Layout hints for allowlisted, non-editable menus. Slots still use native inventory clicks. */
final class NativeMenuPresentation {
    static JsonObject options(Inventory inventory,boolean editable){
        return options(inventory,editable,"","");
    }
    static JsonObject options(Inventory inventory,boolean editable,String owner,String holder){
        var result=new JsonObject();var roles=new JsonObject();var disabled=new JsonObject();
        var auxiliary=new com.google.gson.JsonArray();var hidden=new com.google.gson.JsonArray();var adminFooter=new com.google.gson.JsonArray();var toolbar=new JsonObject();int previewSlot=-1,statusSlot=-1;
        boolean adminMenu=adminMenu(owner,holder);
        if(!editable){
            var ambiguous=new HashSet<String>();
            for(int slot=0;slot<inventory.getSize();slot++){
                var item=inventory.getItem(slot);if(item==null||!item.hasItemMeta())continue;var meta=item.getItemMeta();
                String role=meta.getPersistentDataContainer().get(new NamespacedKey("neverland","menu_role"),PersistentDataType.STRING);
                if("building-preview".equals(role))previewSlot=previewSlot==-1?slot:-2;
                if(role==null&&meta.hasDisplayName())role=role(PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
                if(owner.equals("NeverLandTownyControl")&&holder.equals("ru.neverland.townycontrol.AdminMenu$Screen")&&slot==4&&item.getType()==org.bukkit.Material.NETHER_STAR&&meta.hasDisplayName()
                    &&PlainTextComponentSerializer.plainText().serialize(meta.displayName()).replaceAll("(?i)§[0-9a-fk-orx]","").strip().equals("Управление сервером"))hidden.add(slot);
                if(role!=null&&Set.of("back","close","previous","next","confirm","cancel").contains(role)){
                    if(roles.has(role)){roles.remove(role);ambiguous.add(role);}else if(!ambiguous.contains(role))roles.addProperty(role,slot);
                }
                String reason=meta.getPersistentDataContainer().get(new NamespacedKey("neverland","disabled_reason"),PersistentDataType.STRING);
                if(reason!=null&&!reason.isBlank())disabled.addProperty(Integer.toString(slot),reason.substring(0,Math.min(1500,reason.length())));
            }
            // The addon marks "Все категории" as action=categories-back. NativeBack already
            // uses this same verified control for the header; do not render it a second time.
            int back=NativeBack.slot(inventory,owner,false);if(back>=0&&!roles.has("back"))roles.addProperty("back",back);
            var entries=new ArrayList<NativeMenuNavigation.Entry>();
            for(int slot=0;slot<inventory.getSize();slot++){
                var item=inventory.getItem(slot);if(item==null||!item.hasItemMeta())continue;var data=item.getItemMeta().getPersistentDataContainer();
                String action=owner.isBlank()?null:data.get(new NamespacedKey(owner.toLowerCase(Locale.ROOT),"action"),PersistentDataType.STRING);
                boolean project=!owner.isBlank()&&data.has(new NamespacedKey(owner.toLowerCase(Locale.ROOT),"project"),PersistentDataType.STRING);
                entries.add(new NativeMenuNavigation.Entry(slot,action,project,item.getType()==org.bukkit.Material.CHEST,disabled.has(Integer.toString(slot))?disabled.get(Integer.toString(slot)).getAsString():""));
            }
            var groups=NativeMenuNavigation.group(owner,holder,entries);groups.auxiliary().forEach(auxiliary::add);groups.hidden().forEach(hidden::add);
            if(adminMenu){
                Set<Integer> nav=new HashSet<>();roles.entrySet().forEach(e->nav.add(e.getValue().getAsInt()));
                for(int slot=36;slot<inventory.getSize();slot++){
                    var item=inventory.getItem(slot);
                    if(nav.contains(slot)||item==null||item.getType()==org.bukkit.Material.GRAY_STAINED_GLASS_PANE||!item.hasItemMeta()||!item.getItemMeta().hasDisplayName())continue;
                    if(PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).strip().isEmpty())continue;
                    adminFooter.add(slot);
                }
            }
            previewSlot=NativeMenuNavigation.previewSlot(owner,holder,previewSlot,entries);
            if(NativeMenuNavigation.orderBoard(owner,holder)&&inventory.getSize()==54&&material(inventory,45)==org.bukkit.Material.BOOK&&material(inventory,49)==org.bukkit.Material.CLOCK&&material(inventory,53)==org.bukkit.Material.EMERALD){
                toolbar.addProperty("primary",45);toolbar.addProperty("info",53);statusSlot=49;
                if(material(inventory,47)==org.bukkit.Material.FISHING_ROD)toolbar.addProperty("help",47);
            }
            // Use the addon-owned clickable items; only their position changes.
            NativeMenuNavigation.detailsToolbar(owner,holder,entries).forEach(toolbar::addProperty);

        }
        result.add("roles",roles);result.add("disabled",disabled);result.add("auxiliary",auxiliary);result.add("hidden",hidden);result.add("adminFooter",adminFooter);result.add("toolbar",toolbar);result.addProperty("adminMenu",adminMenu);result.addProperty("statusSlot",statusSlot);result.addProperty("previewSlot",Math.max(-1,previewSlot));return result;
    }
    private static org.bukkit.Material material(Inventory inventory,int slot){var item=inventory.getItem(slot);return item==null?org.bukkit.Material.AIR:item.getType();}
    static boolean adminMenu(String owner,String holder){return owner.equals("NeverLandTownyControl")&&holder.equals("ru.neverland.townycontrol.AdminMenu$Screen");}
    static String role(String text){return switch(text.replaceAll("(?i)§[0-9a-fk-orx]","").strip().toLowerCase(Locale.ROOT)){
        case "назад"->"back";case "закрыть","вернуться в игру"->"close";
        case "предыдущая страница"->"previous";case "следующая страница"->"next";
        case "подтвердить","подтвердить установку","подтвердить сдачу","подтвердить отмену","подтвердить место и начать стройку"->"confirm";
        case "отмена","отменить","продолжить строительство","отложить размещение"->"cancel";default->null;
    };}
}
