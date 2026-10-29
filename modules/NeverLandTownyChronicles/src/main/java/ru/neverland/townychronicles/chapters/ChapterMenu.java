package ru.neverland.townychronicles.chapters;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.model.*;
import ru.neverland.townychronicles.service.*;

/** On-demand immutable views; no disk work or cross-region entity access in menus. */
public final class ChapterMenu implements Listener {
    private record Input(UUID town,List<ChronicleEntry> entries,List<CityChapters.Fact> receipts,long asOf){}
    private final JavaPlugin plugin;private final TownyHook towns;private final ChronicleRepository repository;private final ContributionsMenu history;
    private final DerivedViewCache<Input,List<CityChapters.Chapter>> cache;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));
    public ChapterMenu(JavaPlugin plugin,TownyHook towns,ChronicleRepository repository,ContributionsMenu history){
        this.plugin=plugin;this.towns=towns;this.repository=repository;this.history=history;
        cache=new DerivedViewCache<>("NeverLand-Chronicle-Chapters",i->CityChapters.build(i.town(),i.entries(),i.receipts(),i.asOf()),ex->plugin.getLogger().warning("Главы летописи: "+ex.getMessage()));
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    public void open(Player p,CityChapters.Theme theme,int requested){try(var guard=SuiteState.guard()){
        var town=towns.town(p);if(town==null||!p.hasPermission("townychronicles.use"))throw new IllegalArgumentException("Летопись доступна жителям города");
        if(SuiteState.frozen(plugin.getName())||!plugin.getConfig().getBoolean("chapters.enabled",true))throw new IllegalArgumentException("Главы летописи временно отключены. Обычная история города сохранена");
        var evidence=history.chapterEvidence();long asOf=System.currentTimeMillis()/60_000*60_000;
        var reading=cache.read(town.getUUID(),new Input(town.getUUID(),repository.entries(town.getUUID()),evidence.towns().getOrDefault(town.getUUID(),List.of()),asOf));
        List<CityChapters.Chapter> chapters=reading.result()==null?List.of():reading.result().stream().filter(c->c.theme()==theme).toList();
        int pages=Math.max(1,(chapters.size()+44)/45),page=Math.max(0,Math.min(requested,pages-1));var view=new View(p.getUniqueId(),town.getUUID(),theme,page);
        view.inventory=MenuStyle.inventory(plugin,view,54,"Главы • "+name(theme));
        for(int i=page*45;i<Math.min(chapters.size(),(page+1)*45);i++){
            var c=chapters.get(i);int slot=i%45;view.chapters.put(slot,c);
            var lore=new ArrayList<String>(List.of("С "+DATE.format(Instant.ofEpochMilli(c.from())),"По "+DATE.format(Instant.ofEpochMilli(c.until()))+" МСК","Подтверждённых записей: "+c.facts().size()));
            long war=c.facts().stream().filter(f->f.category()==ChronicleCategory.WAR).count(),recovery=c.facts().stream().filter(f->f.category()==ChronicleCategory.RECOVERY).count();
            lore.add("События войн: "+war+" • восстановление: "+recovery);
            lore.add("Записанные расходы на ремонт: "+c.repairCost().toPlainString());if(c.unknownCosts()>0)lore.add("Без подтверждённой суммы: "+c.unknownCosts());
            lore.add("Нажмите, чтобы прочитать записи");view.inventory.setItem(slot,item(icon(theme),c.title(),lore));
        }
        if(chapters.isEmpty())view.inventory.setItem(22,item(Material.BOOK,reading.result()==null?"Главы готовятся":"Записей по этой теме пока нет",List.of(reading.result()==null?reading.issue():"Сводка появится из сохранённой истории города")));
        if(page>0)view.inventory.setItem(45,item(Material.ARROW,"Предыдущая страница",List.of()));
        view.inventory.setItem(46,item(Material.PLAYER_HEAD,"Правление",List.of("По записанным сменам мэра")));view.inventory.setItem(47,item(Material.IRON_SWORD,"Войны",List.of("По месяцам • без предположений о победителе")));view.inventory.setItem(48,item(Material.ANVIL,"Восстановление",List.of("Подтверждённые ремонты и возрождение города")));
        view.inventory.setItem(49,item(Material.ARROW,"Назад в летопись",List.of()));view.inventory.setItem(50,item(Material.SUNFLOWER,"Обновить",List.of("Заново проверить сохранённые сведения")));
        var info=new ArrayList<String>(List.of("Время: Москва","Сводка по сохранившимся записям","Старые удалённые записи не восстанавливаются"));
        info.add(history.historyIssue().isEmpty()?"Квитанции обновлены: "+DATE.format(Instant.ofEpochMilli(history.historyRefreshed()))+" МСК":history.historyIssue());
        if(evidence.truncated().contains(town.getUUID()))info.add("Показаны последние "+CityChapters.MAX_FACTS+" квитанций");
        view.inventory.setItem(51,item(Material.PAPER,"О полноте истории",info));if(page+1<pages)view.inventory.setItem(53,item(Material.ARROW,"Следующая страница",List.of()));p.openInventory(view.inventory);
    }}
    private void detail(Player p,View parent,CityChapters.Chapter chapter,int requested){
        int pages=Math.max(1,(chapter.facts().size()+44)/45),page=Math.max(0,Math.min(requested,pages-1));var view=new View(p.getUniqueId(),parent.town,parent.theme,parent.page);view.chapter=chapter;view.factPage=page;
        view.inventory=MenuStyle.inventory(plugin,view,54,"Записи главы");
        for(int i=page*45;i<Math.min(chapter.facts().size(),(page+1)*45);i++){var f=chapter.facts().get(i);var lore=new ArrayList<String>(List.of(DATE.format(Instant.ofEpochMilli(f.at()))+" МСК","Источник: "+f.source()));if(!f.actor().isBlank())lore.add("Участник: "+f.actor());if(f.repairCost()!=null)lore.add("Расход казны на ремонт: "+f.repairCost().toPlainString());lore.add("Запись: "+f.id());view.inventory.setItem(i%45,item(f.category().icon(),f.title(),lore));}
        if(page>0)view.inventory.setItem(45,item(Material.ARROW,"Предыдущая страница",List.of()));view.inventory.setItem(49,item(Material.ARROW,"Назад к главам",List.of()));if(page+1<pages)view.inventory.setItem(53,item(Material.ARROW,"Следующая страница",List.of()));p.openInventory(view.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(v.owner)||e.getRawSlot()<0||e.getRawSlot()>=54)return;int slot=e.getRawSlot();
        SuiteScheduler.player(plugin,p,ignored->{try(var guard=SuiteState.guard()){
            if(p.getOpenInventory().getTopInventory()!=v.inventory)return;
            var town=towns.town(p);if(town==null||!town.getUUID().equals(v.town)||!p.hasPermission("townychronicles.use")||SuiteState.frozen(plugin.getName())||!plugin.getConfig().getBoolean("chapters.enabled",true)){p.closeInventory();return;}
            try{if(v.chapter!=null){if(slot==49)open(p,v.theme,v.page);else if(slot==45)detail(p,v,v.chapter,v.factPage-1);else if(slot==53)detail(p,v,v.chapter,v.factPage+1);return;}
                if(v.chapters.containsKey(slot)){detail(p,v,v.chapters.get(slot),0);return;}
                switch(slot){case 45->open(p,v.theme,v.page-1);case 46->open(p,CityChapters.Theme.REIGN,0);case 47->open(p,CityChapters.Theme.WAR,0);case 48->open(p,CityChapters.Theme.RECOVERY,0);case 49->p.performCommand("chronicles");case 50->open(p,v.theme,v.page);case 53->open(p,v.theme,v.page+1);default->{}}
            }catch(IllegalArgumentException ex){p.sendMessage("§e"+ex.getMessage());}
        }});
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    public CompletionStage<Void> prepareClose(){return cache.prepareClose();}public void close(){cache.awaitClosed();}
    private static String name(CityChapters.Theme theme){return switch(theme){case REIGN->"Правление";case WAR->"Войны";case RECOVERY->"Восстановление";};}
    private static Material icon(CityChapters.Theme theme){return switch(theme){case REIGN->Material.PLAYER_HEAD;case WAR->Material.IRON_SWORD;case RECOVERY->Material.ANVIL;};}
    private static ItemStack item(Material type,String name,List<String> lore){var stack=new ItemStack(type);var meta=stack.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(name)));meta.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));meta.addItemFlags(ItemFlag.values());stack.setItemMeta(meta);return stack;}
    private static final class View implements InventoryHolder{final UUID owner,town;final CityChapters.Theme theme;final int page;final Map<Integer,CityChapters.Chapter> chapters=new HashMap<>();Inventory inventory;CityChapters.Chapter chapter;int factPage;View(UUID owner,UUID town,CityChapters.Theme theme,int page){this.owner=owner;this.town=town;this.theme=theme;this.page=page;}public Inventory getInventory(){return inventory;}}
}
