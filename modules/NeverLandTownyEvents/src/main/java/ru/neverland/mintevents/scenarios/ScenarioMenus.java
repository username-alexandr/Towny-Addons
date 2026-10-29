package ru.neverland.mintevents.scenarios;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;
import static ru.neverland.mintevents.scenarios.ScenarioBook.*;

/** One plan screen, two or three readable choices, no extra contributions or hidden payment button. */
public final class ScenarioMenus implements Listener {
    private final JavaPlugin plugin;private final ScenarioService service;private final Consumer<Player> back;
    private static final class Screen implements InventoryHolder {
        final UUID player,town;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;boolean busy;
        Screen(UUID player,UUID town){this.player=player;this.town=town;}public Inventory getInventory(){return inventory;}
    }
    public ScenarioMenus(JavaPlugin plugin,ScenarioService service,Consumer<Player> back){this.plugin=plugin;this.service=service;this.back=back;Bukkit.getPluginManager().registerEvents(this,plugin);}
    private UUID ownTown(Player player){if(!player.hasPermission("mintevents.use"))throw new IllegalArgumentException("Нет доступа к событиям");var resident=TownyAPI.getInstance().getResident(player);var town=resident==null?null:resident.getTownOrNull();if(town==null)throw new IllegalArgumentException("Нужен свой город");return town.getUUID();}
    private boolean current(Player player,Screen screen){return plugin.isEnabled()&&player.isOnline()&&screen.player.equals(player.getUniqueId())&&player.getOpenInventory().getTopInventory()==screen.inventory;}
    private void access(Player player,Screen screen){if(!ownTown(player).equals(screen.town))throw new IllegalArgumentException("Город изменился; откройте меню заново");}
    public void open(Player player){try(var guard=SuiteState.guard()){
        var town=ownTown(player);var screen=new Screen(player.getUniqueId(),town);screen.inventory=MenuStyle.inventory(plugin,screen,27,"План действий при событии");
        for(int slot=18;slot<27;slot++)screen.inventory.setItem(slot,item(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));
        button(screen,18,Material.ARROW,"Назад",List.of("Вернуться к городскому событию"),()->back.accept(player));button(screen,26,Material.BARRIER,"Закрыть",List.of("Вернуться в игру"),player::closeInventory);
        var row=service.current(town).orElse(null);
        if(row==null){button(screen,13,Material.BOOK,"Дополнительного плана нет",List.of(service.problem().isBlank()?"Планы доступны для новых засух и наводнений.":service.problem(),"Обычное событие работает по прежним правилам."),null);player.openInventory(screen.inventory);return;}
        var summary=new ArrayList<String>();summary.add(stage(row));if(!row.choice().isEmpty()){summary.add("Выбрано: "+row.terms().plan(row.choice()).name());summary.add("Решение мэра: "+row.actorName());}
        if(row.phase()==Phase.WARNING)summary.add("До решения: "+Math.max(0,(row.terms().warningMillis()-row.worked()+999)/1000)+" сек.");
        summary.add("После "+row.terms().recoveryPercent()+"% помощи последствия ослабевают.");summary.add("Выбор не списывает деньги и предметы.");if(!service.problem().isBlank())summary.add(service.problem());
        button(screen,4,Material.BOOK,row.terms().title(),summary,null);
        int[] positions=row.terms().plans().size()==2?new int[]{11,15}:new int[]{10,13,16};int i=0;
        for(var plan:row.terms().plans()){
            var lines=new ArrayList<String>();lines.add(plan.description());lines.add("");lines.add("Во время помощи:");lines.addAll(consequences(plan.response()));lines.add("После "+row.terms().recoveryPercent()+"% помощи:");lines.addAll(consequences(plan.recovery()));
            boolean selected=row.choice().equals(plan.id()),allowed=service.canChoose(player,row);
            if(selected)lines.add("Этот план сохранён; изменить выбор нельзя.");else if(allowed)lines.add("Нажмите, чтобы выбрать этот план.");else lines.add(row.choice().isEmpty()?"Выбор доступен мэру на этапе решения.":"Сохранён другой план.");
            int slot=positions[i++];button(screen,slot,selected?Material.LIME_DYE:allowed?Material.PAPER:Material.GRAY_DYE,plan.name(),lines,allowed?()->choose(player,screen,row,plan.id()):null);
        }
        button(screen,22,Material.CLOCK,"Обновить",List.of("Показать сохранённый этап и решение"),()->open(player));player.openInventory(screen.inventory);
    }}
    private void choose(Player player,Screen screen,Scenario row,String plan){
        var result=service.choose(player,row.id(),plan);screen.busy=true;
        button(screen,22,Material.CLOCK,"Сохраняем решение…",List.of("Новые последствия включатся после сохранения."),null);
        result.whenComplete((saved,error)->{if(plugin.isEnabled())SuiteScheduler.player(plugin,player,p->{if(!current(p,screen))return;try{access(p,screen);if(error!=null){p.sendMessage("§eРешение не подтверждено. Прежние условия сохранены.");screen.busy=false;return;}p.sendMessage("§aПлан действий сохранён: "+saved.terms().plan(saved.choice()).name());open(p);}catch(RuntimeException denied){p.closeInventory();}});});
    }
    public static List<String> consequences(Mix mix){return List.of(relative("Помехи росту растений",mix.crops()),relative("Потери аграрного выпуска",mix.output()));}
    private static String relative(String title,double factor){if(factor==1)return title+": без изменения";long percent=Math.round(Math.abs(1-factor)*100);return title+": на "+percent+"% "+(factor<1?"меньше":"больше");}
    public static String stage(Scenario r){return switch(r.phase()){case WARNING->"Предупреждение: жители готовятся к помощи";case DECISION->"Решение: мэр выбирает план действий";case RESPONSE->"Помощь: действуют выбранные условия";case RECOVERY->"Восстановление: последствия ослаблены";case CANCELLED->"План закрыт без нового провала и штрафов";case COMPLETED->r.outcome().equals("SUCCESS")?"Событие завершилось успешно":"Событие завершилось: показан его обычный результат";};}
    private static ItemStack item(Material material,String title,List<String> lore){var item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));meta.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);item.setItemMeta(meta);return item;}
    private static void button(Screen s,int slot,Material material,String title,List<String> lore,Runnable action){s.inventory.setItem(slot,item(material,title,lore));if(action==null)s.actions.remove(slot);else s.actions.put(slot,action);}
    @EventHandler public void click(InventoryClickEvent event){try(var guard=SuiteState.guard()){
        if(!(event.getView().getTopInventory().getHolder() instanceof Screen screen))return;event.setCancelled(true);if(SuiteState.frozen(plugin.getName())||!(event.getWhoClicked() instanceof Player player)||!current(player,screen)||screen.busy||event.getClick()!=ClickType.LEFT)return;
        try{access(player,screen);var action=screen.actions.get(event.getRawSlot());if(action==null)return;screen.busy=true;SuiteScheduler.entity(plugin,player,()->{if(!current(player,screen))return;try{access(player,screen);screen.busy=false;action.run();}catch(RuntimeException error){screen.busy=false;player.sendMessage("§e"+error.getMessage());}});}catch(RuntimeException denied){player.closeInventory();}
    }}
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Screen)event.setCancelled(true);}
}
