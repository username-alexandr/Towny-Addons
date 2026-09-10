package ru.neverland.townymarket;
import java.util.*;
import java.security.MessageDigest;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.localization.*;
public final class MarketCatalog {
    public record Product(String id,ItemStack item,long base){}
    private final JavaPlugin plugin;private final MaterialLabels names=new MaterialLabels();private Map<String,Product> products=Map.of();
    public MarketCatalog(JavaPlugin plugin){this.plugin=plugin;reload();}
    public void reload(){MaterialNameConfig.reload(plugin,names);Map<String,Product> next=new LinkedHashMap<>();var y=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("catalog.yml").toFile());var root=y.getConfigurationSection("products");if(root==null)throw new IllegalArgumentException("В catalog.yml нет products");
        for(String id:root.getKeys(false)){var s=root.getConfigurationSection(id);if(s==null)throw new IllegalArgumentException("Некорректный товар "+id);var item=parse(s.getString("item",id));if(item==null){plugin.getLogger().warning("Товар каталога недоступен: "+id);continue;}next.put(id.toLowerCase(Locale.ROOT),new Product(id.toLowerCase(Locale.ROOT),item,MarketData.cents(s.getString("base-price","1"))));}products=Map.copyOf(next);
    }
    public Collection<Product> products(){return products.values();}
    public Product match(ItemStack item){return products.values().stream().filter(p->p.item().isSimilar(item)).findFirst().orElse(null);}
    public ItemStack select(String key){var p=products.get(key.toLowerCase(Locale.ROOT));return p==null?parse(key):p.item().clone();}
    private ItemStack parse(String key){
        if(key==null)return null;if(key.toLowerCase(Locale.ROOT).startsWith("itemsadder:")){var source=Bukkit.getPluginManager().getPlugin("ItemsAdder");if(source==null||!source.isEnabled())return null;
            try{var api=Class.forName("dev.lone.itemsadder.api.CustomStack",true,source.getClass().getClassLoader());var object=api.getMethod("getInstance",String.class).invoke(null,key.substring(11));if(object==null)return null;var item=(ItemStack)api.getMethod("getItemStack").invoke(object);item=item.clone();item.setAmount(1);return item;}catch(ReflectiveOperationException ex){return null;}}
        var material=MaterialNameConfig.matchMaterial(key);return material==null||!material.isItem()||material.isAir()?null:new ItemStack(material);
    }
    public String label(ItemStack item){String custom=MaterialNameConfig.customName(item.getItemMeta());return custom==null?names.name(item.getType().name()):ChatColor.stripColor(custom).replaceAll("(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]","");}
    public static String encode(ItemStack item){var copy=item.clone();copy.setAmount(1);return Base64.getEncoder().encodeToString(copy.serializeAsBytes());}
    public static ItemStack decode(String item){return ItemStack.deserializeBytes(Base64.getDecoder().decode(item));}
    public static String key(String data){try{return "custom_"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(data)));}catch(Exception ex){throw new IllegalArgumentException("Неверный образец",ex);}}
}
