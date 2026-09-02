package ru.neverland.townychronicles.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townychronicles.model.ChronicleEntry;
import ru.neverland.townychronicles.util.ColorUtil;
import ru.neverland.townychronicles.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public final class ChronicleBookService {
    private final JavaPlugin plugin;private final ChronicleRepository repository;public ChronicleBookService(JavaPlugin plugin,ChronicleRepository repository){this.plugin=plugin;this.repository=repository;}
    public boolean give(Player player,Town town){ItemStack book=create(town);if(!player.getInventory().addItem(book).isEmpty())return false;return true;}
    public ItemStack create(Town town){ItemStack stack=new ItemStack(Material.WRITTEN_BOOK);BookMeta meta=(BookMeta)stack.getItemMeta();meta.setTitle(limit(plugin.getConfig().getString("book.title","Летопись %town%").replace("%town%",town.getName()),32));meta.setAuthor(limit(plugin.getConfig().getString("book.author","NeverLand"),16));List<ChronicleEntry>entries=repository.entries(town.getUUID());int maximum=Math.min(entries.size(),Math.max(1,plugin.getConfig().getInt("book.maximum-entries",40)));List<String>pages=new ArrayList<>();pages.add(ColorUtil.color("&lЛетопись города\n&0"+town.getName()+"\n\n&8Записей: &0"+entries.size()+"\n&8Основан: &0"+TimeUtil.date(TimeUtil.normalizedEpoch(town.getRegistered()))));for(int index=maximum-1;index>=0;index--){ChronicleEntry entry=entries.get(index);StringBuilder page=new StringBuilder();page.append(entry.category().display()).append("\n").append(TimeUtil.date(entry.timestamp())).append("\n\n").append(ColorUtil.strip(entry.title()));for(String detail:entry.details())page.append("\n").append(ColorUtil.strip(detail));pages.add(limit(page.toString(),900));}meta.setPages(pages);stack.setItemMeta(meta);return stack;}
    private String limit(String value,int maximum){return value.length()<=maximum?value:value.substring(0,maximum);}
}
