package cc.crafto.ui.server;

import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.Locale;

/** Finds an existing addon navigation control; never interprets a storage item's name as a command. */
final class NativeBack {
    static boolean backAction(String action){return action!=null&&action.matches("(?:[a-z0-9]+[-_])*back");}
    static int slot(Inventory inventory,String owner,boolean editable){
        int found=-1;
        for(int i=0;i<inventory.getSize();i++){
            var item=inventory.getItem(i);if(item==null||!item.hasItemMeta())continue;
            var meta=item.getItemMeta();boolean back=false;var data=meta.getPersistentDataContainer();
            for(var key:data.getKeys()){
                if(!key.getNamespace().equals(owner.toLowerCase(Locale.ROOT))||!key.getKey().endsWith("action"))continue;
                String action=data.get(key,PersistentDataType.STRING);
                if(backAction(action))back=true;
            }
            if(!editable&&meta.hasDisplayName()){
                String name=PlainTextComponentSerializer.plainText().serialize(meta.displayName()).replaceAll("(?i)§[0-9a-fk-orx]","").replaceAll("^[^\\p{L}]+","").strip().toLowerCase(Locale.ROOT);
                back|=name.equals("назад")||name.startsWith("назад ")||name.equals("back");
            }
            if(back){if(found>=0)return -1;found=i;}
        }
        return found;
    }
    static boolean reusable(String holder,boolean editable){
        // These views consume their selection once. Reopen their read-only entry instead of a used holder.
        if(holder.equals("ru.neverland.townysieges.RaidHours$View")||holder.equals("ru.neverland.townybuilds.construction.CityStyles$View"))return false;
        // Storage/resource editors commit or discard state on close; never reopen such an old instance.
        return !editable||holder.endsWith("$EditorIndexHolder")||holder.endsWith("$ProjectEditorHolder")||holder.endsWith("$EffectEditorHolder");
    }
    static String reopen(String owner,String holder){
        if(owner.equals("NeverLandTownySiegesPlus")&&holder.equals("ru.neverland.townysieges.RaidHours$View"))return "townysieges hours";
        if(owner.equals("NeverLandTownyBuilds")&&holder.equals("ru.neverland.townybuilds.construction.CityStyles$View"))return "townystyle";
        return "";
    }
}
