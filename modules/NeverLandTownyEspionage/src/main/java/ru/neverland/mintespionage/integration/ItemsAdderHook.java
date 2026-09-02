package ru.neverland.mintespionage.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Method;

public final class ItemsAdderHook {
    private boolean available;public ItemsAdderHook(){reload();}public void reload(){available=Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");}
    public ItemStack item(String key){
        if(!available||key==null||key.isBlank())return null;String namespaced=key.startsWith("itemsadder:")?key.substring(11):key;
        try{Class<?> type=Class.forName("dev.lone.itemsadder.api.CustomStack");Object value=type.getMethod("getInstance",String.class).invoke(null,namespaced);
            return value==null?null:(ItemStack)type.getMethod("getItemStack").invoke(value);
        }catch(ReflectiveOperationException|ClassCastException ignored){return null;}
    }
}
