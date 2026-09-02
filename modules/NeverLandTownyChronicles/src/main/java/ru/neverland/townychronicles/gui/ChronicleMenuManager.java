package ru.neverland.townychronicles.gui;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.ChronicleEntry;
import ru.neverland.townychronicles.model.TownChronicleState;
import ru.neverland.townychronicles.service.ChronicleBookService;
import ru.neverland.townychronicles.service.ChronicleRepository;
import ru.neverland.townychronicles.service.MessageService;
import ru.neverland.townychronicles.util.ColorUtil;
import ru.neverland.townychronicles.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChronicleMenuManager implements Listener {
    private final JavaPlugin plugin;private final TownyHook towny;private final ChronicleRepository repository;private final ChronicleBookService books;private final MessageService messages;private final NamespacedKey actionKey;
    public ChronicleMenuManager(JavaPlugin plugin,TownyHook towny,ChronicleRepository repository,ChronicleBookService books,MessageService messages){this.plugin=plugin;this.towny=towny;this.repository=repository;this.books=books;this.messages=messages;actionKey=new NamespacedKey(plugin,"action");}
    public void open(Player player,ChronicleCategory category,int requestedPage){Town town=towny.town(player);if(town==null){messages.send(player,"no-town");return;}List<ChronicleEntry>entries=repository.entries(town.getUUID(),category);int perPage=Math.max(9,Math.min(45,plugin.getConfig().getInt("gui.entries-per-page",45)));int pages=Math.max(1,(entries.size()+perPage-1)/perPage),page=Math.max(0,Math.min(requestedPage,pages-1));ChronicleMenuHolder holder=new ChronicleMenuHolder(town.getUUID(),category,page);String title=plugin.getConfig().getString("gui.title","Летопись %town%").replace("%town%",town.getName());Inventory inventory=Bukkit.createInventory(holder,54,ColorUtil.color(title));holder.inventory(inventory);fill(inventory);int from=page*perPage,to=Math.min(entries.size(),from+perPage),slot=0;for(ChronicleEntry entry:entries.subList(from,to))inventory.setItem(slot++,entry(entry));if(page>0)inventory.setItem(45,action("previous",Material.ARROW,"&#FFFFFFПредыдущая страница",List.of("&7Страница "+page+" из "+pages)));inventory.setItem(47,statsItem(town));inventory.setItem(49,action("book",Material.WRITTEN_BOOK,"&#FFD45AКнига-летопись",List.of("&7Получить историю города книгой.","","&#55FF55Нажмите, чтобы получить")));inventory.setItem(51,item(Material.HOPPER,category==null?"&#FFFFFFВсе категории":category.color()+category.display(),List.of("&7Фильтр задаётся командой:","&f/t chronicles <категория>")));if(page+1<pages)inventory.setItem(53,action("next",Material.ARROW,"&#FFFFFFСледующая страница",List.of("&7Страница "+(page+2)+" из "+pages)));player.openInventory(inventory);}
    public void sendStats(Player player,Town town){TownChronicleState state=repository.state(town.getUUID());long wonders=state.wonders().values().stream().filter(v->v>0).count();player.sendMessage(ColorUtil.color("&#D9B6FF&lЛетопись города "+town.getName()));player.sendMessage(ColorUtil.color("&7Основан: &f"+TimeUtil.date(state.foundedAt())));player.sendMessage(ColorUtil.color("&7Возраст: &f"+TimeUtil.days(state.foundedAt())+" дней"));player.sendMessage(ColorUtil.color("&7Записей: &f"+repository.entries(town.getUUID()).size()));player.sendMessage(ColorUtil.color("&7Достижений: &f"+state.achievements().size()));player.sendMessage(ColorUtil.color("&7Чудес Света: &f"+wonders));}
    @EventHandler public void onClick(InventoryClickEvent event){if(!(event.getInventory().getHolder() instanceof ChronicleMenuHolder holder))return;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player))return;Town town=towny.town(player);if(town==null||!town.getUUID().equals(holder.townId())){player.closeInventory();return;}ItemStack clicked=event.getCurrentItem();if(clicked==null||!clicked.hasItemMeta())return;String action=clicked.getItemMeta().getPersistentDataContainer().get(actionKey,PersistentDataType.STRING);if(action==null)return;switch(action){case "previous"->open(player,holder.category(),holder.page()-1);case "next"->open(player,holder.category(),holder.page()+1);case "book"->{if(!player.hasPermission("townychronicles.book")){messages.send(player,"no-permission");return;}if(books.give(player,town))messages.send(player,"book-given",Map.of("town",town.getName()));else messages.send(player,"book-no-space");}}}
    private ItemStack entry(ChronicleEntry entry){List<String>lore=new ArrayList<>();lore.add("&7"+TimeUtil.date(entry.timestamp()));lore.add("&7Категория: "+entry.category().color()+entry.category().display());lore.add("");for(String line:entry.details())lore.add("&f"+line);if(!entry.actor().isBlank())lore.add("&7Участник: &f"+entry.actor());lore.add("");lore.add("&8ID: "+entry.shortId());return item(entry.category().icon(),entry.category().color()+entry.title(),lore);}
    private ItemStack statsItem(Town town){TownChronicleState state=repository.state(town.getUUID());return item(Material.CLOCK,"&#65B8FFСтатистика",List.of("&7Возраст: &f"+TimeUtil.days(state.foundedAt())+" дней","&7Записей: &f"+repository.entries(town.getUUID()).size(),"&7Достижений: &f"+state.achievements().size(),"&7Чудес: &f"+state.wonders().values().stream().filter(v->v>0).count()));}
    private void fill(Inventory inventory){Material material=Material.matchMaterial(plugin.getConfig().getString("gui.filler","BLACK_STAINED_GLASS_PANE"));ItemStack filler=item(material==null?Material.BLACK_STAINED_GLASS_PANE:material," ",List.of());for(int i=0;i<inventory.getSize();i++)inventory.setItem(i,filler);}
    private ItemStack action(String action,Material material,String name,List<String>lore){ItemStack stack=item(material,name,lore);ItemMeta meta=stack.getItemMeta();meta.getPersistentDataContainer().set(actionKey,PersistentDataType.STRING,action);stack.setItemMeta(meta);return stack;}
    private ItemStack item(Material material,String name,List<String>lore){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.setDisplayName(ColorUtil.color(name));meta.setLore(lore.stream().map(ColorUtil::color).toList());meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);stack.setItemMeta(meta);return stack;}
}
