package cc.crafto.ui.client;
import cc.crafto.ui.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import static cc.crafto.ui.Protocol.*;
/** One adaptive renderer for all civic pages. Authoritative operations stay on the server. */
final class CivicScreen extends Screen {
    private final CraftoUIClient client;private final Screen parent;private String route,token="",id="",caption="Город",notice="Загрузка…",confirmation="";
    private JsonObject theme,map,city;private JsonArray tree;private String treeKind="building",treeCategory="Все";private int treeTier;private final Map<String,CivicPageLayout.Box> treeBoxes=new HashMap<>();private List<JsonObject> treeVisible=List.of();private String confirmLabel="Подтвердить";private int cityScroll;private CityDashboardLayout cityLayout;private final Map<net.minecraft.client.gui.components.events.GuiEventListener,CityDashboardLayout.Box> cityFocus=new IdentityHashMap<>();private List<JsonObject> entries=List.of();private List<String> lines=List.of();
    // One sequence across screen instances prevents a delayed reply from matching a newly opened screen.
    private static int sequence;
    private int left,span,bodyTop,bottom,page,pages=1,awaited,textPage,localPages=1,lastCapacity,ticks;
    private boolean initialized,busy,refresh,returning,review,document,serverBack,failed;private long deadline;
    private CivicPageLayout pageLayout;private int pageScroll,pageContent,bodyBase;private boolean specialBody;
    private double wheelRemainder;
    private final Map<net.minecraft.client.gui.components.events.GuiEventListener,CivicPageLayout.Box> pageFocus=new IdentityHashMap<>();
    private final List<PagePanel> pagePanels=new ArrayList<>();
    private record PagePanel(int y,int height,String title,String detail,String icon,boolean hero){}
    private String type="ALL",rarity="ALL",sort="rarity";private long minimum;private boolean ready;
    private final List<Text> text=new ArrayList<>();private record Text(String text,int x,int y,boolean muted,boolean body){Text(String text,int x,int y,boolean muted){this(text,x,y,muted,false);}}
    private final List<Cell> cells=new ArrayList<>();private record Cell(JsonObject data,int x,int y,int size){}
    private final boolean nativeOrders;
    CivicScreen(CraftoUIClient client,Screen parent,String route){this(client,parent,route,false);}
    CivicScreen(CraftoUIClient client,Screen parent,String route,boolean nativeOrders){super(Component.literal("Город и заказы"));this.client=client;this.parent=parent;this.route=route;this.nativeOrders=nativeOrders;caption=CivicStyle.title(route);if(parent instanceof MenuScreen m)m.suspended=true;if(parent instanceof PersonalScreen p)p.suspendForCommands();}
    private Button button(String label,int x,int y,int w,int h,Runnable action){
        Button b=pageLayout==null?addRenderableWidget(new FlatButton(label,x,y,w,h,()->{if(!busy&&!refresh)action.run();},theme)):
            addRenderableWidget(new CivicCardButton(label,"","","","control",true,false,x,y,w,h,specialBody?bodyBase:0,specialBody?bottom:height,()->{if(!busy&&!refresh)action.run();},theme));
        if(specialBody){pageFocus.put(b,new CivicPageLayout.Box(x-left,y-bodyBase,w,h));pageContent=Math.max(pageContent,y-bodyBase+h);b.setY(y-pageScroll);}
        if(font.width(label)>w-12)b.setTooltip(Tooltip.create(Component.literal(label)));return b;
    }
    private void request(String op,JsonObject data){data.addProperty("op",op);data.addProperty("token",token);data.addProperty("request",++sequence);data.addProperty("size",capacity());data.addProperty("cityDashboard",true);awaited=sequence;busy=true;deadline=System.nanoTime()+12_000_000_000L;client.send(Kind.CIVIC,data);refresh=true;}
    private boolean orderCards(){return route.equals("orders")||route.equals("mine");}
    private int financeCount(){return city!=null&&city.has("finance")?Math.min(3,city.getAsJsonObject("finance").getAsJsonArray("metrics").size()):0;}
    private int capacity(){if(route.equals("city"))return CityDashboardLayout.fit(Math.max(120,span-8),bottom-bodyTop,city==null?8:city.getAsJsonArray("shortcuts").size(),0,financeCount(),city==null?4:integer(city,"developmentCount",3)).capacity();return orderCards()?OrderGridLayout.fit(span-8,Math.max(70,bottom-(pageLayout==null?bodyTop:pageLayout.bodyTop())-(route.equals("orders")?56:0))).capacity():(pageLayout==null?Math.clamp((bottom-bodyTop)/43,1,12):pageLayout.capacity());}
    private void open(String target){route=target;caption=CivicStyle.title(target);failed=false;pageScroll=0;wheelRemainder=0;id="";entries=List.of();lines=List.of();map=null;city=null;cityScroll=0;review=false;document=false;textPage=0;request("open",object("route",target,"source",nativeOrders?"native-orders":""));}
    @Override protected void init(){
        if(theme==null)theme=UiTheme.resolve(client.lastTheme,client.settings.darkTheme);
        text.clear();cells.clear();cityFocus.clear();cityLayout=null;pageFocus.clear();pagePanels.clear();treeBoxes.clear();pageLayout=null;pageContent=0;specialBody=false;
        if(!client.townMember&&TownUi.requiresTown(route)){busy=false;open("inbox");}
        pageLayout=CivicPageLayout.fit(width,height);left=pageLayout.left();span=pageLayout.span();bodyTop=pageLayout.navY()+4;bottom=pageLayout.bottom();
        // The dashboard uses the same dialog frame, with its own shortcuts inside the body.
        if(city!=null&&route.equals("city")&&!review&&!document){bodyTop=pageLayout.navY()+4;footer();renderCity();return;}
        footer();
        if(review||document){renderReview();return;}
        if(route.equals("tree")&&tree!=null){renderTree();return;}
        if(orderCards()||route.equals("map")){initSpecialPage();return;}
        if(!initialized){initialized=true;lastCapacity=capacity();open(route);}
        else if(lastCapacity!=capacity()&&!busy&&map==null){lastCapacity=capacity();request("page",object("page",page));}
        if(map!=null||orderCards()){
            if(!lines.isEmpty()){String info=String.join(" · ",lines);text.add(new Text(clip(info,span),left,bodyTop+2,true));bodyTop+=16;}
            if(map!=null)renderMap();else renderOrderCards();return;
        }
        renderPageCards();
    }
    /** Special content keeps its own meaning, but now shares tabs and a clipped scrolling body. */
    private void initSpecialPage(){
        bodyBase=bodyTop;specialBody=true;
        if(route.equals("orders"))filters();
        boolean mapLegendBelow=route.equals("map")&&pageLayout.compact();
        if(!lines.isEmpty()&&!mapLegendBelow){
            String detail=String.join("\n",lines);int h=CivicStyle.wrap(detail,span-36).size()*12+20;
            pagePanels.add(new PagePanel(bodyTop-bodyBase,h,"",detail,"",false));bodyTop+=h+10;
        }
        if(!initialized){initialized=true;lastCapacity=capacity();open(route);}else if(lastCapacity!=capacity()&&!busy&&map==null){lastCapacity=capacity();request("page",object("page",page));}
        if(map!=null)renderMap();else if(orderCards())renderOrderCards();else {pagePanels.add(new PagePanel(0,80,busy?"Загружаем карту…":"Карта недоступна",busy?"Получаем участки города с сервера":notice,"map",false));pageContent=80;}
        if(mapLegendBelow&&!lines.isEmpty()){String detail=String.join("\n",lines);int h=CivicStyle.wrap(detail,span-36).size()*12+20;pagePanels.add(new PagePanel(pageContent+10,h,"",detail,"",false));pageContent+=10+h;}
        specialBody=false;bodyTop=bodyBase;scrollPage(pageScroll);
    }
    private void footer(){
        if(pageLayout==null){
            button("← Назад",left,height-26,78,20,this::back);
            button("Обновить",left+span-88,height-26,88,20,()->{if(review)open("city");else updated();});
            return;
        }
        control(review?"Отмена":"← Назад",left,pageLayout.footerY(),78,22,false,this::back);
        var reload=control("Обновить",left+span-88,pageLayout.footerY(),88,22,false,this::updated);reload.active=!review&&!busy;
        var close=control("×",left+span-20,pageLayout.y()+8,20,20,false,this::onClose);
        close.setTooltip(Tooltip.create(Component.literal("Закрыть меню · Esc")));
    }
    private Button control(String label,int x,int y,int w,int h,boolean primary,Runnable action){
        return addRenderableWidget(new CivicCardButton(label,"","","",primary?"primary":"control",true,false,x,y,w,h,0,height,()->{if(!refresh)action.run();},theme));
    }
    private String rowTitle(JsonObject entry){
        String target=str(entry,"targetKey","");target=target.isBlank()?"":Component.translatable(target).getString();
        String title=str(entry,"title","");return title.isBlank()?target:target.isBlank()?title:title+" · "+target;
    }
    private void renderPageCards(){
        int y=0;boolean emptyGoal=route.equals("goals")&&entries.isEmpty();
        if(!lines.isEmpty()&&!route.equals("inbox")&&!emptyGoal){
            String detail=String.join("\n",lines);int h=CivicStyle.wrap(detail,span-36).size()*12+20;
            pagePanels.add(new PagePanel(y,h,"",detail,"",false));y+=h+12;
        }
        boolean loneInfo=entries.size()==1&&str(entries.getFirst(),"action","").isBlank()&&str(entries.getFirst(),"reason","").isBlank()&&str(entries.getFirst(),"targetKey","").isBlank();
        if(entries.isEmpty()||loneInfo){
            String title=loneInfo?rowTitle(entries.getFirst()):busy?"Загружаем раздел…":failed?"Не удалось загрузить раздел":route.equals("goals")?"Цель пока не выбрана":"Пока нет записей";
            String detail=loneInfo?str(entries.getFirst(),"detail",""):busy?"Получаем актуальные данные с сервера":failed?notice:route.equals("goals")?(lines.isEmpty()?"Мэр может закрепить стройку или городской контракт.":String.join("\n",lines)):"Новые записи появятся здесь.";
            int h=Math.max(pageLayout.compact()?48:160,(pageLayout.compact()?24:96)+(CivicStyle.wrap(title,span-40).size()+CivicStyle.wrap(detail,span-40).size())*12);
            pagePanels.add(new PagePanel(y,h,title,detail,CivicStyle.iconFor(route),true));pageContent=y+h;
        }else{
            for(int i=0;i<entries.size();i++){
                var row=entries.get(i);var box=pageLayout.card(i,entries.size(),y);String key=str(row,"action",""),reason=str(row,"reason","");
                var b=addRenderableWidget(new CivicCardButton(rowTitle(row),str(row,"detail",""),reason,str(row,"title","").equals("Принять")?"check":str(row,"title","").equals("Отклонить")?"close":CivicStyle.iconFor(route),"row",!key.isBlank(),false,box.x(),bodyTop+box.y()-pageScroll,box.width(),box.height(),bodyTop,bottom,()->{
                    if(busy||refresh)return;if(!reason.isBlank()){notice=reason;return;}if(!key.isBlank()){client.civicReturn=this;request("act",object("action",key));}
                },theme));
                b.active=!busy&&(!key.isBlank()||!reason.isBlank());pageFocus.put(b,box);pageContent=Math.max(pageContent,box.bottom());
            }
        }
        if(route.equals("inbox")&&!lines.isEmpty()){
            String status="Ваш статус: "+String.join(" · ",lines);int h=CivicStyle.wrap(status,span-36).size()*12+12;
            pagePanels.add(new PagePanel(pageContent+12,h,"",status,"status",false));pageContent+=12+h;
        }
        // Clamp after resize or a shorter response, then move all body widgets by the same delta.
        int clamped=Math.clamp(pageScroll,0,maxPageScroll()),shift=pageScroll-clamped;pageScroll=clamped;
        for(var key:pageFocus.keySet())if(key instanceof AbstractWidget b)b.setY(b.getY()+shift);
        paging(page,pages,()->{pageScroll=0;request("page",object("page",Math.max(0,page-1)));},()->{pageScroll=0;request("page",object("page",Math.min(pages-1,page+1)));});
    }
    private int maxPageScroll(){return pageLayout==null?0:Math.max(0,pageContent-pageLayout.viewport());}
    private boolean scrollPage(int value){
        int next=Math.clamp(value,0,maxPageScroll());if(next==pageScroll)return false;int shift=pageScroll-next;pageScroll=next;
        for(var key:pageFocus.keySet())if(key instanceof AbstractWidget b)b.setY(b.getY()+shift);return true;
    }
    private CityCardButton cityCard(JsonObject data,String mode,boolean selected,CityDashboardLayout.Box box,Runnable action){
        var b=addRenderableWidget(new CityCardButton(data,mode,selected,left+box.x(),bodyTop+box.y()-cityScroll,box.width(),box.height(),bodyTop,bottom,()->{if(!busy&&!refresh)action.run();},theme));
        String tip=java.util.stream.Stream.of(str(data,"title",str(data,"label","")),str(data,"fullValue",str(data,"value","")),str(data,"detail",str(data,"hint",""))).filter(x->!x.isBlank()).collect(java.util.stream.Collectors.joining("\n"));
        String reason=str(data,"reason","");if(!reason.isBlank())tip+="\n"+reason;
        b.setTooltip(Tooltip.create(Component.literal(tip.strip())));cityFocus.put(b,box);return b;
    }
    private void cityAction(JsonObject row){
        String reason=str(row,"reason","");if(!reason.isBlank()){notice=reason;return;}
        String key=str(row,"action","");if(!key.isEmpty()){client.civicReturn=this;request("act",object("action",key));}
    }
    private void renderCity(){
        var shortcuts=city.getAsJsonArray("shortcuts");String warning=str(city,"sectionNotice","");
        int extra=warning.isBlank()?0:1;
        cityLayout=CityDashboardLayout.fit(span-8,bottom-bodyTop,shortcuts.size(),Math.max(1,entries.size()+extra),financeCount(),integer(city,"developmentCount",3));
        cityScroll=Math.clamp(cityScroll,0,cityLayout.maxScroll());
        int cap=capacity();if(lastCapacity!=cap&&!busy){lastCapacity=cap;request("page",object("page",page));}
        for(int i=0;i<city.getAsJsonArray("metrics").size();i++){var row=city.getAsJsonArray("metrics").get(i).getAsJsonObject();var metric=cityCard(row,"metric",false,cityLayout.metric(i),()->cityAction(row));metric.active=!str(row,"action","").isBlank()&&str(row,"reason","").isBlank()&&!busy;}
        if(financeCount()>0){
            var finance=city.getAsJsonObject("finance");
            for(int i=0;i<financeCount();i++){
                var row=finance.getAsJsonArray("metrics").get(i).getAsJsonObject().deepCopy();
                row.addProperty("action",str(finance,"action",""));row.addProperty("reason",str(finance,"reason",""));
                var button=cityCard(row,"finance",false,cityLayout.finance(i),()->cityAction(row));
                button.active=!str(row,"action","").isBlank()&&str(row,"reason","").isBlank()&&!busy;
            }
        }
        for(int i=0;i<shortcuts.size();i++){var row=shortcuts.get(i).getAsJsonObject();cityCard(row,"shortcut",false,cityLayout.shortcut(i),()->cityAction(row));}
        String section=str(city,"section","active");String[] ids={"active","builds","contracts"};
        String[] titles={"В работе","Здания","Контракты"},counts={"activeCount","buildCount","taskCount"};
        for(int i=0;i<3;i++){String target=ids[i];boolean known=target.equals("builds")?bool(city,"buildsKnown",false):target.equals("contracts")?bool(city,"contractsKnown",false):bool(city,"buildsKnown",false)&&bool(city,"contractsKnown",false);
            String label=titles[i]+" · "+(known?integer(city,counts[i],0):integer(city,counts[i],0)>0?integer(city,counts[i],0)+"+":"?");
            cityCard(object("title",label,"detail",known?"Показать этот раздел":"Часть данных недоступна"),"tab",section.equals(target),cityLayout.tab(i),()->{cityScroll=Math.min(cityLayout.tabsY(),cityLayout.maxScroll());request("section",object("section",target));});
        }
        int offset=0;
        if(!warning.isBlank()){cityCard(object("title","Данные доступны не полностью","detail",warning),"info",false,cityLayout.card(offset++),()->notice=warning);}
        for(var row:entries)cityCard(row,"entry",false,cityLayout.card(offset++),()->cityAction(row));
        if(entries.isEmpty()&&warning.isBlank())cityCard(object("title",str(city,"emptyTitle","Нет записей"),"detail",str(city,"emptyDetail","")),"info",false,cityLayout.card(0),()->{});
        paging(page,pages,()->request("page",object("page",Math.max(0,page-1))),()->request("page",object("page",Math.min(pages-1,page+1))));
    }
    private int depth(JsonObject node,Map<String,JsonObject> all,Map<String,Integer> cache,Set<String> visiting){
        String id=str(node,"id","");if(cache.containsKey(id))return cache.get(id);
        if(!visiting.add(id))return 0;
        int value=0;if(node.has("parents"))for(var parent:node.getAsJsonArray("parents")){
            JsonObject previous=all.get(parent.getAsString());if(previous!=null)value=Math.max(value,1+depth(previous,all,cache,visiting));
        }
        visiting.remove(id);cache.put(id,Math.min(16,value));return cache.get(id);
    }
    private void renderTree(){
        int graphY=span>=420?34:60,top=bodyTop+graphY;
        button("Здания",left,bodyTop+2,76,23,()->{treeKind="building";treeCategory="Все";treeTier=0;pageScroll=0;rebuildWidgets();});
        button("Исследования",left+80,bodyTop+2,98,23,()->{treeKind="research";treeCategory="Все";treeTier=0;pageScroll=0;rebuildWidgets();});
        button("Возможности",left+182,bodyTop+2,98,23,()->{treeKind="content";treeCategory="Все";treeTier=0;pageScroll=0;rebuildWidgets();});
        var all=new LinkedHashMap<String,JsonObject>();for(var element:tree){var node=element.getAsJsonObject();if(str(node,"kind","").equals(treeKind))all.put(str(node,"id",""),node);}
        var categories=new TreeSet<String>();for(var node:all.values())categories.add(str(node,"category","Базовые"));
        if(!treeCategory.equals("Все")&&!categories.contains(treeCategory))treeCategory="Все";
        var choices=new ArrayList<String>();choices.add("Все");choices.addAll(categories);
        button("Ветвь: "+treeCategory+" ›",span>=420?left+288:left,span>=420?bodyTop+2:bodyTop+28,span>=420?span-288:span,23,()->{treeCategory=choices.get((choices.indexOf(treeCategory)+1)%choices.size());treeTier=0;pageScroll=0;rebuildWidgets();});
        var shown=all.values().stream().filter(node->treeCategory.equals("Все")||treeCategory.equals(str(node,"category","Базовые"))).toList();
        treeVisible=shown;Map<String,Integer> depths=new HashMap<>();for(var node:shown)depth(node,all,depths,new HashSet<>());
        int cols=span>=530?3:2,nodeWidth=(span-(cols-1)*10-8)/cols;
        int minDepth=shown.stream().mapToInt(node->depths.getOrDefault(str(node,"id",""),0)).min().orElse(0);
        int maxDepth=shown.stream().mapToInt(node->depths.getOrDefault(str(node,"id",""),0)-minDepth).max().orElse(0);
        treeTier=Math.clamp(treeTier,0,Math.max(0,maxDepth-cols+1));int[] row=new int[cols];
        for(var node:shown){int d=depths.getOrDefault(str(node,"id",""),0)-minDepth-treeTier;if(d<0||d>=cols)continue;
            int x=left+d*(nodeWidth+10),y=graphY+row[d]++*62;
            var box=new CivicPageLayout.Box(x,y,nodeWidth,56);treeBoxes.put(str(node,"id",""),box);
            String key=str(node,"action","");String detail=str(node,"status","")+" · "+str(node,"detail","");
            boolean unique=bool(node,"unique",false);
            var b=addRenderableWidget(new CivicCardButton(str(node,"title",""),detail,"",unique?"unique":treeKind.equals("research")?"planner":treeKind.equals("content")?"materials":"build",unique?"tree-special":"row",!key.isBlank(),false,x,bodyTop+y-pageScroll,nodeWidth,56,top,bottom,()->{if(!busy&&!refresh&&!key.isBlank()){client.civicReturn=this;request("act",object("action",key));}},theme));
            b.active=!busy&&!key.isBlank();String requirements="";if(node.has("requires"))for(var requirement:node.getAsJsonArray("requires"))requirements+="\n"+requirement.getAsString();
            b.setTooltip(Tooltip.create(Component.literal(str(node,"title","")+"\n"+str(node,"status","")+requirements+"\n"+str(node,"detail",""))));
            pageFocus.put(b,box);
        }
        pageContent=graphY+Arrays.stream(row).max().orElse(0)*62;
        if(treeBoxes.isEmpty()){
            String reason=treeKind.equals("research")?(lines.size()>1?lines.get(1):"Исследования откроются после строительства необходимых научных зданий."):treeKind.equals("content")?"Новые возможности появятся после развития торговых зданий.":"Проверьте каталог зданий и права просмотра.";
            pagePanels.add(new PagePanel(graphY+2,72,all.isEmpty()?"Пока нет доступных ветвей":"Ветвь ещё закрыта",all.isEmpty()?reason:"Продолжайте развивать необходимые здания.","planner",false));pageContent=graphY+74;
        }
        if(maxDepth>=cols){button("‹ Ветви",left+span-154,bottom+2,72,18,()->{treeTier=Math.max(0,treeTier-1);pageScroll=0;rebuildWidgets();}).active=treeTier>0;
            button("Ветви ›",left+span-76,bottom+2,76,18,()->{treeTier=Math.min(maxDepth-cols+1,treeTier+1);pageScroll=0;rebuildWidgets();}).active=treeTier<maxDepth-cols+1;}
        // Reuse the ordinary footer for a direct route to the building plan.
        if(!entries.isEmpty()){var row0=entries.getFirst();String key=str(row0,"action","");button("Смета и план",left,bottom+2,105,18,()->{if(!key.isBlank())request("act",object("action",key));});}
        scrollPage(pageScroll);
    }
    private boolean scrollCity(int value){
        if(cityLayout==null)return false;int next=Math.clamp(value,0,cityLayout.maxScroll());if(next==cityScroll)return false;int shift=cityScroll-next;cityScroll=next;
        for(var key:cityFocus.keySet())if(key instanceof CityCardButton b)b.setY(b.getY()+shift);return true;
    }
    @Override public void setFocused(net.minecraft.client.gui.components.events.GuiEventListener target){
        super.setFocused(target);
        var pageBox=pageFocus.get(target);if(pageBox!=null&&pageLayout!=null)scrollPage(pageLayout.scrollToReveal(pageScroll,pageContent,pageBox.y(),pageBox.bottom()));
        var box=cityFocus.get(target);if(box!=null&&cityLayout!=null)scrollCity(cityLayout.scrollToReveal(cityScroll,box));
    }
    private void renderOrderCards(){
        var grid=OrderGridLayout.fit(span-8,Math.max(70,bottom-(pageLayout==null?bodyTop:pageLayout.bodyTop())-(route.equals("orders")?56:0)));
        for(int i=0;i<entries.size();i++){
            int x=left+(i%grid.columns())*(grid.cellWidth()+OrderGridLayout.GAP),y=bodyTop+(i/grid.columns())*grid.stride();
            var row=entries.get(i);String target=str(row,"targetKey","");String title=str(row,"title","");if(!target.isBlank())title+=" · "+Component.translatable(target).getString();
            String key=str(row,"action",""),reason=str(row,"reason",""),detail=str(row,"detail","");
            var b=addRenderableWidget(new OrderCardButton(title,str(row,"rarity",""),route.equals("mine")?"":str(row,"reward",""),route.equals("mine")?detail:str(row,"kind",detail),reason,str(row,"icon",""),x,y,grid.cellWidth(),grid.cellHeight(),()->{if(!busy&&!refresh){client.civicReturn=this;request("act",object("action",key));}},theme));
            if(pageLayout!=null){b.clipTo(bodyBase,bottom);pageFocus.put(b,new CivicPageLayout.Box(x-left,y-bodyBase,grid.cellWidth(),grid.cellHeight()));pageContent=Math.max(pageContent,y-bodyBase+grid.cellHeight());b.setY(y-pageScroll);}
            b.active=!key.isEmpty()&&reason.isBlank()&&!busy;b.setTooltip(Tooltip.create(Component.literal(title+"\n"+detail+(reason.isBlank()?"":"\n"+reason))));
        }
        if(entries.isEmpty()){
            String title=busy?"Загружаем заказы…":"Нет заказов по выбранным фильтрам";
            pagePanels.add(new PagePanel(bodyTop-bodyBase,70,title,busy?"Получаем данные с сервера":"Измените тип, редкость или минимальную награду.","orders",false));pageContent=Math.max(pageContent,bodyTop-bodyBase+70);
        }
        boolean body=specialBody;specialBody=false;
        paging(page,pages,()->request("page",object("page",Math.max(0,page-1))),()->request("page",object("page",Math.min(pages-1,page+1))));specialBody=body;
    }
    private String rootRoute(){return switch(route){case "offer","order","mine"->"orders";case "invite-town","invite-company"->"inbox";case "plot"->"map";case "build","contract"->"city";case "history-entry"->"history";default->route;};}
    private void filters(){
        int w=(span-8)/3;String[] types={"ALL","DELIVERY","MINING","HUNTING","FISHING"},rarities={"ALL","COMMON","UNCOMMON","RARE","EPIC","LEGENDARY"};
        button("Тип: "+label(type),left,bodyTop,w,20,()->{type=next(types,type);filter();});
        button("Редкость: "+label(rarity),left+w+4,bodyTop,w,20,()->{rarity=next(rarities,rarity);filter();});
        button("Порядок: "+label(sort),left+2*(w+4),bodyTop,span-2*(w+4),20,()->{sort=next(new String[]{"rarity","reward","ready","title"},sort);filter();});
        bodyTop+=24;
        button(ready?"Есть предметы: да":"Есть предметы: любые",left,bodyTop,(span-4)/2,20,()->{ready=!ready;filter();});
        button("Награда от: "+minimum/100,left+(span+4)/2,bodyTop,span-(span+4)/2,20,()->{long[] values={0,10000,100000,1000000};int i=0;while(i<values.length-1&&values[i]!=minimum)i++;minimum=values[(i+1)%values.length];filter();});bodyTop+=24;
    }
    private static String next(String[] values,String current){int i=Arrays.asList(values).indexOf(current);return values[(i+1)%values.length];}
    private static String label(String value){return switch(value){case "ALL"->"все";case "DELIVERY"->"поставка";case "MINING"->"добыча";case "HUNTING"->"охота";case "FISHING"->"рыбалка";case "COMMON"->"обычные";case "UNCOMMON"->"необычные";case "RARE"->"редкие";case "EPIC"->"эпические";case "LEGENDARY"->"легендарные";case "rarity"->"редкость ↓";case "reward"->"награда";case "ready"->"готовность";case "title"->"название";default->value;};}
    private void filter(){textPage=0;pageScroll=0;request("filter",object("type",type,"rarity",rarity,"sort",sort,"minimum",minimum,"ready",ready));}
    private void paging(int current,int total,Runnable previous,Runnable next){
        if(total<=1)return;
        button("‹",left+span-95,bottom+3,25,17,()->{if(current>0)previous.run();}).active=current>0&&!busy;
        button("›",left+span-25,bottom+3,25,17,()->{if(current+1<total)next.run();}).active=current+1<total&&!busy;
        text.add(new Text((current+1)+"/"+total,left+span-65,bottom+7,true));
    }
    private void renderReview(){
        var wrapped=new ArrayList<String>();for(String line:lines)wrapped.addAll(CivicStyle.wrap(line,span-28));
        int area=bottom-(review?30:0)-bodyTop,offset=Math.min(10,Math.max(2,(area-9)/2));
        int capacity=Math.max(1,(area-offset-3)/12);localPages=Math.max(1,(wrapped.size()+capacity-1)/capacity);textPage=Math.clamp(textPage,0,localPages-1);
        for(int i=textPage*capacity;i<Math.min(wrapped.size(),(textPage+1)*capacity);i++)text.add(new Text(wrapped.get(i),left+14,bodyTop+offset+(i%capacity)*12,false));
        if(review){var confirm=control(confirmLabel,left,bottom-23,span,22,true,()->{if(busy)return;String value=confirmation;confirmation="";request("confirm",object("confirmation",value));});confirm.active=!busy&&!confirmation.isEmpty()&&textPage==localPages-1;}
        paging(textPage,localPages,()->{textPage--;rebuildWidgets();},()->{textPage++;rebuildWidgets();});
    }
    private void renderMap(){
        int size=Math.max(10,Math.min(24,(bottom-bodyTop-26)/9));int grid=9*size,x=left+(span-grid)/2,y=bodyTop;
        int cx=integer(map,"cx",0),cz=integer(map,"cz",0);
        for(var e:map.getAsJsonArray("cells")){var cell=e.getAsJsonObject();int px=x+(integer(cell,"x",0)-cx+4)*size,py=y+(integer(cell,"z",0)-cz+4)*size;cells.add(new Cell(cell,px,py,size));
            var b=button(bool(cell,"here",false)?"•":"",px,py,size-1,size-1,()->request("act",object("action",str(cell,"action",""))));b.setAlpha(0);b.setTooltip(Tooltip.create(Component.literal(integer(cell,"x",0)+", "+integer(cell,"z",0)+" · "+str(cell,"owner",""))));
        }
        pageContent=Math.max(pageContent,y-bodyBase+grid+28);
        int w=(span-16)/5;for(int i=0;i<Math.min(5,entries.size());i++){var e=entries.get(i);button(str(e,"title",""),left+i*(w+4),y+grid+4,w,20,()->request("act",object("action",str(e,"action",""))));}
    }
    void accessChanged(){refresh=true;}
    void updated(){if(!busy&&!review)request("refresh",new JsonObject());}
    void receive(JsonObject b){
        if(returning||integer(b,"request",0)!=awaited)return;busy=false;if(b.has("theme")){client.lastTheme=b.getAsJsonObject("theme").deepCopy();theme=UiTheme.resolve(client.lastTheme,client.settings.darkTheme);}
        switch(str(b,"op","")){
            case "view" -> {failed=false;if(!id.equals(str(b,"id",""))||!route.equals(str(b,"route",route))){textPage=0;pageScroll=0;}document=bool(b,"document",false);serverBack=bool(b,"canBack",false);token=str(b,"token","");route=str(b,"route",route);id=str(b,"id","");caption=str(b,"title","");notice=str(b,"notice","");page=integer(b,"page",0);pages=integer(b,"pages",1);var out=new ArrayList<JsonObject>();for(var e:b.getAsJsonArray("rows"))out.add(e.getAsJsonObject());entries=List.copyOf(out);lines=readLines(b);map=b.has("map")?b.getAsJsonObject("map"):null;city=b.has("city")?b.getAsJsonObject("city"):null;tree=b.has("tree")?b.getAsJsonArray("tree"):null;review=false;confirmation="";}
            case "review" -> {caption=str(b,"title","");String targetKey=str(b,"targetKey","");lines=readLines(b).stream().map(line->line.replace("{craftoui:target}",targetKey.isBlank()?"цель заказа":Component.translatable(targetKey).getString())).toList();confirmLabel=str(b,"confirmLabel","Подтвердить");confirmation=str(b,"confirmation","");document=false;review=true;textPage=0;notice="Подтверждение действует 30 секунд.";map=null;}
            case "error" -> {failed=true;notice=str(b,"message","Ошибка");review=false;confirmation="";}
            case "commands" -> {returning=true;client.commands(this,str(b,"module",""));return;}
            default -> {}
        }refresh=true;
    }
    private static List<String> readLines(JsonObject b){var out=new ArrayList<String>();if(b.has("lines"))for(var line:b.getAsJsonArray("lines"))out.add(line.getAsString());return List.copyOf(out);}
    /** Packets can arrive between a client tick and frame extraction. Commit layout before either draws it. */
    private void refreshWidgets(){if(refresh){refresh=false;rebuildWidgets();}}
    @Override public void tick(){ticks++;if(ticks%200==0&&!busy&&!review&&!failed&&!token.isEmpty())updated();if(busy&&System.nanoTime()>deadline){busy=false;failed=true;notice="Нет ответа. Обновите экран.";confirmation="";refresh=true;}refreshWidgets();}
    private String clip(String value,int size){return font.width(value)<=size?value:font.plainSubstrByWidth(value,Math.max(0,size-8))+"…";}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        if(client.settings.blur)extractBlurredBackground(g);
        g.fill(0,0,width,height,UiTheme.color(theme,pageLayout==null?"background":"shade"));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        refreshWidgets();
        g.nextStratum();
        if(pageLayout!=null)drawPage(g);else g.centeredText(font,clip(caption,span),width/2,12,UiTheme.color(theme,"text"));
        if(cityLayout!=null){
            g.enableScissor(left,bodyTop,left+span,bottom);
            g.text(font,clip(str(city,"role","")+" · состояние вашего города",span-12),left+2,bodyTop+3-cityScroll,UiTheme.color(theme,"muted"),false);
            if(financeCount()>0)g.text(font,clip("Финансы · "+str(city.getAsJsonObject("finance"),"period","Текущая неделя"),span-12),left+2,bodyTop+cityLayout.financeY()-14-cityScroll,UiTheme.color(theme,"muted"),false);
            g.text(font,"Развитие города",left+2,bodyTop+cityLayout.shortcutsY()-14-cityScroll,UiTheme.color(theme,"muted"),false);
            g.text(font,"Управление и события",left+2,bodyTop+cityLayout.managementY()-14-cityScroll,UiTheme.color(theme,"muted"),false);
            g.disableScissor();
            if(cityLayout.maxScroll()>0){int track=bottom-bodyTop,thumb=Math.max(14,track*track/cityLayout.contentHeight()),y=bodyTop+(track-thumb)*cityScroll/cityLayout.maxScroll();g.fill(left+span-3,bodyTop,left+span,bottom,UiTheme.color(theme,"panel"));g.fill(left+span-3,y,left+span,y+thumb,UiTheme.color(theme,"accent"));}
        }
        for(var row:text)g.text(font,row.text(),row.x(),row.y()-(row.body()?pageScroll:0),UiTheme.color(theme,row.muted()?"muted":"text"),false);
        int mapScroll=pageLayout==null?0:pageScroll;
        if(map!=null&&pageLayout!=null)g.enableScissor(left,bodyTop,left+span,bottom);
        for(var cell:cells){int color=switch(str(cell.data(),"status","free")){case "own"->0xFF2E7867;case "other"->0xFF96545B;default->0xFF4B5863;};g.fill(cell.x(),(cell.y()-mapScroll),cell.x()+cell.size()-1,(cell.y()-mapScroll)+cell.size()-1,color);if(bool(cell.data(),"here",false))g.outline(cell.x(),(cell.y()-mapScroll),cell.size()-1,cell.size()-1,0xFFFFFFFF);}
        for(var cell:cells){String town=str(cell.data(),"town","");if(town.isEmpty())continue;
            for(var other:cells){if(town.equals(str(other.data(),"town","")))continue;int dx=other.x()-cell.x(),dy=other.y()-cell.y(),size=cell.size();
                if(dy==0&&dx==size)g.fill(cell.x()+size-2,(cell.y()-mapScroll),cell.x()+size,(cell.y()-mapScroll)+size,0xFFE4D7AE);
                if(dy==0&&dx==-size)g.fill(cell.x(),(cell.y()-mapScroll),cell.x()+1,(cell.y()-mapScroll)+size,0xFFE4D7AE);
                if(dx==0&&dy==size)g.fill(cell.x(),(cell.y()-mapScroll)+size-2,cell.x()+size,(cell.y()-mapScroll)+size,0xFFE4D7AE);
                if(dx==0&&dy==-size)g.fill(cell.x(),(cell.y()-mapScroll),cell.x()+size,(cell.y()-mapScroll)+1,0xFFE4D7AE);
            }
        }
        if(map!=null&&pageLayout!=null)g.disableScissor();
        String status=notice.isBlank()?((review||document?localPages:pages)>1?"Колёсико — прокрутка и страницы":cityLayout!=null&&cityLayout.maxScroll()>0||maxPageScroll()>0?"Прокрутка: колёсико / Page Down":""):notice;
        g.text(font,clip(busy?"Загрузка…":status,span-100),left,bottom+7,UiTheme.color(theme,"muted"),false);g.nextStratum();super.extractRenderState(g,mouseX,mouseY,delta);
        if(cityLayout==null&&pagePanels.isEmpty()&&!lines.isEmpty()&&!review&&!document&&mouseX>=left&&mouseX<left+span&&mouseY>=bodyTop-16&&mouseY<bodyTop&&font.width(String.join(" · ",lines))>span)g.setTooltipForNextFrame(font,tooltipLines(String.join("\n",lines)),mouseX,mouseY);
        if(!busy&&font.width(notice)>span-100&&mouseX>=left&&mouseX<left+Math.max(0,span-100)&&mouseY>=bottom+7&&mouseY<bottom+16)g.setTooltipForNextFrame(font,tooltipLines(notice),mouseX,mouseY);
    }
    private void drawPage(GuiGraphicsExtractor g){
        var layout=pageLayout;int bg=UiTheme.color(theme,"background"),panel=UiTheme.color(theme,"panel"),muted=UiTheme.color(theme,"muted"),accent=UiTheme.color(theme,"accent"),textColor=UiTheme.color(theme,"text");
        CivicStyle.panel(g,layout.x(),layout.y(),layout.width(),layout.height(),CivicStyle.mix(bg,panel,30));
        int titleY=layout.y()+14,titleX=left+28;
        CivicStyle.icon(g,review?"check":CivicStyle.iconFor(route),left+2,layout.y()+13,CivicStyle.inkAccent(theme));

        if(layout.compact())CivicStyle.text(g,caption,titleX,titleY,span-62,textColor);
        else {g.pose().pushMatrix();g.pose().translate(titleX,titleY);g.pose().scale(1.15f,1.15f);CivicStyle.text(g,caption,0,0,(int)((span-62)/1.15f),textColor);g.pose().popMatrix();}
        if(!layout.compact())CivicStyle.detail(g,review?"Проверьте последствия перед выполнением действия":CivicStyle.subtitle(route),titleX,layout.y()+31,span-38,muted);
        int separator=bodyTop-2;
        g.fill(left,separator,left+span,separator+1,CivicStyle.mix(panel,UiTheme.color(theme,"border"),30));
        if(review||document)CivicStyle.panel(g,left,bodyTop,span,Math.max(1,bottom-bodyTop-(review?30:0)),panel);
        if(tree!=null&&route.equals("tree")&&!review){
            g.enableScissor(left,bodyTop+(span>=420?34:60),left+span,bottom);
            for(var node:treeVisible){var child=treeBoxes.get(str(node,"id",""));if(child==null||!node.has("parents"))continue;
                for(var parent:node.getAsJsonArray("parents")){var source=treeBoxes.get(parent.getAsString());if(source==null)continue;
                    int x1=source.right(),x2=child.x(),y1=bodyTop+source.y()-pageScroll+28,y2=bodyTop+child.y()-pageScroll+28;
                    if(x2<=x1)continue;int middle=(x1+x2)/2,c=CivicStyle.mix(panel,accent,55);
                    g.fill(x1,y1,middle+1,y1+2,c);g.fill(middle,Math.min(y1,y2),middle+2,Math.max(y1,y2)+2,c);g.fill(middle,y2,x2,y2+2,c);
                }
            }
            g.disableScissor();
        }
        g.enableScissor(left,bodyTop,left+span,bottom);
        for(var p:pagePanels){
            int y=bodyTop+p.y()-pageScroll,w=span-8;if(!p.icon().equals("status"))CivicStyle.panel(g,left,y,w,p.height(),panel);g.nextStratum();
            if(p.hero()){
                int center=left+w/2;
                if(!layout.compact()){
                    CivicStyle.panel(g,center-24,y+16,48,42,CivicStyle.mix(panel,accent,10));
                    g.pose().pushMatrix();g.pose().translate(center-18,y+19);g.pose().scale(2,2);CivicStyle.icon(g,p.icon(),0,0,CivicStyle.inkAccent(theme));g.pose().popMatrix();
                }
                int yy=y+(layout.compact()?10:72);for(String line:CivicStyle.wrap(p.title(),w-32)){g.centeredText(font,line,center,yy,textColor);yy+=12;}
                yy+=9;for(String line:CivicStyle.wrap(p.detail(),w-36)){g.centeredText(font,line,center,yy,muted);yy+=12;}
            }else{
                if(!p.icon().equals("status"))g.fill(left,y+9,left+2,y+p.height()-9,CivicStyle.mix(panel,accent,55));
                int yy=y+10;if(!p.title().isBlank()){CivicStyle.text(g,p.title(),left+14,yy,w-28,textColor);yy+=20;}for(String line:CivicStyle.wrap(p.detail(),w-28)){g.text(font,line,left+14,yy,muted,false);yy+=12;}
            }
        }
        g.disableScissor();
        if(maxPageScroll()>0){int track=layout.viewport(),thumb=Math.max(12,track*track/pageContent),y=bodyTop+(track-thumb)*pageScroll/maxPageScroll();g.fill(left+span-3,bodyTop,left+span,bottom,panel);g.fill(left+span-3,y,left+span,y+thumb,accent);}
        g.fill(left,bottom+26,left+span,bottom+27,CivicStyle.mix(panel,UiTheme.color(theme,"border"),30));
    }
    private List<net.minecraft.util.FormattedCharSequence> tooltipLines(String value){
        var lines=new ArrayList<>(font.split(Component.literal(value),Math.max(40,Math.min(280,width-24))));
        int limit=Math.max(1,(height-24)/(font.lineHeight+2));
        if(lines.size()>limit){lines=new ArrayList<>(lines.subList(0,limit));lines.set(limit-1,Component.literal("…").getVisualOrderText());}
        return lines;
    }
    // Do not execute old widgets with a new response token before their replacement is visible.
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){return !refresh&&super.mouseClicked(e,doubleClick);}
    @Override public boolean keyPressed(KeyEvent e){
        if(e.key()==256){onClose();return true;}if(refresh)return true;if(e.key()==259){back();return true;}
        if(maxPageScroll()>0){if(e.key()==266)return scrollPage(pageScroll-pageLayout.viewport())||turnPage(1);if(e.key()==267)return scrollPage(pageScroll+pageLayout.viewport())||turnPage(-1);if(e.key()==268)return scrollPage(0);if(e.key()==269)return scrollPage(maxPageScroll());}
        if(cityLayout!=null){if(e.key()==266)return scrollCity(cityScroll-(bottom-bodyTop))||turnPage(1);if(e.key()==267)return scrollCity(cityScroll+(bottom-bodyTop))||turnPage(-1);if(e.key()==268)return scrollCity(0);if(e.key()==269)return scrollCity(cityLayout.maxScroll());}
        if(e.key()==266)return turnPage(1);if(e.key()==267)return turnPage(-1);return super.keyPressed(e);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){
        if(!Double.isFinite(v)||v==0)return false;
        if(x<left||x>=left+span||y<bodyTop||y>=bottom+24){wheelRemainder=0;return false;}
        if(refresh)return true;
        if(y<bottom&&((maxPageScroll()>0&&scrollPage(pageScroll+(v<0?32:-32)))||(cityLayout!=null&&scrollCity(cityScroll+(v<0?32:-32))))){wheelRemainder=0;return true;}
        return turnPage(v);
    }
    private boolean turnPage(double v){
        boolean local=review||document;int current=local?textPage:page,total=local?localPages:pages;
        if(total<=1)return false;if(busy)return true;
        int direction=v<0?1:-1,next=current+direction;
        if(next<0||next>=total){wheelRemainder=0;return true;}
        if(Math.signum(wheelRemainder)!=Math.signum(v))wheelRemainder=0;
        wheelRemainder+=v;if(Math.abs(wheelRemainder)<1)return true;wheelRemainder=0;
        if(local){textPage=next;rebuildWidgets();}else {pageScroll=0;request("page",object("page",next));}return true;
    }
    private void back(){
        if(busy){busy=false;awaited=++sequence;}
        if(review){review=false;confirmation="";request("refresh",new JsonObject());}
        else if(serverBack)request("back",new JsonObject());
        else if(nativeOrders&&route.equals("mine"))returnNativeOrders();
        else leave(parent);
    }
    /** The root Esc returns to its entry point; a nested Esc follows server-owned history. */
    @Override public void onClose(){back();}
    void forcedClose(){leave(null);}
    private void returnNativeOrders(){
        returning=true;busy=false;confirmation="";awaited=++sequence;client.civicReturn=null;
        minecraft.gui.setScreen(null);
        client.send(Kind.CIVIC,object("op","return-native-orders","request",awaited));
    }
    private void leave(Screen target){
        returning=true;busy=false;confirmation="";awaited=++sequence;client.civicReturn=null;
        client.send(Kind.CIVIC,object("op","close","request",awaited));
        if(parent instanceof MenuScreen m){m.suspended=false;if(target==null)m.releaseSession();}
        if(parent instanceof PersonalScreen p){p.resume();if(target==null&&p.parent!=null)p.parent.releaseSession();}
        if(target instanceof MenuScreen m&&m.isDetached()){minecraft.gui.setScreen(null);client.request(m.menuId());}else minecraft.gui.setScreen(target);
    }
    MenuScreen ownerMenu(){return parent instanceof MenuScreen m?m:parent instanceof PersonalScreen p?p.parent:null;}
    void nativeEntered(){if(parent instanceof MenuScreen m)m.detach();if(parent instanceof PersonalScreen p&&p.parent!=null)p.parent.detach();}
    void resume(){returning=false;busy=false;request("refresh",new JsonObject());}
    @Override public void removed(){if(!returning)client.civicReturn=this;}
    @Override public boolean isPauseScreen(){return false;}
}
