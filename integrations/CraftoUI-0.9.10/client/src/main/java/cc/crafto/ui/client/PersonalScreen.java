package cc.crafto.ui.client;

import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Local preferences and display-only snapshots; the server authorizes every destination. */
final class PersonalScreen extends PanelScreen {
    final MenuScreen parent;final CraftoUIClient client;
    private final SettingsScreen settingsParent;
    private int tab,page,ticks;private String query="";private boolean onlyFavorites,returning,suspended;
    private EditBox search;
    PersonalScreen(MenuScreen parent,CraftoUIClient client,int tab){this(parent,client,tab,null);}
    PersonalScreen(SettingsScreen parent,CraftoUIClient client){this(parent.parent,client,1,parent);}
    private PersonalScreen(MenuScreen parent,CraftoUIClient client,int tab,SettingsScreen settingsParent){super("Мой NeverLand",client);this.parent=parent;this.client=client;this.tab=tab;this.settingsParent=settingsParent;if(parent!=null)parent.suspended=true;}
    void updated(){changed();}
    @Override public void tick(){if(++ticks%40==0&&client.personalAvailable&&settingsParent==null)client.send(Kind.DIRECTORY,new JsonObject());synchronizeWidgets();}
    private void saved(){client.settings.save();changed();}
    @Override protected void init(){
        String[] titles={"Поиск и избранное","Панель контрактов",client.townMember?"Мой город":"Приглашения","Пока тебя не было"};
        String[] details={"Найдите раздел или закрепите частое действие","Выберите контракты, которые хотите видеть во время игры","Сведения и доступные действия","Изменения с последнего сохранённого состояния"};
        String[] icons={"search","hud",client.townMember?"city":"inbox","summary"};
        begin(UiTheme.resolve(client.lastTheme,client.settings.darkTheme),titles[tab],details[tab],icons[tab]);
        noTabs();
        // Each destination has its own entry point. Search must not masquerade as
        // settings or a city menu, and this keeps the same screen consistent on resize.
        footer(this::onClose,"",null);
        if(tab==0)quick();else if(tab==1)contracts();else if(tab==2&&!client.townMember)info("Входящие приглашения","Приглашения доступны через городской интерфейс сервера.","inbox",0);else if(tab==3)summary();else city();
        finish();
    }
    private void quick(){
        search=input("Поиск по меню","склад, контракты, постройки…",query,6,6,span-142,80,s->{query=s;page=0;resetScroll();changed();});
        bodyButton(onlyFavorites?"Все разделы":"★ Избранное",span-125,3,117,()->{onlyFavorites=!onlyFavorites;page=0;resetScroll();changed();});
        List<JsonObject> entries=new ArrayList<>();var prefs=client.preferences();
        for(var e:client.directory){String id=str(e,"id","");if((!onlyFavorites||prefs.favorites.contains(id))&&QuickSearch.matches(str(e,"label",""),str(e,"keywords",""),query))entries.add(e);}
        entries.sort(Comparator.<JsonObject>comparingInt(e->{int i=prefs.favorites.indexOf(str(e,"id",""));return i<0?Integer.MAX_VALUE:i;}).thenComparing(e->str(e,"label","")));
        int cols=columns(174,4),cell=cellWidth(cols),capacity=Math.clamp(Math.max(2,(bottom-bodyTop-38)/62)*cols,2,24),pages=Math.max(1,(entries.size()+capacity-1)/capacity);page=Math.clamp(page,0,pages-1);
        for(int i=page*capacity;i<Math.min(entries.size(),(page+1)*capacity);i++){
            var entry=entries.get(i);String id=str(entry,"id",""),label=str(entry,"label","");int index=i-page*capacity,x=index%cols*(cell+10),y=40+index/cols*62;boolean favorite=prefs.favorites.contains(id);
            card(label,"",favorite?"favorite":"search","compact",favorite,x,y,cell-30,52,()->client.launch(id,this));
            var star=card(favorite?"★":"☆","","","control",favorite,x+cell-26,y,26,52,()->{prefs.favorite(id);saved();});
            star.setTooltip(Tooltip.create(Component.literal(favorite?"Убрать из избранного":"Добавить в избранное")));
        }
        if(entries.isEmpty())info(onlyFavorites?"Здесь будет избранное":"Ничего не найдено",client.personalAvailable?(onlyFavorites?"Откройте все разделы и нажмите ☆ рядом с нужным.":"Попробуйте другое название раздела."):"Сервер не предоставил список разделов.",onlyFavorites?"favorite":"search",40);
        status=entries.size()+" разделов · ☆ — закрепить";pager(page,pages,()->flip(-1,pages),()->flip(1,pages));setInitialFocus(search);
    }
    private void flip(int delta,int pages){page=Math.clamp(page+delta,0,pages-1);resetScroll();changed();}
    private void contracts(){
        var prefs=client.preferences();var all=client.contractHud.entries();
        int y=info("Панель контрактов","Выберите, что показывать в игре. Изменения сохраняются только для вас.","hud",0);
        int cols=columns(260,2),w=cellWidth(cols),start=y;
        for(int i=0;i<6;i++){
            int x=i%cols*(w+10),cy=start+i/cols*64;
            switch(i){
                case 0->card("Какие задания показывать",prefs.automatic?"Сейчас видны все взятые задания":"Сейчас видны только отмеченные ниже","orders","setting",true,x,cy,w,58,()->{if(prefs.automatic)prefs.manual(all);else prefs.automatic=true;page=0;saved();}).badge(prefs.automatic?"Все":"Выбранные");
                case 1->card("Панель в игре","Показывать задания поверх мира","hud","setting",client.settings.contractsHud,x,cy,w,58,()->{client.settings.contractsHud=!client.settings.contractsHud;saved();}).badge(client.settings.contractsHud?"Вкл.":"Выкл.");
                case 2->card("Уведомление о награде","Сообщать, когда задание готово к сдаче","inbox","setting",prefs.notifications,x,cy,w,58,()->{prefs.notifications=!prefs.notifications;saved();}).badge(prefs.notifications?"Вкл.":"Выкл.");
                case 3->card("Строки на экране","Нажмите, чтобы показать больше или меньше","hud","setting",false,x,cy,w,58,()->{prefs.rows=prefs.rows%5+1;saved();}).badge(Integer.toString(prefs.rows));
                case 4->card("Размер панели","Нажмите, чтобы подобрать размер","settings","setting",false,x,cy,w,58,()->{prefs.scale=prefs.scale>=1.25f?.7f:Math.min(1.3f,prefs.scale+.1f);saved();}).badge(Math.round(prefs.scale*100)+"%");
                case 5->card("Положение панели","Перетащите панель в удобное место","hud","setting",false,x,cy,w,58,()->{suspended=true;minecraft.gui.setScreen(new HudPositionScreen(this,client));}).badge("Изменить");
                default->{}
            }
        }
        y=start+((6+cols-1)/cols)*64+8;
        y=info("Выбор и порядок","Нажмите на задание, чтобы закрепить его. Стрелки меняют порядок на панели.","orders",y);
        List<ContractHudState.Entry> ordered=new ArrayList<>(prefs.selected(all));for(var e:all)if(ordered.stream().noneMatch(v->v.id().equals(e.id())))ordered.add(e);
        cols=columns(280,2);w=cellWidth(cols);int capacity=Math.max(cols,Math.max(1,(bottom-bodyTop-y-10)/104)*cols),pages=Math.max(1,(ordered.size()+capacity-1)/capacity);page=Math.clamp(page,0,pages-1);
        for(int i=page*capacity;i<Math.min(ordered.size(),(page+1)*capacity);i++){
            var entry=ordered.get(i);int index=i-page*capacity,x=index%cols*(w+10),cy=y+index/cols*104;boolean selected=prefs.automatic||prefs.pinned.contains(entry.id());
            String target=entry.targetKey().isBlank()?"":Component.translatable(entry.targetKey()).getString();
            String detail=(target.isBlank()?"":target+" · ")+entry.progress()+" / "+entry.goal()+(entry.status()==ContractHudState.Status.READY?" · готово к сдаче":"");
            card(entry.title(),detail,"orders","contract",selected,x,cy,w-34,94,()->{prefs.pin(entry.id(),all);saved();}).progress(entry.progress(),entry.goal());
            var visible=prefs.selected(all);int order=visible.indexOf(entry);
            var up=bodyButton("↑",x+w-28,cy+18,28,()->{prefs.move(entry.id(),-1,all);saved();});up.active=selected&&order>0;up.setTooltip(Tooltip.create(Component.literal("Поднять задание на панели")));
            var down=bodyButton("↓",x+w-28,cy+48,28,()->{prefs.move(entry.id(),1,all);saved();});down.active=selected&&order>=0&&order<visible.size()-1;down.setTooltip(Tooltip.create(Component.literal("Опустить задание на панели")));
        }
        if(ordered.isEmpty())info("Нет взятых контрактов","Выберите задание: Работа и торговля → Личные заказы или /orders.","orders",y);
        status="Настройки панели сохраняются автоматически";pager(page,pages,()->flip(-1,pages),()->flip(1,pages));
    }
    private void summary(){
        var prefs=client.preferences();int half=(span-18)/2;
        bodyButton("При входе: "+(prefs.summary?"вкл.":"выкл."),0,0,half,()->{prefs.summary=!prefs.summary;saved();});
        if(client.civicAvailable&&client.townMember)bodyButton("События города",half+10,0,span-half-18,()->client.civic(this,"history"));
        int y=36;
        if(client.summary.isEmpty())y=info("Новых событий нет","Здесь появятся изменения, переданные сервером.","summary",y);
        else for(String line:client.summary)if(!line.isBlank())y=info("",line,"summary",y);
        long ready=client.contractHud.entries().stream().filter(e->e.status()==ContractHudState.Status.READY).count();
        if(ready>0)info("Можно получить награду","Готово личных заказов: "+ready+". Сдача — через /orders.","orders",y);
        status="Сравнение с последним сохранённым состоянием";
    }
    private void city(){
        bodyButton("Обновить сведения",0,0,160,()->client.send(Kind.DASHBOARD,new JsonObject()));int y=36;JsonObject c=client.city;
        if(c.isEmpty()){info("Сведения о городе",client.personalAvailable?"Данные города загружаются…":"Сервер не предоставил сведения о городе.","city",y);return;}
        y=info(str(c,"name","Город"),str(c,"status","")+"\nКазна: "+str(c,"treasury","—")+"\nУчастков занято: "+integer(c,"claimed",0)+" · лимит: "+number(integer(c,"limit",0))+"\nМожно захватить: "+number(integer(c,"remaining",0))+" · жителей: "+integer(c,"residents",0),"city",y);
        for(String key:List.of("builds","resources","contracts")){
            List<String> values=new ArrayList<>();if(c.has(key))for(var value:c.getAsJsonArray(key)){var row=value.getAsJsonObject();String translation=str(row,"targetKey","");values.add(str(row,"text","")+(translation.isEmpty()?"":Component.translatable(translation).getString()));}
            y=info(switch(key){case "builds"->"Активные стройки";case "resources"->"Осталось установить";default->"Городские задания";},values.isEmpty()?"Нет записей":String.join("\n",values),key.equals("builds")?"build":key.equals("resources")?"materials":"goals",y);
        }
        info("Выплаты",bool(c,"contractsKnown",false)?String.format(Locale.ROOT,"За задания: %.2f",c.get("pendingReward").getAsDouble()):"Сведения о выплате недоступны.","treasury",y);status="Данные вашего города";
    }
    private String number(int value){return value<0?"без лимита":Integer.toString(value);}
    void suspendForCommands(){suspended=true;}
    void resume(){suspended=false;changed();}
    void launched(){returning=true;if(parent!=null)parent.suspended=false;}
    @Override public void onClose(){returning=true;if(settingsParent!=null){settingsParent.resume();minecraft.gui.setScreen(settingsParent);}else if(parent!=null){parent.suspended=false;if(parent.isDetached()){minecraft.gui.setScreen(null);client.request(parent.menuId());}else minecraft.gui.setScreen(parent);}else minecraft.gui.setScreen(null);}
    @Override public void removed(){if(!returning&&!suspended&&parent!=null)parent.releaseSession();}
}
