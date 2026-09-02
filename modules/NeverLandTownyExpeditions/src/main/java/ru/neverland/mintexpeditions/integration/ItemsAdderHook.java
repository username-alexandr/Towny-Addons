package ru.neverland.mintexpeditions.integration;
import org.bukkit.Bukkit; import org.bukkit.inventory.ItemStack; import java.lang.reflect.Method;
public final class ItemsAdderHook {
    private boolean available; public ItemsAdderHook(){reload();} public void reload(){available=Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");}
    public ItemStack item(String key){if(!available||key==null||!key.toLowerCase().startsWith("itemsadder:"))return null; try{Class<?> c=Class.forName("dev.lone.itemsadder.api.CustomStack"); Object o=c.getMethod("getInstance",String.class).invoke(null,key.substring(11)); return o==null?null:(ItemStack)c.getMethod("getItemStack").invoke(o);}catch(ReflectiveOperationException|ClassCastException e){return null;}}
}
