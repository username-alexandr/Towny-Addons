package ru.neverland.townycontrol;

import com.palmergames.bukkit.towny.TownyAPI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.IntConsumer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import ru.neverland.core.*;

/** Server-owned buttons, one-use confirmations and permission checks on every deferred click. */
final class AdminMenu implements Listener {
    @FunctionalInterface private interface Action {void run()throws Exception;}
    private static final int PAGE_SIZE=36;
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneOffset.UTC);
    private final NeverLandTownyControl plugin;
    private final AdminRouter router;
    private final ActivityBridge activities;
    private final AuditCommand audit;
    private final Map<UUID,Screen> screens=new HashMap<>();
    private static final class Screen implements InventoryHolder {
        final UUID owner;final Map<Integer,Action> buttons=new HashMap<>();final Set<String> permissions;
        Inventory inventory;boolean busy;long expires=Long.MAX_VALUE;
        Screen(Player player,Set<String> permissions){owner=player.getUniqueId();this.permissions=permissions;}
        @Override public Inventory getInventory(){return inventory;}
    }
    AdminMenu(NeverLandTownyControl plugin,AdminRouter router,AuditCommand audit){this.plugin=plugin;this.router=router;this.activities=new ActivityBridge(router);this.audit=audit;Bukkit.getPluginManager().registerEvents(this,plugin);}
    private Screen screen(Player player,String title,String... permissions){
        if(!AdminAccess.admin(player))throw new IllegalArgumentException("Нет права "+AdminAccess.ADMIN);
        for(String p:permissions)if(!AdminAccess.has(player,p))throw new IllegalArgumentException("Нет права "+p);
        var s=new Screen(player,Set.of(permissions));s.inventory=MenuStyle.inventory(plugin,s,54,title);
        for(int slot=36;slot<54;slot++)s.inventory.setItem(slot,item(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));
        button(s,49,Material.BARRIER,"Закрыть",List.of("Закрыть административное меню"),player::closeInventory);return s;
    }
    private void show(Player player,Screen s){player.openInventory(s.inventory);if(player.getOpenInventory().getTopInventory()==s.inventory)screens.put(player.getUniqueId(),s);}
    private boolean current(Player player,Screen s){return plugin.isEnabled()&&player.isOnline()&&s.owner.equals(player.getUniqueId())&&screens.get(s.owner)==s&&player.getOpenInventory().getTopInventory()==s.inventory;}
    private boolean authorized(Player player,Screen s){return AdminAccess.admin(player)&&s.permissions.stream().allMatch(p->AdminAccess.has(player,p));}
    private static ItemStack item(Material material,String title,List<String> lines){var item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));meta.lore(MenuStyle.loreComponents(lines.stream().map(MenuStyle::decode).toList()));item.setItemMeta(meta);return item;}
    private void button(Screen s,int slot,Material icon,String title,List<String> lore,Action action){s.inventory.setItem(slot,item(icon,title,lore));if(action!=null)s.buttons.put(slot,action);}
    private void back(Screen s,Action action){button(s,45,Material.ARROW,"Назад",List.of("Вернуться в предыдущий раздел"),action);}
    private void pagination(Screen s,int page,long count,IntConsumer go){
        if(page>1)button(s,47,Material.ARROW,"Предыдущая страница",List.of("Страница "+(page-1)),()->go.accept(page-1));
        button(s,48,Material.PAPER,"Страница "+page,List.of("Найдено: "+count,"По 36 записей на странице"),null);
        if((long)page*PAGE_SIZE<count&&page<100)button(s,51,Material.ARROW,"Следующая страница",List.of("Страница "+(page+1)),()->go.accept(page+1));
        if(page==100&&(long)page*PAGE_SIZE<count)button(s,51,Material.PAPER,"Уточните фильтры",List.of("Доступны первые 100 страниц","Сузьте выборку или используйте команды"),null);
    }
    void home(Player player){
        var s=screen(player,"Администрация • NeverLand");
        button(s,4,Material.NETHER_STAR,"Управление сервером",List.of("Выберите раздел","Доступ: OP или "+AdminAccess.ADMIN),null);
        button(s,11,Material.BOOK,"Журналы операций",List.of("Деньги, торговля, ресурсы и действия админов","Категории, фильтры, подробности и CSV"),()->logs(player));
        button(s,13,Material.REDSTONE,"Отмена без провала",List.of("Ивенты и задачи с безопасной отменой","Проверка цели перед подтверждением"),()->activityModules(player,"cancel"));
        button(s,15,Material.CLOCK,"Перезапуск задач",List.of("Новый таймер активной задачи","Прогресс и квитанции сохраняются"),()->activityModules(player,"restart"));
        button(s,29,Material.REPEATER,"Пауза и время",List.of("Приостановить, продолжить или продлить"),()->time(player));
        button(s,31,Material.LEVER,"Управление модулями",List.of("Состояние и безопасное отключение","Предпросмотр зависимых аддонов"),()->modules(player,1,false));
        button(s,33,Material.COMPASS,"Справочник аддонов",List.of("Игровых аддонов: "+router.entries().size(),"Штатные команды и доступные задачи"),()->modules(player,1,true));show(player,s);
    }
    private void time(Player player){var s=screen(player,"Задачи • Пауза и время");int slot=10;for(String action:List.of("pause","resume","extend","status")){String a=action;button(s,slot++,actionIcon(a),actionName(a),List.of("Выбрать аддон и активную задачу"),()->activityModules(player,a));}back(s,()->home(player));show(player,s);}
    private void activityModules(Player player,String action)throws Exception {
        var s=screen(player,actionName(action)+" • Аддоны");int slot=0;
        for(var entry:router.entries())if(entry.activities()) {
            int targetSlot=slot++;
            if(!AdminAccess.has(player,entry.permission())){button(s,targetSlot,Material.GRAY_DYE,entry.shortName(),List.of("Нет права: "+entry.permission()),null);continue;}
            try{long count=activities.targets(player,entry).stream().filter(t->t.actions().contains(action)).count();button(s,targetSlot,actionIcon(action),entry.shortName(),List.of("Действие: "+actionName(action),"Подходящих задач: "+count),()->tasks(player,entry,action,1));}
            catch(Exception ex){button(s,targetSlot,Material.GRAY_DYE,entry.shortName(),List.of("Недоступен",clean(ex.getMessage())),null);}
        }
        back(s,()->home(player));button(s,53,Material.SPYGLASS,"Обновить",List.of("Проверить доступность задач"),()->activityModules(player,action));show(player,s);
    }
    private void tasks(Player player,AdminRouter.Entry entry,String action,int page)throws Exception {
        var all=activities.targets(player,entry).stream().filter(t->t.actions().contains(action)).toList();var s=screen(player,entry.shortName()+" • "+actionName(action),entry.permission());int slot=0;
        for(var task:all.stream().skip((page-1L)*PAGE_SIZE).limit(PAGE_SIZE).toList())button(s,slot++,actionIcon(action),clean(task.name()),List.of("ID: "+task.id(),"Нажмите для подробностей и выбора действия"),()->task(player,task,action,page));
        if(all.isEmpty())button(s,22,Material.GLASS_BOTTLE,"Подходящих задач нет",List.of("Задачи завершены или не поддерживают это действие"),null);
        pagination(s,page,all.size(),p->attempt(player,()->tasks(player,entry,action,p)));back(s,()->activityModules(player,action));button(s,53,Material.SPYGLASS,"Обновить",List.of("Прочитать текущее состояние"),()->tasks(player,entry,action,page));show(player,s);
    }
    private void task(Player player,ActivityBridge.Task selected,String category,int page)throws Exception {
        var fresh=activities.targets(player,selected.entry()).stream().filter(t->t.id().equals(selected.id())).findFirst().orElseThrow(()->new IllegalArgumentException("Задача завершена. Обновите список"));
        var s=screen(player,fresh.entry().shortName()+" • Задача",fresh.entry().permission());
        String status=fresh.actions().contains("status")?activities.execute(player,fresh,"status",0):fresh.name();
        button(s,4,Material.WRITABLE_BOOK,clean(fresh.name()),List.of("ID: "+fresh.id(),clean(status)),null);
        int slot=19;for(String a:List.of("pause","resume","extend","restart","cancel"))if(fresh.actions().contains(a))button(s,slot++,actionIcon(a),actionName(a),actionLore(a,fresh.entry().shortName()),()->{
            if(a.equals("extend"))extension(player,fresh,category,page);else confirmActivity(player,fresh,a,0,category,page);
        });
        back(s,()->tasks(player,fresh.entry(),category,page));button(s,53,Material.SPYGLASS,"Обновить",List.of("Прочитать текущее состояние"),()->task(player,fresh,category,page));show(player,s);
    }
    private void extension(Player player,ActivityBridge.Task task,String category,int page){var s=screen(player,"Продление • Выбор времени",task.entry().permission());int slot=10;for(long minutes:new long[]{5,15,30,60,180,1440})button(s,slot++,Material.CLOCK,"+"+minutes+" мин.",List.of(clean(task.name()),"Далее — подтверждение"),()->confirmActivity(player,task,"extend",minutes,category,page));back(s,()->task(player,task,category,page));show(player,s);}
    private void confirmActivity(Player player,ActivityBridge.Task task,String action,long minutes,String category,int page){
        var lines=new ArrayList<>(actionLore(action,task.entry().shortName()));lines.add("Аддон: "+task.entry().shortName());lines.add(clean(task.name()));lines.add("ID: "+task.id());if(minutes>0)lines.add("Продлить на "+minutes+" мин.");
        confirm(player,actionName(action),lines,task.entry().permission(),()->task(player,task,category,page),()->{
            String result=activities.execute(player,task,action,minutes);
            player.sendMessage("§a"+result);player.closeInventory();
        });
    }
    private void confirm(Player player,String title,List<String> lines,String permission,Action back,Action execute){
        var s=screen(player,"Подтвердить • "+title,permission);s.expires=System.currentTimeMillis()+60000;
        button(s,4,Material.WRITABLE_BOOK,title,lines,null);button(s,20,Material.LIME_CONCRETE,"Подтвердить",List.of("Выполнить указанное действие один раз","Подтверждение действует 60 секунд"),()->{player.closeInventory();execute.run();});
        button(s,24,Material.RED_CONCRETE,"Вернуться без изменений",List.of("Ничего не выполнять"),back);back(s,back);show(player,s);
    }
    private void modules(Player player,int page,boolean reference){
        var s=screen(player,reference?"Справочник • Аддоны":"Модули • Состояние");var entries=new ArrayList<>(router.entries());int slot=0;
        for(var entry:entries.stream().skip((page-1L)*PAGE_SIZE).limit(PAGE_SIZE).toList())button(s,slot++,Material.COMPARATOR,entry.shortName(),List.of(plugin.moduleStatus(entry.module()),reference?"Команды и доступные действия":"Нажмите для управления"),()->module(player,entry,reference));
        pagination(s,page,entries.size(),p->modules(player,p,reference));back(s,()->home(player));button(s,53,Material.SPYGLASS,"Обновить",List.of("Прочитать текущее состояние модулей"),()->modules(player,page,reference));show(player,s);
    }
    private void module(Player player,AdminRouter.Entry entry,boolean reference){
        var s=screen(player,entry.shortName()+" • Управление");button(s,4,Material.COMPARATOR,entry.shortName(),List.of(plugin.moduleStatus(entry.module()),"Право аддона: "+entry.permission()),null);
        button(s,19,Material.REDSTONE_TORCH,"Отключить модуль",List.of("Показать затронутые зависимости","Прогресс и таймеры сохраняются"),()->{
            var plan=plugin.modulePlan(entry.module());var lines=new ArrayList<String>();lines.add("Будут остановлены: "+String.join(", ",plan));lines.add("Прогресс сохраняется; за простой нет провалов");
            confirm(player,"Отключение "+entry.shortName(),lines,AdminAccess.ADMIN,()->module(player,entry,reference),()->{
                if(!plan.equals(plugin.modulePlan(entry.module())))throw new IllegalArgumentException("План зависимостей изменился; откройте подтверждение заново");
                player.closeInventory();plugin.onCommand(player,plugin.getCommand("nltmodules"),"nltmodules",new String[]{"disable",entry.module()});
            });
        });
        button(s,21,Material.LIME_DYE,"Разрешить запуск",List.of("Модуль запустится после полного рестарта сервера","Зависимости также должны быть разрешены"),()->confirm(player,"Запуск "+entry.shortName(),List.of("Изменить разрешение на запуск","Потребуется полный перезапуск сервера"),AdminAccess.ADMIN,()->module(player,entry,reference),()->{player.closeInventory();plugin.onCommand(player,plugin.getCommand("nltmodules"),"nltmodules",new String[]{"enable",entry.module()});}));
        button(s,23,Material.BOOK,"Команды аддона",List.of("Показать справочник в чате","/nltadmin "+entry.shortName()+" native …"),()->{router.describe(player,entry);player.closeInventory();});
        if(entry.activities())button(s,25,Material.CLOCK,"Активные задачи",List.of("Статус и допустимые действия"),()->tasks(player,entry,"status",1));
        back(s,()->modules(player,1,reference));show(player,s);
    }
    void logs(Player player){
        var s=screen(player,"Журналы • Категории",AdminAccess.AUDIT);int slot=10;
        for(var category:AuditCategory.values()){if(slot%9==8)slot+=2;button(s,slot++,Material.valueOf(category.icon),category.title,List.of("Нажмите для просмотра","Даты и время в UTC"),()->logPage(player,category,new AuditQuery(Map.of("category",category.name()),1)));}
        button(s,40,Material.KNOWLEDGE_BOOK,"Как читать журнал",List.of("Сделка показывает товар и его цену","Списания, переводы и сделки — разные записи","Их суммы нельзя складывать","OBSERVED — команда, не доказательство оплаты"),null);
        button(s,53,Material.SPYGLASS,"Проверить журналы",List.of("Проверка целостности всех файлов","Результат будет отправлен в чат"),()->audit.onCommand(player,plugin.getCommand("nltaudit"),"nltaudit",new String[]{"status"}));back(s,()->home(player));show(player,s);
    }
    private void logPage(Player player,AuditCategory category,AuditQuery query){
        var loading=screen(player,category.title+" • Чтение",AdminAccess.AUDIT);button(loading,22,Material.CLOCK,"Читаю журнал…",List.of("Можно вернуться назад или закрыть меню"),null);back(loading,()->logs(player));show(player,loading);
        try{audit.page(query,PAGE_SIZE,(result,error)->{
            if(!current(player,loading))return;
            if(!authorized(player,loading)){player.closeInventory();return;}
            if(error!=null){button(loading,22,Material.BARRIER,"Журнал не прочитан полностью",List.of("Проверьте консоль сервера","Неполный результат не отображается"),null);button(loading,53,Material.SPYGLASS,"Повторить",List.of("Повторить чтение"),()->logPage(player,category,query));return;}
            var s=screen(player,category.title+" • "+query.page(),AdminAccess.AUDIT);int slot=0;
            for(var record:result.rows())button(s,slot++,Material.valueOf(category.icon),AuditCategory.kind(record.kind()),summary(record),()->record(player,record,category,query));
            if(result.rows().isEmpty())button(s,22,Material.GLASS_BOTTLE,"Записей не найдено",List.of("Измените фильтры или обновите страницу","Журнал ведётся с установки аудита"),null);
            pagination(s,query.page(),result.matched(),p->logPage(player,category,new AuditQuery(query.filters(),p)));back(s,()->logs(player));
            button(s,46,Material.HOPPER,"Фильтры",filterLore(query),()->filters(player,category,query));
            if(AdminAccess.has(player,AdminAccess.EXPORT))button(s,50,Material.WRITABLE_BOOK,"Экспорт CSV",List.of("Текущая категория и все выбранные фильтры","Все страницы, до 100000 записей","Путь к файлу появится в чате"),()->{
                if(!AdminAccess.has(player,AdminAccess.EXPORT))throw new IllegalArgumentException("Право экспорта отозвано");var args=new ArrayList<String>();args.add("export");query.filters().forEach((k,v)->args.add(k+"="+v));audit.onCommand(player,plugin.getCommand("nltaudit"),"nltaudit",args.toArray(String[]::new));
            });
            button(s,53,Material.SPYGLASS,"Обновить",List.of("Прочитать журнал заново"),()->logPage(player,category,query));show(player,s);
        });}catch(Exception ex){button(loading,22,Material.BARRIER,"Чтение недоступно",List.of(clean(ex.getMessage())),null);button(loading,53,Material.SPYGLASS,"Повторить",List.of("Повторить чтение"),()->logPage(player,category,query));}
    }
    private void filters(Player player,AuditCategory category,AuditQuery query){
        var s=screen(player,"Журналы • Фильтры",AdminAccess.AUDIT);button(s,4,Material.HOPPER,"Выбранные фильтры",filterLore(query),null);
        button(s,10,Material.COMPASS,"Между городами",List.of(query.filters().containsKey("intercity")?"Включён • нажмите, чтобы снять":"Показать только передачу между городами"),()->logPage(player,category,with(query,"intercity",query.filters().containsKey("intercity")?null:"true")));
        button(s,12,Material.OAK_SIGN,"Выбрать город",List.of("Отправитель, получатель или город инициатора"),()->towns(player,category,query,1));
        button(s,14,Material.CLOCK,"Сегодня",List.of("С начала текущего дня UTC"),()->logPage(player,category,with(query,"since",LocalDate.now(ZoneOffset.UTC).toString())));
        button(s,16,Material.CLOCK,"Последние 7 дней",List.of("Сегодня и шесть предыдущих дней UTC"),()->logPage(player,category,with(query,"since",LocalDate.now(ZoneOffset.UTC).minusDays(6).toString())));
        button(s,28,Material.LIME_DYE,"Результат операции",List.of("Выполнено, доставлено, отменено и другие статусы","Выбрать точный статус записи"),()->outcomes(player,category,query));
        button(s,30,Material.MILK_BUCKET,"Сбросить фильтры",List.of("Сохранить выбранную категорию"),()->logPage(player,category,new AuditQuery(Map.of("category",category.name()),1)));
        button(s,32,Material.PAPER,"Точный поиск",List.of("/nltaudit search town=… player=… id=…","Также доступны даты, тип, модуль и текст","ID удалённого города можно указать вручную"),()->{player.sendMessage("§b/nltaudit search category="+category.name()+" [town=UUID] [player=UUID] [id=UUID] [since=YYYY-MM-DD] [until=YYYY-MM-DD]");player.closeInventory();});back(s,()->logPage(player,category,query));show(player,s);
    }
    private void towns(Player player,AuditCategory category,AuditQuery query,int page){
        var all=TownyAPI.getInstance().getTowns().stream().sorted(Comparator.comparing(t->t.getName().toLowerCase(Locale.ROOT))).toList();var s=screen(player,"Журналы • Выбор города",AdminAccess.AUDIT);int slot=0;
        for(var town:all.stream().skip((page-1L)*PAGE_SIZE).limit(PAGE_SIZE).toList()){String id=town.getUUID().toString();button(s,slot++,Material.OAK_SIGN,clean(town.getName()),List.of("UUID: "+id),()->logPage(player,category,with(query,"town",id)));}pagination(s,page,all.size(),p->towns(player,category,query,p));back(s,()->filters(player,category,query));show(player,s);
    }
    private void outcomes(Player player,AuditCategory category,AuditQuery query){
        var s=screen(player,"Журналы • Результат",AdminAccess.AUDIT);int slot=0;
        for(String value:List.of("COMPLETED","COMPLETE","DONE","DELIVERED","CREDIT","DEBIT","OBSERVED","CANCELLED","RETURNED","REFUNDED","REJECTED","UNCONFIRMED","PREPARED","PAID"))button(s,slot++,Material.PAPER,AuditCategory.outcome(value),List.of("Точный статус: "+value,"Финальный результат зависит от типа операции"),()->logPage(player,category,with(query,"outcome",value)));
        button(s,40,Material.MILK_BUCKET,"Все результаты",List.of("Снять фильтр результата"),()->logPage(player,category,with(query,"outcome",null)));back(s,()->filters(player,category,query));show(player,s);
    }
    private void record(Player player,AuditRecord record,AuditCategory category,AuditQuery query){
        var s=screen(player,"Журнал • Подробности",AdminAccess.AUDIT);button(s,4,Material.BOOK,AuditCategory.kind(record.kind()),summary(record),null);
        party(s,19,"Отправитель",record.from());party(s,21,"Получатель",record.to());party(s,23,"Инициатор",record.actor());
        button(s,25,Material.PAPER,"Квитанция операции",List.of("Запись: "+record.id(),"Операция: "+record.operation(),"Аддон: "+record.module(),"Тип: "+record.kind(),"Результат: "+record.outcome(),clean(record.details())),null);
        button(s,40,Material.WRITABLE_BOOK,"ID в чат",List.of("Вывести ID записи и операции для копирования"),()->player.sendMessage("§7id="+record.id()+" operation="+record.operation()));back(s,()->logPage(player,category,query));show(player,s);
    }
    private void party(Screen screen,int slot,String label,AuditRecord.Party party){button(screen,slot,Material.OAK_SIGN,label,List.of(clean(party.name()),"Тип: "+party.type(),"ID: "+party.id(),"Город: "+party.town()),null);}
    private static List<String> summary(AuditRecord r){return List.of(TIME.format(Instant.ofEpochMilli(r.at()))+" UTC","От: "+clean(r.from().name()),"Кому: "+clean(r.to().name()),"Инициатор: "+clean(r.actor().name()),"Предмет: "+(r.asset().isEmpty()?"—":r.quantity()+" × "+clean(r.asset().split(":",2)[0])),"Сумма / цена: "+(r.money().isEmpty()?"—":r.money()),"Результат: "+AuditCategory.outcome(r.outcome()));}
    private static AuditQuery with(AuditQuery q,String key,String value){var filters=new HashMap<>(q.filters());if(value==null)filters.remove(key);else filters.put(key,value);return new AuditQuery(filters,1);}
    private static List<String> filterLore(AuditQuery query){var result=new ArrayList<String>();result.add("Категория: "+AuditCategory.valueOf(query.filters().get("category")).title);query.filters().entrySet().stream().filter(e->!e.getKey().equals("category")).sorted(Map.Entry.comparingByKey()).forEach(e->result.add(switch(e.getKey()){case "town"->"Город";case "intercity"->"Между городами";case "since"->"С даты UTC";case "outcome"->"Результат";default->e.getKey();}+": "+e.getValue()));if(result.size()==1)result.add("Все города и даты");return result;}
    private static String clean(String text){if(text==null)return "";String clean=text.replaceAll("[\\p{Cntrl}§&]"," ");return clean.substring(0,Math.min(300,clean.length()));}
    private static String actionName(String action){return switch(action){case "cancel"->"Отмена без провала";case "restart"->"Перезапуск таймера";case "pause"->"Приостановить";case "resume"->"Продолжить";case "extend"->"Продлить";default->"Статус задач";};}
    private static Material actionIcon(String action){return switch(action){case "cancel"->Material.REDSTONE;case "restart","extend"->Material.CLOCK;case "pause"->Material.REPEATER;case "resume"->Material.LIME_DYE;default->Material.BOOK;};}
    private static List<String> actionLore(String action,String module){return switch(action){case "cancel"->List.of("Нейтральная отмена без начисления провала","Расчёты и возвраты выполняет аддон");case "restart"->List.of("Запустить таймер заново; сохранить прогресс",module.equals("Events")?"Событие также выйдет из паузы":"Текущее состояние паузы сохраняется");case "pause"->List.of("Приостановить задачу, сохранив прогресс");case "resume"->List.of("Продолжить приостановленную задачу");default->List.of("Увеличить оставшееся время");};}
    private void attempt(Player player,Action action){try{action.run();}catch(Exception ex){player.sendMessage("§cОперация остановлена: "+clean(ex.getMessage()));}}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Screen s))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!current(player,s))return;
        if(!authorized(player,s)){player.closeInventory();return;}
        if(event.getClick()!=ClickType.LEFT||event.getRawSlot()<0||event.getRawSlot()>=54||s.busy)return;
        var action=s.buttons.get(event.getRawSlot());if(action==null)return;s.busy=true;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!current(player,s))return;
            if(!authorized(player,s)){player.closeInventory();return;}
            if(System.currentTimeMillis()>s.expires){player.sendMessage("§eПодтверждение истекло. Откройте действие заново");player.closeInventory();return;}
            attempt(player,action);if(current(player,s))s.busy=false;
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Screen)event.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent event){if(event.getInventory().getHolder() instanceof Screen s)screens.remove(event.getPlayer().getUniqueId(),s);}
    @EventHandler public void quit(PlayerQuitEvent event){screens.remove(event.getPlayer().getUniqueId());}
    void shutdown(){for(var player:List.copyOf(Bukkit.getOnlinePlayers()))if(player.getOpenInventory().getTopInventory().getHolder() instanceof Screen)player.closeInventory();screens.clear();}
}
