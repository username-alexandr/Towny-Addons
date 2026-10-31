package cc.crafto.ui.server;
import cc.crafto.ui.*;
import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static cc.crafto.ui.Protocol.*;
import static cc.crafto.ui.server.PublicMethods.*;
import static cc.crafto.ui.server.CivicView.*;
/** Entity-owned navigation and single-use actions; disk and region scans run off the player task. */
final class CivicWorkbench {
    record Route(String name,String id,int page){}
    private record Planning(DevelopmentView plan,DevelopmentEffects effects){}
    private record Loaded(CityBuildReader.Result construction,Planning planning,CityProgression.Snapshot tree){}
    record Review(Action action,UUID town,UUID world,int x,int z,String fingerprint,String title,List<String> lines,String targetKey){
        Review(Action action,UUID town,UUID world,int x,int z,String fingerprint,String title,List<String> lines){this(action,town,world,x,z,fingerprint,title,lines,"");}
    }
    static final class State {
        String token="",route="city",id="",selectedWorld="",type="ALL",rarity="ALL",sort="rarity";int page,size=6,request,epoch;long minimum,pollAt;boolean readyOnly;
        UUID planTown;final Map<String,Integer> plan=new LinkedHashMap<>();boolean procurementBusy;
        final Map<String,Action> actions=new HashMap<>();final Deque<Route> history=new ArrayDeque<>();final CommandTicket<Review> ticket=new CommandTicket<>();
        String companyRole="",citySection="active";boolean cityDashboard,nativeOrders;Set<String> invitations=Set.of();
        void clear(){epoch++;actions.clear();ticket.clear();token="";history.clear();nativeOrders=false;}
        void open(String destination,boolean dashboard){open(destination,dashboard,false);}
        void open(String destination,boolean dashboard,boolean fromNativeOrders){
            clear();route=allowedRoute(destination);id="";selectedWorld="";page=0;citySection="active";cityDashboard=dashboard;
            nativeOrders=route.equals("mine")&&fromNativeOrders;
        }
        /** Recovery only changes server-owned navigation. It never accepts an action or target from the client. */
        boolean recover(String op){
            if(op.equals("close")){clear();return true;}
            if(!op.equals("refresh")&&!op.equals("back"))return false;
            epoch++;actions.clear();ticket.clear();token="";
            if(op.equals("back")){
                if(history.isEmpty()){route="city";id="";page=0;}
                else {var previous=history.pop();route=previous.name();id=previous.id();page=previous.page();}
            }
            return true;
        }
        void requireToken(String presented){require(!token.isEmpty()&&token.equals(presented),"Экран устарел. Нажмите «Обновить».");}
    }
    private final CraftoUIPlugin plugin;private final TownyCivic towny;private final CivicData data=new CivicData();
    private final CityBuildReader builds;private final CivicGoals goals;private final TownyConfirmBridge confirmations;
    CivicWorkbench(CraftoUIPlugin p){plugin=p;towny=new TownyCivic(p);confirmations=new TownyConfirmBridge(p,towny);builds=new CityBuildReader(p,new AddonFiles());goals=new CivicGoals(p.getDataFolder().toPath().resolve("civic-state.json"));}
    void start(){
        confirmations.start();
        plugin.getServer().getPluginManager().registerEvents(goals,plugin);
        async(()->{try{goals.load();refreshPlans();}catch(Exception e){plugin.getLogger().warning("Civic goals unavailable: "+e.getMessage());}});
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,t->{try{goals.flush();refreshPlans();}catch(Exception e){plugin.getLogger().warning("Civic goals save: "+e.getMessage());}},5,10,TimeUnit.SECONDS);
    }
    void departed(UUID player){confirmations.departed(player);}
    TownyConfirmBridge confirmations(){return confirmations;}
    void stop(){try{goals.flush();}catch(Exception e){plugin.getLogger().severe("Civic goals were not saved: "+e.getMessage());}}
    private void refreshPlans(){for(UUID town:goals.towns())builds.read(towny.addon("NeverLandTownyBuilds"),town).thenAccept(b->goals.plan(town,b));}
    private void async(Runnable r){plugin.getServer().getAsyncScheduler().runNow(plugin,t->r.run());}
    private void own(Player p,Runnable r){if(plugin.isEnabled())p.getScheduler().run(plugin,t->{if(p.isOnline()&&plugin.isEnabled())r.run();},null);}
    private static boolean has(Player p,String permission){return p.hasPermission(permission);}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalArgumentException(reason);}
    private static String error(Throwable t){while(t.getCause()!=null)t=t.getCause();return t instanceof IllegalArgumentException?t.getMessage():"Данные сейчас недоступны. Обновите экран; подробности в журнале сервера.";}
    void handle(Player p,State s,JsonObject b,Consumer<JsonObject> send){
        int request=integer(b,"request",0);String op=str(b,"op","open");
        try{
            // Closing must also work after permission loss or a configuration reload.
            if(op.equals("close")){s.recover(op);return;}
            require(has(p,"craftoui.use")&&has(p,"craftoui.city"),"Нет доступа к городскому интерфейсу");
            if(op.equals("return-native-orders")){
                require(s.nativeOrders&&s.history.isEmpty()&&s.route.equals("mine")&&plugin.civicOrdersAvailable(p),"Предыдущее меню заказов недоступно");
                s.clear();dispatch(p,"NeverLandTownyContracts","orders","");return;
            }
            if(op.equals("poll")){if(System.nanoTime()-s.pollAt<8_000_000_000L)return;s.pollAt=System.nanoTime();load(p,s,send,true);return;}
            s.request=request;
            if(!op.equals("open")&&!TownMembership.hasTown(plugin,p)&&cc.crafto.ui.TownUi.requiresTown(s.route)){s.clear();s.route="inbox";s.id="";s.page=0;load(p,s,send,false);return;}
            s.size=Math.clamp(integer(b,"size",s.size),1,12);
            if(op.equals("open")){s.open(str(b,"route","city"),bool(b,"cityDashboard",false),str(b,"source","").equals("native-orders")&&plugin.civicOrdersAvailable(p));}
            else if(!s.recover(op)){
                s.requireToken(str(b,"token",""));
                switch(op){
                    case "page" -> s.page=Math.clamp(integer(b,"page",0),0,CityHistory.MAX_ENTRIES);
                    case "section" -> {require(s.cityDashboard&&s.route.equals("city"),"Раздел недоступен на этом экране");s.citySection=choice(str(b,"section","active"),List.of("active","builds","contracts"));s.page=0;}
                    case "filter" -> {s.type=choice(str(b,"type","ALL"),List.of("ALL","DELIVERY","MINING","HUNTING","FISHING"));s.rarity=choice(str(b,"rarity","ALL"),List.of("ALL","COMMON","UNCOMMON","RARE","EPIC","LEGENDARY"));s.sort=choice(str(b,"sort","rarity"),List.of("rarity","reward","ready","title"));s.minimum=Math.clamp(b.has("minimum")?b.get("minimum").getAsLong():0,0,100_000_000_000L);s.readyOnly=bool(b,"ready",false);s.page=0;}
                    case "act" -> {Action action=s.actions.get(str(b,"action",""));require(action!=null,"Действие устарело");act(p,s,action,send);return;}
                    case "confirm" -> {Review review=s.ticket.consume(str(b,"confirmation",""),System.nanoTime());require(review!=null,"Подтверждение истекло или уже использовано");execute(p,s,review,send);return;}
                    default -> throw new IllegalArgumentException("Неизвестное действие интерфейса");
                }
            }
            load(p,s,send,false);
        }catch(Exception e){send.accept(object("op","error","request",request,"message",error(e)));}
    }
    private static String choice(String value,List<String> values){require(values.contains(value),"Неверный фильтр");return value;}
    private static String allowedRoute(String r){return choice(r,List.of("city","materials","inbox","map","goals","orders","mine","treasury","history","tree","planner","planner-catalog","planner-supply","planner-effects"));}
    private void navigate(State s,String route,String id){if(s.history.size()>=24)s.history.removeLast();s.history.push(new Route(s.route,s.id,s.page));s.route=route;s.id=id;s.page=0;}
    private void load(Player p,State s,Consumer<JsonObject> send,boolean poll)throws Exception{
        if(!poll)s.ticket.clear();
        TownyCivic.Context c=towny.capture(p);
        if(!Objects.equals(s.planTown,c.town())){s.plan.clear();s.planTown=c.town();}
        if(c.town()==null&&cc.crafto.ui.TownUi.requiresTown(s.route)){s.clear();s.route="inbox";s.id="";s.page=0;}
        if(!poll&&s.route.startsWith("procurement-")){loadProcurement(p,s,c,send);return;}
        int epoch=poll?s.epoch:++s.epoch,request=s.request;
        boolean needPlan=!poll&&(s.route.startsWith("planner")||s.route.equals("tree"));Map<String,Integer> requestedPlan=Set.of("planner-catalog","planner-project","tree").contains(s.route)?Map.of():Map.copyOf(s.plan);
        boolean needEffects=needPlan&&s.route.equals("planner-effects"),canUpkeep=has(p,"neverlandtownyupkeep.use"),canPower=has(p,"neverlandtownypower.use");
        boolean history=!poll&&Set.of("history","history-entry").contains(s.route);
        boolean canHistory=has(p,"townychronicles.use");
        boolean needBuilds=!poll&&Set.of("city","build","materials","goals","tree").contains(s.route);
        boolean contracts=has(p,"mintcontracts.use"),companies=has(p,"neverlandtownycompanies.use"),treasury=has(p,"neverlandtownytreasury.use")||has(p,"neverlandtownytreasury.admin"),canBuild=has(p,"neverlandtownybuilds.use");
        boolean needFinance=!poll&&treasury&&c.town()!=null&&Set.of("city","treasury").contains(s.route);
        Map<String,Plugin> addons=new HashMap<>();for(String name:List.of("NeverLandTownyContracts","NeverLandTownyCompanies","NeverLandTownyTreasuryPlus","NeverLandTownyBuilds","NeverLandTownyResearch","NeverLandTownyUpkeep","NeverLandTownyPower",CityHistory.PLUGIN)){Plugin addon=towny.addon(name);if(addon!=null)addons.put(name,addon);}
        async(()->{
            var journal=history&&canHistory?CityHistory.read(addons.get(CityHistory.PLUGIN),c.town()):CityHistory.Result.unavailable("Нет доступа к истории города.");
            var snapshot=data.read(c,addons,contracts,companies,treasury);String balance=poll?"":towny.balance(c);
            var finance=needFinance?CityFinanceReader.read(addons.get("NeverLandTownyTreasuryPlus"),c.town(),System.currentTimeMillis()):CityFinance.unavailable("Нужно право просмотра казны");
            var future=needBuilds&&c.town()!=null&&canBuild?builds.read(addons.get("NeverLandTownyBuilds"),c.town()):CompletableFuture.completedFuture(CityBuildReader.unavailable("Постройки недоступны"));
            var planning=needPlan&&canBuild?DevelopmentView.read(addons.get("NeverLandTownyBuilds"),c.town(),requestedPlan):CompletableFuture.completedFuture(DevelopmentView.unavailable("Нет доступа к планированию"));
            var tree=new CompletableFuture<CityProgression.Snapshot>();
            if(s.route.equals("tree"))planning.whenComplete((plan,error)->async(()->tree.complete(error==null&&plan.available()?CityProgression.read(addons.get("NeverLandTownyBuilds"),addons.get("NeverLandTownyResearch"),c.town(),plan.catalog()):CityProgression.Snapshot.unavailable("Не удалось получить каталог построек"))));
            else tree.complete(CityProgression.Snapshot.unavailable("Раздел не открыт"));
            var withEffects=planning.thenApply(plan->new Planning(plan,needEffects?DevelopmentEffects.read(canUpkeep?addons.get("NeverLandTownyUpkeep"):null,canPower?addons.get("NeverLandTownyPower"):null,c.town(),plan):DevelopmentEffects.unavailable("Выберите раздел последствий")));
            future.thenCombine(withEffects,(b,plan)->new Loaded(b,plan,null)).thenCombine(tree,(loaded,graph)->new Loaded(loaded.construction(),loaded.planning(),graph)).whenComplete((loaded,failure)->own(p,()->{
                try{
                    if(!poll&&epoch!=s.epoch)return;TownyCivic.Context fresh=towny.capture(p);
                    require(Objects.equals(c.town(),fresh.town()),"Город изменился. Обновите экран.");
                    require(has(p,"craftoui.use")&&has(p,"craftoui.city"),"Нет доступа к интерфейсу");
                    require(!needEffects||(canUpkeep==has(p,"neverlandtownyupkeep.use")&&canPower==has(p,"neverlandtownypower.use")),"Права изменились. Обновите экран.");
                    require(canHistory==has(p,"townychronicles.use")&&contracts==has(p,"mintcontracts.use")&&companies==has(p,"neverlandtownycompanies.use")&&canBuild==has(p,"neverlandtownybuilds.use")&&treasury==(has(p,"neverlandtownytreasury.use")||has(p,"neverlandtownytreasury.admin")),"Права изменились. Обновите экран.");
                    if(poll){notifications(p,s,fresh,snapshot,send);return;}
                    if(failure!=null)throw new IllegalArgumentException("Не удалось обновить стройки");
                    var construction=loaded.construction();
                    if(needBuilds&&fresh.town()!=null)goals.plan(fresh.town(),construction);
                    CivicView view=view(p,s,fresh,snapshot,construction,balance,journal,finance,loaded.planning().plan());if(s.route.equals("tree"))loaded.tree().populate(view,construction.levels());if(needEffects)loaded.planning().effects().populate(view);s.actions.clear();s.token=UUID.randomUUID().toString();
                    if(view.map!=null)for(var cell:view.map.getAsJsonArray("cells")){var a=cell.getAsJsonObject();String key=str(a,"action","");if(!key.isEmpty())s.actions.put(key,new Action("plot",str(a,"id",""),fresh.world().toString()));}
                    var response=view.json(s.token,s.actions,s.page,view.map==null?s.size:5);response.addProperty("request",request);response.addProperty("townMember",fresh.town()!=null);response.addProperty("canBack",!s.history.isEmpty());send.accept(response);
                }catch(Exception e){if(!poll)send.accept(object("op","error","request",request,"message",error(e)));}
            }));
        });
    }
    private void notifications(Player p,State s,TownyCivic.Context c,CivicData.Snapshot d,Consumer<JsonObject> send)throws Exception{
        var ids=new HashSet<String>();if(has(p,"towny.command.invite.accept"))for(var i:towny.invitations(p))ids.add("town:"+i.id());for(var i:d.invites())ids.add("company:"+i.id());
        var lines=new ArrayList<>(goals.roleUpdates(p.getUniqueId(),c.roles()+(d.roles().isEmpty()?"":" · "+String.join(", ",d.roles()))));
        if(!s.invitations.containsAll(ids))lines.add("Есть приглашения в город или компанию. Откройте «Входящие».");s.invitations=Set.copyOf(ids);
        for(String line:lines)send.accept(object("op","notification","title","Город и компании","message",line));
    }
    private CivicView view(Player p,State s,TownyCivic.Context c,CivicData.Snapshot d,CityBuildReader.Result b,String balance,CityHistory.Result journal,CityFinance finance,DevelopmentView planning)throws Exception{
        CivicView v=new CivicView();v.route=s.route;v.id=s.id;v.notice=d.warning();
        switch(s.route){
            case "tree" -> {require(c.town()!=null&&has(p,"neverlandtownybuilds.use"),"Нет доступа к развитию города");v.rows.add(new Row("План и смета","Выбрать будущие здания, оценить цену и материалы",new Action("route","planner")));}
            case "planner","planner-catalog","planner-project","planner-supply","planner-effects" -> {require(c.town()!=null,"Сначала вступите в город");require(has(p,"neverlandtownybuilds.use"),"Нет доступа к постройкам");planning.populate(v,s.route,s.id,Map.copyOf(s.plan));
                if(s.route.equals("planner-supply")&&planning.available()){
                    String reason=procurementAccessReason(p,c);var added=new HashSet<String>();
                    for(var step:planning.steps())if(added.add(step.id()))v.rows.add(new Row("Заказать материалы: "+step.name(),"Пересчитать нехватку для ближайшего уровня; сначала смета, затем подтверждение","",new Action("procurement-offers",step.id()),reason));
                }
                if(Set.of("planner","planner-supply").contains(s.route))v.rows.add(new Row("Закупки города","Сохранённые заявки, подтверждение оплаты и возвраты","",new Action("procurement-page","1"),procurementAccessReason(p,c)));
            }
            case "city" -> {
                if(s.cityDashboard){
                    v.title=c.townName();Object town=towny.town(c.town());
                    String buildAccess=!has(p,"neverlandtownybuilds.use")?"Нужно право просмотра построек":towny.addon("NeverLandTownyBuilds")==null?"Аддон построек недоступен":"";
                    String contractAccess=!has(p,"mintcontracts.use")?"Нужно право просмотра контрактов":towny.addon("NeverLandTownyContracts")==null?"Аддон контрактов недоступен":"";
                    String treasuryAccess=!has(p,"neverlandtownytreasury.use")&&!has(p,"neverlandtownytreasury.admin")?"Нужно право просмотра казны":towny.addon("NeverLandTownyTreasuryPlus")==null?"Аддон казны недоступен":"";
                    String historyAccess=!has(p,"townychronicles.use")?"Нужно право просмотра летописи":towny.addon(CityHistory.PLUGIN)==null?"Летопись сейчас недоступна":"";
                    CityDashboardView.populate(v,c,b,d,balance,((Number)call(town,"getNumResidents")).intValue(),(boolean)call(town,"hasUnlimitedClaims")?"без лимита":call(town,"getMaxTownBlocks").toString(),s.citySection,new CityDashboardView.Access(buildAccess,contractAccess,treasuryAccess,historyAccess));
                    CityDashboardView.finances(v,finance,treasuryAccess);
                    break;
                }
                v.title=c.townName();v.lines.add(c.roles()+" · участков занято "+c.claimed()+" · можно захватить "+CitySnapshot.number(c.remaining()));
                v.rows.add(new Row("Казна и операции","Баланс: "+balance+" · история городского бюджета","",new Action("route","treasury"),c.town()==null?"Сначала вступите в город":!has(p,"neverlandtownytreasury.use")&&!has(p,"neverlandtownytreasury.admin")?"Нужно право просмотра казны":""));
                if(finance.known())v.rows.add(new Row("Финансы города","Расходы: "+CityFinance.money(finance.expenses())+" · итог: "+CityFinance.signed(finance.net())+" · "+finance.period(),new Action("route","treasury")));
                v.rows.add(new Row("История города","Летопись: события, даты, участники и подробности","",new Action("route","history"),c.town()==null?"Сначала вступите в город":!has(p,"townychronicles.use")?"Нужно право просмотра летописи":towny.addon(CityHistory.PLUGIN)==null?"Chronicles не установлен или выключен":""));
                for(var site:b.sites())v.rows.add(new Row(site.label(),site.known()?"Установлено "+site.placed()+" / "+site.total():"Подойдите к площадке для подсчёта",new Action("build",site.id())));
                if(c.town()!=null)v.rows.add(Row.info("Жители и лимит",call(towny.town(c.town()),"getNumResidents")+" жителей · лимит участков "+((boolean)call(towny.town(c.town()),"hasUnlimitedClaims")?"без лимита":call(towny.town(c.town()),"getMaxTownBlocks"))));
                for(var level:b.levels().entrySet())if(level.getValue()>0&&b.sites().stream().noneMatch(site->site.id().equals(level.getKey())))v.rows.add(new Row(b.names().getOrDefault(level.getKey(),level.getKey()),"Построено · уровень "+level.getValue(),new Action("build",level.getKey())));
                for(var task:d.contracts())v.rows.add(new Row(task.title(),"Прогресс "+task.progress()+" / "+task.goal(),new Action("contract",task.id())));
                if(c.town()==null)v.lines.add("Вступите в город, чтобы видеть его стройки и задачи.");
                v.rows.add(new Row("План развития","Зависимости, общая смета и снабжение",new Action("route","planner")));
                v.rows.add(new Row("Материалы","Стройки, склад и ваш инвентарь",new Action("route","materials")));
                v.rows.add(new Row("Общая цель","Приоритет города и личный вклад",new Action("route","goals")));
            }
            case "history","history-entry" -> {
                require(has(p,"townychronicles.use"),"Нужно право просмотра летописи");require(c.town()!=null,"Сначала вступите в город");
                v.title="История города · "+c.townName();v.notice="";
                if(s.route.equals("history")&&has(p,"townychronicles.contributions")&&CityHistory.contributionsAvailable(towny.addon(CityHistory.PLUGIN)))
                    v.rows.add(new Row("Вклад жителей","Личные материалы, взносы, ремонты, защита и благодарности мэра",new Action("open-contributions","")));
                if(!journal.problem().isBlank()){v.rows.add(Row.info("Летопись недоступна",journal.problem()));break;}
                if(s.route.equals("history")){
                    v.lines.add("Летопись Chronicles · новые события сверху · время МСК");
                    if(journal.limited())v.lines.add("Показаны последние "+CityHistory.MAX_ENTRIES+" записей.");
                    for(var event:journal.entries())v.rows.add(new Row(event.title(),event.subtitle(),new Action("history-entry",event.id())));
                    if(journal.entries().isEmpty())v.rows.add(Row.info("История пока пуста","Chronicles ещё не записал события этого города."));
                }else{
                    var event=journal.entries().stream().filter(e->e.id().equals(s.id)).findFirst().orElseThrow(()->new IllegalArgumentException("Запись больше недоступна. Вернитесь к истории города."));
                    v.title=event.title();v.document=true;v.lines.add(event.title());v.lines.add(event.subtitle());
                    if(!event.source().isBlank())v.lines.add("Источник: "+event.source());
                    v.lines.add("");v.lines.addAll(event.details());
                    if(event.details().isEmpty())v.lines.add("Дополнительных подробностей нет.");
                }
            }
            case "treasury" -> {
                v.title="История казны";require(has(p,"neverlandtownytreasury.use")||has(p,"neverlandtownytreasury.admin"),"Нужно право просмотра казны");require(c.town()!=null,"Сначала вступите в город");
                finance.addDetails(v);
                v.lines.add("Журнал бюджета TreasuryPlus. Он не включает операции, которые другие плагины не передали этому аддону.");
                v.rows.add(new Row("Управление казной","Открыть бюджет и распределение средств",new Action("command","treasury")));
                for(String line:d.treasury())v.rows.add(Row.info("Операция",line));if(d.treasury().isEmpty())v.rows.add(Row.info("История пуста","Пока нет записанных операций бюджета."));
            }
            case "materials" -> {
                v.title="Недостающие материалы";require(b.known(),"Данные построек сейчас недоступны. Проверьте аддон Builds.");require(c.town()!=null,"Сначала вступите в город");require(has(p,"neverlandtownybuilds.use"),"Нет доступа к постройкам");
                var need=new TreeMap<Material,Integer>();for(var site:b.sites())if(site.known())site.missing().forEach((m,n)->need.merge(m,n,Integer::sum));
                if(b.sites().stream().anyMatch(site->!site.known()))v.lines.add("Часть площадок недоступна. Ниже только подтверждённые потребности загруженных строек.");
                Boolean returned=null;try{returned=(Boolean)call(towny.addon("NeverLandTownyBuilds"),"uiReturnsPlacedMaterial");}catch(Exception ignored){}
                var warehouse=warehouse(c);Map<Material,Integer> inventory=count(p.getInventory().getStorageContents(),false);
                v.lines.add("Осталось установить · на складе · у вас · добыть для установки. Ресурсы здесь не резервируются.");
                if(Boolean.TRUE.equals(returned))v.lines.add(0,"Материал возвращается после установки: нужен один предмет каждого типа. Фонд запуска оплачивается отдельно в управлении стройкой.");
                if(returned==null)v.lines.add("Правила расхода не переданы аддоном: расчёт добычи недоступен.");
                for(var e:need.entrySet()){
                    var n=CivicMath.placement(e.getKey().name(),e.getValue(),warehouse==null?0:warehouse.getOrDefault(e.getKey(),0),inventory.getOrDefault(e.getKey(),0),Boolean.TRUE.equals(returned));
                    v.rows.add(new Row("","Установить: "+n.remaining()+" · склад: "+(warehouse==null?"неизвестно":n.warehouse())+" · у вас: "+n.player()+" · добыть: "+(warehouse==null||returned==null?"уточните данные":n.gather()),e.getKey().translationKey(),null,""));
                }
                if(need.isEmpty())v.rows.add(Row.info("Нет подтверждённого дефицита","Откройте детали нужной стройки."));
                if(warehouse==null)v.lines.add("Склад открыт другим игроком или недоступен: расчёт «ещё добыть» приостановлен.");
            }
            case "inbox" -> {
                v.title="Входящие приглашения";v.lines.add(c.roles());v.lines.addAll(d.roles());
                for(var i:towny.invitations(p))v.rows.add(new Row("Город «"+i.town()+"»","Приглашение вступить",new Action("invite-town",i.id())));
                for(var i:d.invites())v.rows.add(new Row("Компания «"+i.name()+"»","Истекает через "+Math.max(1,(i.expiry()-System.currentTimeMillis())/60000)+" мин.",new Action("invite-company",i.id())));
                if(v.rows.isEmpty())v.rows.add(Row.info("Приглашений нет","Новые приглашения появятся здесь."));
            }
            case "invite-town","invite-company" -> {
                boolean company=s.route.equals("invite-company");v.title=company?"Приглашение в компанию":"Приглашение в город";
                if(company){var i=d.invites().stream().filter(x->x.id().equals(s.id)).findFirst().orElseThrow(()->new IllegalArgumentException("Приглашение истекло"));v.lines.add(i.name());String reason=c.town()==null||!c.town().toString().equals(i.town())?"Сначала вступите в город компании":!d.roles().isEmpty()?"Вы уже состоите в компании":"";
                    v.rows.add(new Row("Принять","Вступить сотрудником","",new Action("review-company-join",i.id()),reason));v.rows.add(new Row("Отклонить","Убрать это приглашение",new Action("review-company-decline",i.id())));
                }else{var i=towny.invitations(p).stream().filter(x->x.id().equals(s.id)).findFirst().orElseThrow(()->new IllegalArgumentException("Приглашение истекло"));v.lines.add(i.town());
                    v.rows.add(new Row("Принять","Вступить в город","",new Action("review-town-accept",i.id()),c.town()!=null?"Сначала выйдите из текущего города":""));v.rows.add(new Row("Отклонить","Убрать это приглашение",new Action("review-town-deny",i.id())));}
            }
            case "build" -> {
                require(has(p,"neverlandtownybuilds.use"),"Нет доступа к постройкам");var site=b.sites().stream().filter(x->x.id().equals(s.id)).findFirst().orElse(null);v.title=b.names().getOrDefault(s.id,s.id);
                if(site!=null){v.lines.add(site.known()?"Установлено "+site.placed()+" из "+site.total():"Часть площадки не загружена");for(var e:site.missing().entrySet())v.rows.add(new Row("","Ещё установить: "+e.getValue(),e.getKey().translationKey(),null,""));}
                String buildReason=BuildDetailsAccess.reason(towny.addon("NeverLandTownyBuilds"));
                v.rows.add(0,new Row("Открыть управление стройкой","Условия, улучшения и действия","",new Action("open-build",s.id),buildReason));
                if(!buildReason.isBlank())v.rows.add(new Row("Все постройки","Открыть доступное меню Builds и выбрать стройку",new Action("open-builds","")));
                v.rows.add(1,new Row("Закрепить как общую цель","Жители увидят прогресс и вклад","",new Action("review-pin-build",s.id),site==null?"Стройка не активна":c.manager()?"":"Нужна должность мэра или право управления целями"));
            }
            case "contract" -> {
                var task=d.contracts().stream().filter(x->x.id().equals(s.id)).findFirst().orElseThrow(()->new IllegalArgumentException("Контракт уже завершён или недоступен"));v.title=task.title();v.lines.add("Общий прогресс "+task.progress()+" / "+task.goal());v.lines.add("Ваш вклад: "+task.contributions().getOrDefault(p.getUniqueId().toString(),0));
                v.rows.add(new Row("Условия и действия","Сдать ресурсы, получить награду, управление",new Action("open-contract",s.id)));
                v.rows.add(new Row("Закрепить цель города","Приоритет для жителей","",new Action("review-pin-contract",s.id),c.manager()?"":"Нужна должность мэра или право управления целями"));
            }
            case "goals" -> {
                v.title="Общая цель города";require(c.town()!=null,"Сначала вступите в город");var goal=goals.get(c.town());
                if(goal==null)v.lines.add("Цель не выбрана. Мэр может закрепить стройку или городской контракт.");
                else if(goal.kind().equals("build")){
                    var site=b.sites().stream().filter(x->x.id().equals(goal.id())).findFirst().orElse(null);
                    v.rows.add(new Row(b.names().getOrDefault(goal.id(),goal.id()),site==null?"Стройка завершена или больше не активна":site.known()?"Общий прогресс "+site.placed()+" / "+site.total()+" · ваш вклад "+goals.contribution(c.town(),p.getUniqueId(),site):"Прогресс уточняется возле площадки",new Action("build",goal.id())));
                    v.lines.add("Личный вклад: ваши подходящие блоки, установленные после закрепления и сохранившиеся в чертеже.");
                }else{
                    var task=d.contracts().stream().filter(x->x.id().equals(goal.id())).findFirst().orElse(null);
                    v.rows.add(new Row(task==null?"Контракт завершён или недоступен":task.title(),task==null?"Откройте историю контрактов":"Прогресс "+task.progress()+" / "+task.goal()+" · ваш вклад "+task.contributions().getOrDefault(p.getUniqueId().toString(),0),task==null?null:new Action("contract",goal.id())));
                }
                if(goal!=null)v.rows.add(new Row("Снять закрепление","Стройка и ресурсы сохранятся","",new Action("review-unpin",""),c.manager()?"":"Нужно право управления целями"));
                for(var site:b.sites())v.rows.add(new Row(site.label(),"Выбрать стройку",new Action("build",site.id())));
                for(var task:d.contracts())v.rows.add(new Row(task.title(),"Выбрать контракт",new Action("contract",task.id())));
            }
            case "map" -> map(p,s,c,v);
            case "plot" -> plot(p,s,c,v);
            case "orders","mine","offer","order" -> orders(p,s,v);
            default -> throw new IllegalArgumentException("Неизвестный экран");
        }
        var pending=confirmations.get(p);if(pending!=null){v.rows.add(0,new Row("Подтверждение Towny",confirmations.title(p,pending),new Action("review-towny-confirm",pending.token())));v.rows.add(1,new Row("Отменить подтверждение Towny","Не выполнять ожидающую операцию",new Action("cancel-towny",pending.token())));}
        return v;
    }
    @SuppressWarnings("unchecked") private Map<Material,Integer> warehouse(TownyCivic.Context c){try{
        var addon=towny.addon("NeverLandTownyBuilds");if(addon==null)return null;Object storage=call(addon,"storage");
        if((boolean)storage.getClass().getMethod("busy",UUID.class,String.class).invoke(storage,c.town(),"warehouse"))return null;
        ItemStack[] stock=(ItemStack[])storage.getClass().getMethod("stock",UUID.class,String.class).invoke(storage,c.town(),"warehouse");return count(stock,false);
    }catch(Exception e){return null;}}
    private static Map<Material,Integer> count(ItemStack[] stacks,boolean plain){Map<Material,Integer> result=new EnumMap<>(Material.class);for(var item:stacks)if(item!=null&&!item.getType().isAir()&&(!plain||item.isSimilar(new ItemStack(item.getType()))))result.merge(item.getType(),item.getAmount(),Integer::sum);return result;}
    private Object ordersApi(){var command=plugin.getServer().getPluginCommand("neverlandtownycontracts:orders");require(command!=null&&command.getPlugin().getName().equals("NeverLandTownyContracts")&&command.getPlugin().isEnabled(),"Аддон заказов недоступен");Object executor=command.getExecutor();try{executor.getClass().getMethod("uiSnapshot",Player.class);}catch(Exception e){throw new IllegalArgumentException("Для нового экрана заказов обновите Contracts до 0.3.23");}return executor;}
    private JsonObject orderData(Player p)throws Exception{return JSON.toJsonTree(call(ordersApi(),"uiSnapshot",Player.class,p)).getAsJsonObject();}
    private static JsonObject find(JsonObject data,String list,String id){for(var row:data.getAsJsonArray(list))if(str(row.getAsJsonObject(),"id","").equals(id))return row.getAsJsonObject();throw new IllegalArgumentException("Заказ недоступен или уже изменился");}
    private static long cents(JsonObject row){return row.get("cents").getAsLong();}
    private static String money(long cents){return String.format(Locale.ROOT,"%,.2f",cents/100.0);}
    private static String typeName(String type){return switch(type){case "DELIVERY"->"Поставка";case "MINING"->"Добыча";case "HUNTING"->"Охота";case "FISHING"->"Рыбалка";default->type;};}
    private static String rarityName(String r){return switch(r){case "COMMON"->"Обычный";case "UNCOMMON"->"Необычный";case "RARE"->"Редкий";case "EPIC"->"Эпический";case "LEGENDARY"->"Легендарный";default->r;};}
    private int carried(Player p,JsonObject row){Material m=Material.matchMaterial(str(row,"target",""));return m==null?0:count(p.getInventory().getStorageContents(),true).getOrDefault(m,0);}
    private static String target(JsonObject row){Material m=Material.matchMaterial(str(row,"target",""));return m!=null?m.translationKey():"entity.minecraft."+str(row,"target","").toLowerCase(Locale.ROOT);}
    private void orders(Player p,State s,CivicView v)throws Exception{
        var data=orderData(p);v.title=s.route.equals("mine")?"Мои заказы":"Подбор заказов";
        if(s.route.equals("orders")){
            v.lines.add("Ваша личная доска на "+str(data,"day","")+" · новая подборка в 00:00 МСК. Подсказка по инвентарю относится только к поставкам.");
            v.rows.add(new Row("Мои заказы","Прогресс, сдача и отмена",new Action("route","mine")));
            Map<String,JsonObject> rows=new HashMap<>();var offers=new ArrayList<CivicMath.Offer>();for(var element:data.getAsJsonArray("offers")){var r=element.getAsJsonObject();String id=str(r,"id","");rows.put(id,r);offers.add(new CivicMath.Offer(id,str(r,"title",""),str(r,"type",""),str(r,"rarity",""),cents(r),integer(r,"goal",0),carried(p,r)));}
            for(var offer:CivicMath.filter(offers,s.type,s.rarity,s.minimum,s.sort,s.readyOnly)){
                var r=rows.get(offer.id());boolean ready=offer.type().equals("DELIVERY")&&offer.carried()>=offer.amount();
                v.rows.add(new Row(offer.title(),typeName(offer.type())+" · "+rarityName(offer.rarity())+" · "+money(offer.cents())+(ready?" · предметы уже есть":""),target(r),new Action("review-order-accept",offer.id()),str(r,"reason","")).order(offer.rarity(),money(offer.cents()),typeName(offer.type()),offer.type()));
            }
            if(v.rows.size()==1)v.rows.add(Row.info("Ничего не найдено","Измените фильтры."));
        }else if(s.route.equals("mine")){
            for(var e:data.getAsJsonArray("mine")){var r=e.getAsJsonObject();int progress=str(r,"type","").equals("DELIVERY")?Math.min(carried(p,r),integer(r,"goal",0)):integer(r,"progress",0);v.rows.add(new Row(str(r,"title",""),progress+" / "+integer(r,"goal",0)+" · "+money(cents(r)),target(r),new Action("order",str(r,"id","")),"").order(str(r,"rarity",""),money(cents(r)),typeName(str(r,"type","")),str(r,"type","")));}
            if(v.rows.isEmpty())v.rows.add(Row.info("Активных заказов нет","Выберите подходящий на доске."));
        }else{
            boolean offer=s.route.equals("offer");var r=find(data,offer?"offers":"mine",s.id);v.title=str(r,"title","");
            if(r.has("instructions"))for(var instruction:r.getAsJsonArray("instructions"))v.lines.add(instruction.getAsString());
            v.lines.add(typeName(str(r,"type",""))+" · "+rarityName(str(r,"rarity",""))+" · награда "+money(cents(r)));v.rows.add(new Row("","Требуется "+integer(r,"goal",0)+" · у вас "+carried(p,r),target(r),null,""));
            if(offer)v.rows.add(new Row("Принять заказ","Стоимость принятия: 0","",new Action("review-order-accept",s.id),str(r,"reason","")));
            else{boolean ready=str(r,"type","").equals("DELIVERY")?carried(p,r)>=integer(r,"goal",0):integer(r,"progress",0)>=integer(r,"goal",0);
                v.rows.add(new Row("Сдать и получить награду",str(r,"type","").equals("DELIVERY")?"Предметы будут списаны из инвентаря":"Зачисление за подтверждённый прогресс","",new Action("review-order-claim",s.id),ready?"":"Условия заказа ещё не выполнены"));
                v.rows.add(new Row("Отменить заказ","Штрафа нет; награда за прогресс не выплачивается",new Action("review-order-cancel",s.id)));
            }
        }
    }
    private void map(Player p,State s,TownyCivic.Context c,CivicView v)throws Exception{
        require(c.town()!=null,"Сначала вступите в город");v.title="Карта участков";v.lines.add("Ваш город — зелёный, другие — красный, свободные — серый. Выберите клетку. Управление доступно, когда вы стоите на выбранном участке.");
        int cx=c.x(),cz=c.z();if(!s.id.isEmpty()){String[] xy=s.id.split(",");cx=Integer.parseInt(xy[0]);cz=Integer.parseInt(xy[1]);}
        JsonArray cells=new JsonArray();for(int z=cz-4;z<=cz+4;z++)for(int x=cx-4;x<=cx+4;x++){
            Object plot=towny.plot(c,x,z),town=plot==null?null:call(plot,"getTownOrNull"),owner=plot==null?null:call(plot,"getResidentOrNull");
            String townId=town==null?"":call(town,"getUUID").toString();String name=town==null?"Свободная территория":CivicData.clean(call(town,"getName").toString());
            cells.add(object("id",x+","+z,"action","cell"+x+"_"+z,"x",x,"z",z,"status",town==null?"free":townId.equals(c.town().toString())?"own":"other","owner",owner==null?name:CivicData.clean(call(owner,"getName").toString()),"here",x==c.x()&&z==c.z(),"town",townId));
        }
        v.map=object("cells",cells,"size",c.size(),"cx",cx,"cz",cz);
        for(var offset:List.of(new int[]{0,-9},new int[]{-9,0},new int[]{9,0},new int[]{0,9}))v.rows.add(new Row(offset[0]<0?"← Запад":offset[0]>0?"Восток →":offset[1]<0?"↑ Север":"Юг ↓","",new Action("map-pan",(cx+offset[0])+","+(cz+offset[1]))));
        v.rows.add(new Row("К моему участку","",new Action("map-pan","")));
    }
    private void plot(Player p,State s,TownyCivic.Context c,CivicView v)throws Exception{
        require(s.selectedWorld.equals(c.world().toString()),"Вы перешли в другой мир. Обновите карту.");
        String[] xy=s.id.split(",");int x=Integer.parseInt(xy[0]),z=Integer.parseInt(xy[1]);Object plot=towny.plot(c,x,z),town=plot==null?null:call(plot,"getTownOrNull"),owner=plot==null?null:call(plot,"getResidentOrNull");
        v.title="Участок "+x+", "+z;v.lines.add("Границы блоков: X "+(x*c.size())+"…"+((x+1)*c.size()-1)+", Z "+(z*c.size())+"…"+((z+1)*c.size()-1));
        boolean local=x==c.x()&&z==c.z(),ownTown=town!=null&&c.town()!=null&&call(town,"getUUID").equals(c.town());String far=local?"":"Подойдите к выбранному участку";
        v.lines.add("Город: "+(town==null?"не занят":call(town,"getName"))+" · владелец: "+(owner==null?"город / отсутствует":call(owner,"getName")));
        if(plot!=null)v.lines.add("Тип: "+call(plot,"getTypeName")+" · правила: "+call(plot,"getPermissions"));
        if(town==null)v.rows.add(new Row("Захватить для города","Сначала показать цену и последствия","",new Action("review-plot-claim",s.id,c.world().toString()),!far.isEmpty()?far:c.town()==null?"Сначала вступите в город":!has(p,"towny.command.town.claim")?"Нужно право захвата территории":c.remaining()==0?"Исчерпан лимит участков":""));
        if(ownTown){
            v.rows.add(new Row("Освободить территорию города","Предпросмотр возврата и изменений","",new Action("review-plot-unclaim",s.id,c.world().toString()),!far.isEmpty()?far:!has(p,"towny.command.town.unclaim")?"Нужно право освобождения территории":(boolean)call(plot,"isHomeBlock")?"Главный участок нельзя освободить здесь":""));
            double price=((Number)call(plot,"getPlotPrice")).doubleValue();
            if(price>=0)v.rows.add(new Row("Купить участок","Цена: "+String.format(Locale.ROOT,"%.2f",towny.economy()?price:0),"",new Action("review-plot-buy",s.id,c.world().toString()),!far.isEmpty()?far:!has(p,"towny.command.plot.claim")?"Нет права покупки участка":(boolean)call(plot,"hasPlotObjectGroup")?"Участок входит в группу; покупка через остальные действия":""));
            v.rows.add(new Row("Остальные действия с участком","Тип, разрешения, продажа — формы Towny","",new Action("plot-commands",s.id),far));
        }
    }
    private void act(Player p,State s,Action action,Consumer<JsonObject> send)throws Exception{
        if(action.kind().startsWith("procurement-review-")){reviewProcurement(p,s,action,send);return;}
        if(action.kind().startsWith("review-")){
            var review=review(p,action);String confirmation=s.ticket.issue(review,System.nanoTime());
            send.accept(object("op","review","request",s.request,"token",s.token,"confirmation",confirmation,"title",review.title(),"lines",review.lines(),"targetKey",review.targetKey(),"confirmLabel",action.kind().equals("review-order-accept")?"Да, принять заказ":"Подтвердить"));return;
        }
        switch(action.kind()){
            case "procurement-offers" -> navigate(s,"procurement-offers",action.id());
            case "procurement-order" -> navigate(s,"procurement-order",UUID.fromString(action.id()).toString());
            case "procurement-page" -> {int page=Integer.parseInt(action.id());require(page>=1&&page<=10000,"Страница закупок недоступна");navigate(s,"procurement-requests",Integer.toString(page));}
            case "procurement-quote" -> {
                var c=towny.capture(p);procurementCall(p,s,c,"quote",new Object[]{action.id(),action.extra()},send,value->{
                    var row=ProcurementView.order(value,c.town(),null);require(row.offer().project().equals(action.id())&&row.offer().itemKey().equals(action.extra())&&row.actor().equals(p.getUniqueId())&&row.state().equals("QUOTE"),"Смета не соответствует выбранному материалу");
                    navigate(s,"procurement-order",row.id().toString());renderProcurement(p,s,ProcurementView.orderPage(row,System.currentTimeMillis()),send);
                });return;
            }
            case "plan-project" -> {require(has(p,"neverlandtownybuilds.use"),"Нет доступа к постройкам");navigate(s,"planner-project",action.id());}
            case "plan-level" -> {require(has(p,"neverlandtownybuilds.use"),"Нет доступа к постройкам");require(s.plan.containsKey(action.id())||s.plan.size()<8,"В плане уже восемь целей. Уберите одну из них.");s.plan.put(action.id(),Integer.parseInt(action.extra()));navigate(s,"planner","");}
            case "plan-remove" -> {s.plan.remove(action.id());navigate(s,"planner","");}
            case "plan-clear" -> {s.plan.clear();navigate(s,"planner","");}
            case "cancel-towny" -> {confirmations.execute(p,action.id(),false);}
            case "route" -> navigate(s,allowedRoute(action.id()),"");
            case "open-research" -> {require(towny.capture(p).town()!=null&&has(p,"neverlandtownyresearch.use")&&action.id().matches("[a-z0-9_-]{1,64}"),"Нет доступа к исследованию");plugin.civicAnchor(p);dispatch(p,"NeverLandTownyResearch","townyresearch","info "+action.id());return;}
            case "open-content" -> {require(towny.capture(p).town()!=null,"Сначала вступите в город");String args=switch(action.id()){case "market"->"market";case "global-market"->"market global";default->throw new IllegalArgumentException("Неизвестный раздел развития");};plugin.civicAnchor(p);dispatch(p,"Towny","town",args);return;}
            case "build","contract","history-entry","invite-town","invite-company","offer","order","plot" -> {if(action.kind().equals("plot")){require(action.extra().equals(towny.capture(p).world().toString()),"Вы перешли в другой мир. Обновите карту.");s.selectedWorld=action.extra();}navigate(s,action.kind(),action.id());}
            case "map-pan" -> {s.id=action.id();s.page=0;}
            case "open-build" -> {
                require(has(p,"neverlandtownybuilds.use"),"Нет доступа к постройкам");Plugin addon=towny.addon("NeverLandTownyBuilds");require(addon!=null,"Аддон построек недоступен");
                require(BuildDetailsAccess.supported(addon.getClass()),BuildDetailsAccess.reason(addon));
                var method=addon.getClass().getMethod("uiOpenDetails",Player.class,String.class);plugin.civicAnchor(p);method.invoke(addon,p,action.id());
                return;
            }
            case "open-builds" -> {require(has(p,"neverlandtownybuilds.use"),"Нет доступа к постройкам");require(towny.addon("NeverLandTownyBuilds")!=null,"Аддон построек недоступен");plugin.civicAnchor(p);dispatch(p,"Towny","town","builds");return;}
            case "open-contract" -> {require(has(p,"mintcontracts.use"),"Нет доступа к контрактам");plugin.civicAnchor(p);dispatch(p,"Towny","town","contracts info "+UUID.fromString(action.id()));return;}
            case "command" -> {require(has(p,"neverlandtownytreasury.use")||has(p,"neverlandtownytreasury.admin"),"Нет доступа к казне");plugin.civicAnchor(p);dispatch(p,"Towny","town","treasury history");return;}
            case "plot-commands" -> {send.accept(object("op","commands","request",s.request,"module","Towny"));return;}
            case "open-contributions" -> {
                require(has(p,"townychronicles.use")&&has(p,"townychronicles.contributions")&&towny.capture(p).town()!=null,"Нужно право просмотра вклада жителей своего города");
                require(CityHistory.contributionsAvailable(towny.addon(CityHistory.PLUGIN)),"Для вклада жителей обновите Chronicles");
                plugin.civicAnchor(p);dispatch(p,CityHistory.PLUGIN,"chronicles","contributions");return;
            }
            default -> throw new IllegalArgumentException("Действие недоступно");
        }
        load(p,s,send,false);
    }
    static List<String> acceptPreview(JsonObject r){
        String name=str(r,"title","Заказ");long duration=Math.max(0,r.has("duration")?r.get("duration").getAsLong():0);
        var lines=new ArrayList<String>();lines.add("Вы уверены, что хотите принять заказ «"+name+"»?");
        lines.add("Тип: "+typeName(str(r,"type",""))+" · редкость: "+rarityName(str(r,"rarity","")));
        lines.add("Объём задания: "+integer(r,"goal",0)+" × {craftoui:target}");
        if(duration>0){long minutes=Math.max(1,(duration+59999)/60000);lines.add("Срок после принятия: "+(minutes>=60?(minutes/60)+" ч "+(minutes%60)+" мин":minutes+" мин"));}
        if(r.has("instructions"))for(var instruction:r.getAsJsonArray("instructions"))lines.add(instruction.getAsString());
        lines.add("Награда после сдачи: "+money(cents(r))+" монет. При принятии награда не начисляется.");
        lines.add("Плата за принятие: 0. Заказ займёт один активный слот. Отмена сбросит прогресс.");
        lines.add("Нажмите «Да, принять заказ» только если согласны с условиями. «Отмена» ничего не изменит.");
        return List.copyOf(lines);
    }
    private Review review(Player p,Action a)throws Exception{
        var c=towny.capture(p);String fingerprint="",title="Подтверждение",targetKey="";List<String> lines=new ArrayList<>();
        if(a.kind().equals("review-towny-confirm")){
            var pending=confirmations.get(p);require(pending!=null&&pending.token().equals(a.id()),"Подтверждение Towny устарело");title="Окончательное подтверждение Towny";fingerprint=pending.token();lines.add(confirmations.title(p,pending));lines.add("Будет выполнена операция, указанная выше. Towny повторно проверит её условия и стоимость. Для отмены вернитесь назад и нажмите «Отменить подтверждение Towny».");
        }else if(a.kind().startsWith("review-order-")){
            String operation=a.kind().substring("review-order-".length());var r=find(orderData(p),operation.equals("accept")?"offers":"mine",a.id());fingerprint=str(r,"quote","");title=str(r,"title","");
            if(operation.equals("accept")){require(str(r,"reason","").isEmpty(),str(r,"reason",""));title="Принять заказ?";targetKey=target(r);lines.addAll(acceptPreview(r));}
            else if(operation.equals("cancel")){lines.add("Заказ будет отменён, его прогресс потерян. Штраф: 0. Возврат: 0.");lines.add("Награда "+money(cents(r))+" не будет выплачена. Предметы в инвентаре останутся у вас.");lines.add("Повторно взять этот заказ на сегодняшней доске нельзя.");}
            else{boolean delivery=str(r,"type","").equals("DELIVERY");require(delivery?carried(p,r)>=integer(r,"goal",0):integer(r,"progress",0)>=integer(r,"goal",0),"Условия ещё не выполнены");lines.add(delivery?"Будет списано "+integer(r,"goal",0)+" × "+str(r,"target","")+" из инвентаря. Именные и изменённые предметы не принимаются.":"Предметы не списываются. Подтверждённый прогресс будет использован для выплаты.");lines.add("Награда: "+money(cents(r))+". Зачисление через платёжный журнал аддона; повторная выплата защищена сервером.");}
        }else if(a.kind().startsWith("review-plot-")){
            require(c.town()!=null,"Сначала вступите в город");require(a.extra().equals(c.world().toString()),"Вы перешли в другой мир");require(a.id().equals(c.x()+","+c.z()),"Подойдите к выбранному участку");Object plot=towny.plot(c,c.x(),c.z()),town=towny.town(c.town());String op=a.kind().substring("review-plot-".length());double price;
            if(op.equals("claim")){require(plot==null,"Территория уже занята");require(has(p,"towny.command.town.claim")&&c.remaining()!=0,"Нет права или свободного лимита участков");price=towny.economy()?((Number)call(town,"getTownBlockCost")).doubleValue():0;title="Захват территории";lines.add("Списание из казны: "+String.format(Locale.ROOT,"%.2f",price)+". Занятых участков станет на один больше, свободный лимит уменьшится на один.");}
            else{require(plot!=null&&Objects.equals(call(call(plot,"getTownOrNull"),"getUUID"),c.town()),"Участок больше не принадлежит вашему городу");
                if(op.equals("unclaim")){require(has(p,"towny.command.town.unclaim")&&!(boolean)call(plot,"isHomeBlock"),"Нет права освобождения участка либо это главный участок");price=towny.economy()?((Number)towny.setting("getClaimRefundPrice")).doubleValue():0;title="Освобождение территории";lines.add(price>=0?"Возврат в казну: "+String.format(Locale.ROOT,"%.2f",price):"Списание из казны: "+String.format(Locale.ROOT,"%.2f",-price));lines.add("Участок перестанет принадлежать городу и потеряет его защиту. Towny может восстановить блоки по настройкам сервера.");}
                else{require(has(p,"towny.command.plot.claim"),"Нет права покупки участка");price=((Number)call(plot,"getPlotPrice")).doubleValue();require(price>=0,"Участок не продаётся");require(!(boolean)call(plot,"hasPlotObjectGroup"),"Участок входит в группу. Откройте покупку через «Остальные действия», чтобы Towny показал стоимость всей группы.");if(!towny.economy())price=0;title="Покупка участка";lines.add("Списание с личного счёта: "+String.format(Locale.ROOT,"%.2f",price)+". Владельцем станете вы; действуют городские налоги и правила.");}
            }
            require(Double.isFinite(price),"Стоимость недоступна");fingerprint=op+"/"+price+"/"+(plot==null?"free":String.valueOf(call(plot,"getResidentOrNull")))+"/"+c.claimed();
            lines.add("Участок: "+a.id()+". Перед выполнением Towny повторно проверит права, баланс, соседство и лимиты.");
        }else if(a.kind().startsWith("review-town-")){
            var invite=towny.invitations(p).stream().filter(i->i.id().equals(a.id())).findFirst().orElseThrow(()->new IllegalArgumentException("Приглашение уже недоступно"));boolean accept=a.kind().endsWith("accept");if(accept)require(c.town()==null,"Вы уже состоите в городе");title=invite.town();fingerprint=invite.commandName();lines.add(accept?"Вы вступите в этот город. На вас будут распространяться его налоги и правила. Принятие приглашения не списывает плату за вступление.":"Приглашение будет удалено. Деньги и имущество не изменятся.");
        }else if(a.kind().startsWith("review-company-")){
            require(has(p,"neverlandtownycompanies.use"),"Нет доступа к компаниям");title=a.kind().endsWith("join")?"Вступление в компанию":"Отклонение приглашения";lines.add(a.kind().endsWith("join")?"Вы станете сотрудником компании. Плата за вступление: 0; владение компанией и её долги вам не передаются.":"Ваше приглашение будет удалено. Баланс и состав компании сохранятся.");
        }else if(a.kind().startsWith("review-pin-")||a.kind().equals("review-unpin")){
            require(c.town()!=null&&c.manager(),"Нужна должность мэра или право управления целями");require(goals.ready(),"Хранилище целей недоступно");title=a.kind().equals("review-unpin")?"Снять общую цель":"Закрепить общую цель";
            var goal=goals.get(c.town());fingerprint=String.valueOf(goal);lines.add("Стоимость: 0. Ресурсы и деньги не перемещаются. Стройки и контракты не удаляются.");lines.add(a.kind().equals("review-unpin")?"Жители больше не увидят эту цель закреплённой.":"Предыдущая закреплённая цель будет заменена. Учёт новых блоков для личного вклада начнётся с этого момента.");
        }else throw new IllegalArgumentException("Нет безопасного предпросмотра для этого действия");
        return new Review(a,c.town(),c.world(),c.x(),c.z(),fingerprint,title,List.copyOf(lines),targetKey);
    }
    private void execute(Player p,State s,Review old,Consumer<JsonObject> send)throws Exception{
        if(old.action().kind().startsWith("review-procurement-")){executeProcurement(p,s,old,send);return;}
        Review fresh=review(p,old.action());require(Objects.equals(old.town(),fresh.town())&&old.fingerprint().equals(fresh.fingerprint()),"Условия изменились. Откройте предпросмотр заново.");
        Action a=old.action();String kind=a.kind();
        if(kind.startsWith("review-plot-"))require(old.world().equals(fresh.world())&&old.x()==fresh.x()&&old.z()==fresh.z(),"Вы перешли на другой участок");
        if(kind.equals("review-towny-confirm")){confirmations.execute(p,a.id(),true);s.route="map";s.id="";
        }else if(kind.startsWith("review-order-")){
            String op=kind.substring("review-order-".length());guard(p,"NeverLandTownyContracts","orders",op+" "+a.id());
            Object api=ordersApi();api.getClass().getMethod("uiExecute",Player.class,String.class,String.class,String.class).invoke(api,p,op,a.id(),old.fingerprint());s.route="mine";s.id="";
        }else if(kind.startsWith("review-plot-")){
            String op=kind.substring("review-plot-".length());dispatch(p,"Towny",op.equals("buy")?"plot":"town",op.equals("buy")?"claim":op);s.route="map";s.id="";p.getScheduler().runDelayed(plugin,t->send.accept(object("op","refresh")),null,20);
        }else if(kind.startsWith("review-town-")){
            boolean accept=kind.endsWith("accept");String command=towny.setting(accept?"getAcceptCommand":"getDenyCommand").toString();dispatch(p,"Towny","invite",command+" "+old.fingerprint());s.route="inbox";s.id="";
        }else if(kind.startsWith("review-company-")){
            String id=UUID.fromString(a.id()).toString();
            if(kind.endsWith("join")){
                var command=guard(p,"NeverLandTownyCompanies","townycompanies","join "+id);var executor=command.getExecutor();
                try{executor.getClass().getMethod("uiJoin",Player.class,String.class).invoke(executor,p,id);}catch(NoSuchMethodException e){throw new IllegalArgumentException("Обновите Companies до 0.1.15 для принятия приглашений в UI");}
            }else dispatch(p,"NeverLandTownyCompanies","townycompanies","decline "+id);
            data.invalidate();s.route="inbox";s.id="";
        }else{
            int request=s.request,epoch=++s.epoch;var goal=kind.equals("review-unpin")?null:new CivicGoals.Goal(kind.endsWith("build")?"build":"contract",a.id(),System.currentTimeMillis());
            async(()->{try{goals.pinIfUnchanged(fresh.town(),fresh.fingerprint(),goal);refreshPlans();own(p,()->{if(epoch!=s.epoch)return;s.route="goals";s.id="";s.page=0;try{load(p,s,send,false);}catch(Exception e){send.accept(object("op","error","request",request,"message",error(e)));}});}catch(Exception e){own(p,()->send.accept(object("op","error","request",request,"message",error(e))));}});return;
        }
        s.page=0;send.accept(object("op","notification","title","Действие передано серверу","message","Результат обработки — в чате. Обновите экран для актуальных данных."));load(p,s,send,false);
    }
    private PluginCommand guard(Player p,String owner,String root,String arguments){
        PluginCommand cmd=plugin.getServer().getPluginCommand(owner.toLowerCase(Locale.ROOT)+":"+root);require(cmd!=null&&cmd.getPlugin().getName().equals(owner)&&cmd.getPlugin().isEnabled(),"Аддон действия недоступен");
        require(cmd.testPermissionSilent(p),"Нет разрешения на команду");String line="/"+owner.toLowerCase(Locale.ROOT)+":"+root+(arguments.isBlank()?"":" "+arguments);
        require(!arguments.contains("\n")&&!arguments.contains("\r"),"Неверные параметры");var event=new PlayerCommandPreprocessEvent(p,line);plugin.getServer().getPluginManager().callEvent(event);require(!event.isCancelled()&&event.getMessage().equals(line),"Действие отменено или изменено серверным обработчиком");return cmd;
    }
    private void dispatch(Player p,String owner,String root,String arguments){PluginCommand command=guard(p,owner,root,arguments);command.execute(p,owner.toLowerCase(Locale.ROOT)+":"+root,arguments.isBlank()?new String[0]:arguments.split(" "));}

    private String procurementAccessReason(Player p,TownyCivic.Context c)throws Exception{
        if(c.town()==null)return "Сначала вступите в город";
        if(!has(p,"mintcontracts.buildorders"))return "Нужно право управления закупками города";
        Object city=towny.town(c.town());if(city==null||!p.getUniqueId().equals(call(call(city,"getMayor"),"getUUID")))return "Заказывать материалы и возвращать резерв может только мэр";
        if(towny.addon("NeverLandTownyContracts")==null)return "Аддон контрактов временно недоступен";
        return "";
    }
    private void requireProcurement(Player p,UUID town)throws Exception{
        require(has(p,"craftoui.use")&&has(p,"craftoui.city")&&has(p,"neverlandtownybuilds.use"),"Нет доступа к планировщику");
        var c=towny.capture(p);require(town!=null&&town.equals(c.town()),"Город изменился. Откройте планировщик заново.");String why=procurementAccessReason(p,c);require(why.isEmpty(),why);
    }
    @FunctionalInterface private interface ProcurementResult{void accept(Object value)throws Exception;}
    private void procurementCall(Player p,State s,TownyCivic.Context c,String method,Object[] arguments,Consumer<JsonObject> send,ProcurementResult next)throws Exception{
        requireProcurement(p,c.town());require(!s.procurementBusy,"Закупка ещё обрабатывается. Дождитесь ответа.");
        int epoch=++s.epoch;s.procurementBusy=true;s.actions.clear();s.ticket.clear();
        ProcurementAccess.request(towny.addon("NeverLandTownyContracts"),method,p,arguments).whenComplete((value,failure)->own(p,()->{
            s.procurementBusy=false;if(epoch!=s.epoch)return;
            try{
                requireProcurement(p,c.town());
                if(failure!=null){renderProcurement(p,s,ProcurementView.unavailable(error(ProcurementAccess.reason(failure))),send);return;}
                next.accept(value);
            }catch(Exception e){send.accept(object("op","error","request",s.request,"message",error(e)));}
        }));
    }
    private void loadProcurement(Player p,State s,TownyCivic.Context c,Consumer<JsonObject> send)throws Exception{
        switch(s.route){
            case "procurement-offers" -> {String project=s.id;procurementCall(p,s,c,"offers",new Object[]{project},send,value->renderProcurement(p,s,ProcurementView.offersPage(ProcurementView.offers(value,project)),send));}
            case "procurement-requests" -> {int page=s.id.isEmpty()?1:Integer.parseInt(s.id);require(page>=1&&page<=10000,"Страница недоступна");procurementCall(p,s,c,"requests",new Object[]{page},send,value->renderProcurement(p,s,ProcurementView.ordersPage(ProcurementView.orders(value,c.town()),page),send));}
            case "procurement-order" -> {UUID id=UUID.fromString(s.id);procurementCall(p,s,c,"status",new Object[]{id},send,value->renderProcurement(p,s,ProcurementView.orderPage(ProcurementView.order(value,c.town(),id),System.currentTimeMillis()),send));}
            default -> throw new IllegalArgumentException("Раздел закупок недоступен");
        }
    }
    private void renderProcurement(Player p,State s,CivicView view,Consumer<JsonObject> send){
        s.actions.clear();s.token=UUID.randomUUID().toString();var response=view.json(s.token,s.actions,s.page,s.size);response.addProperty("request",s.request);response.addProperty("townMember",true);response.addProperty("canBack",!s.history.isEmpty());send.accept(response);
    }
    private void reviewProcurement(Player p,State s,Action action,Consumer<JsonObject> send)throws Exception{
        boolean cancel=action.kind().equals("procurement-review-cancel");require(cancel||action.kind().equals("procurement-review-pay"),"Действие закупки недоступно");
        var c=towny.capture(p);UUID id=UUID.fromString(action.id());procurementCall(p,s,c,"status",new Object[]{id},send,value->{
            var row=ProcurementView.order(value,c.town(),id);require(cancel?row.cancellable():row.payable(System.currentTimeMillis()),"Состояние закупки изменилось. Сверьте её состояние.");
            if(!cancel&&row.state().equals("QUOTE"))require(row.actor().equals(p.getUniqueId()),"Эту смету подготовил другой мэр. Рассчитайте материалы заново.");
            var review=new Review(new Action(cancel?"review-procurement-cancel":"review-procurement-pay",id.toString()),c.town(),c.world(),c.x(),c.z(),row.fingerprint(),cancel?"Подтвердить отмену закупки":row.actionTitle(),row.review(cancel));
            // Refresh/back invalidates this single-use ticket just like all other civic actions.
            String confirmation=s.ticket.issue(review,System.nanoTime());
            send.accept(object("op","review","request",s.request,"token",s.token,"confirmation",confirmation,"title",review.title(),"lines",review.lines()));
        });
    }
    private void executeProcurement(Player p,State s,Review review,Consumer<JsonObject> send)throws Exception{
        boolean cancel=review.action().kind().equals("review-procurement-cancel");require(cancel||review.action().kind().equals("review-procurement-pay"),"Действие закупки недоступно");
        requireProcurement(p,review.town());var c=towny.capture(p);UUID id=UUID.fromString(review.action().id());
        procurementCall(p,s,c,"status",new Object[]{id},send,value->{
            var row=ProcurementView.order(value,review.town(),id);require(row.fingerprint().equals(review.fingerprint()),"Условия закупки изменились. Откройте подтверждение заново.");
            require(cancel?row.cancellable():row.payable(System.currentTimeMillis()),"Состояние закупки изменилось. Сверьте её состояние.");
            procurementCall(p,s,c,cancel?"cancel":"confirm",new Object[]{id},send,result->{
                var saved=ProcurementView.order(result,review.town(),id);require(saved.fingerprint().equals(review.fingerprint()),"Ответ закупки не совпадает с подтверждёнными условиями. Откройте закупки города для сверки.");
                s.route="procurement-order";s.id=id.toString();s.page=0;renderProcurement(p,s,ProcurementView.orderPage(saved,System.currentTimeMillis()),send);
            });
        });
    }
}
