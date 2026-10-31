package cc.crafto.ui.client;

import cc.crafto.ui.*;import com.google.gson.*;import net.minecraft.client.*;import net.minecraft.client.gui.*;import net.minecraft.client.gui.components.*;import net.minecraft.client.gui.screens.*;import net.minecraft.client.input.*;import net.minecraft.network.chat.Component;
import java.util.*;import java.nio.file.*;import java.lang.reflect.*;import java.awt.image.*;import java.awt.RenderingHints;import javax.imageio.*;
import static cc.crafto.ui.Protocol.*;

public class PanelProbe {
 static int checks,renders;
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 static Field field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static Object get(Object o,String name)throws Exception{return field(o,name).get(o);}static void set(Object o,String name,Object value)throws Exception{field(o,name).set(o,value);}
 static void draw(Screen s){{var frame=new GuiGraphicsExtractor();s.extractBackground(frame,-1,-1,0);s.extractRenderState(frame,-1,-1,0);}}
 static AbstractWidget widget(Screen s,String prefix){return s.widgets.stream().filter(b->b.label.startsWith(prefix)).findFirst().orElseThrow(()->new AssertionError("No widget "+prefix));}
 static void click(Screen s,AbstractWidget b){s.setFocused(b);int y=b.y+3;try{String key=s instanceof PanelScreen?"bodyWidgets":"pageFocus";if(((Map<?,?>)get(s,key)).containsKey(b))y=Math.max(y,(Integer)get(s,"bodyTop")+3);}catch(Exception ignored){}s.mouseClicked(new MouseButtonEvent(b.x+3,y),false);}
 static String uid(int i){return new UUID(0,i+1).toString();}
 static CraftoUIClient client(JsonObject theme){
  var c=new CraftoUIClient();c.lastTheme=theme;
  List<JsonObject> entries=new ArrayList<>();String[] labels={"Личные заказы","Городской склад","Постройки","Профессии","Компании","Политики города","Рынок","Казна","Друзья","Достижения","Городской совет","Экспедиции","Настройки","История города"};
  for(int i=0;i<labels.length;i++)entries.add(object("id","entry-"+i,"label",labels[i],"keywords",labels[i]));c.directory=entries;c.preferences.favorites.add("entry-1");
  c.contractHud.entries=List.of(new ContractHudState.Entry(uid(0),"Поставка древесины",96,128,true,ContractHudState.Status.ACTIVE,"block.minecraft.oak_log","DELIVERY"),new ContractHudState.Entry(uid(1),"Камень для ратуши",64,64,true,ContractHudState.Status.READY,"block.minecraft.stone","MINING"),new ContractHudState.Entry(uid(2),"Снабжение северного поселения с длинным названием",11,64,false,ContractHudState.Status.ACTIVE));
  c.summary=List.of("Завершено строительство ратуши: уровень 3.","Город расширил территорию. Новых участков: 4.","В казну поступило 2 500 монет.");
  c.city=object("name","NeverLand","status","Житель","treasury","25 000","claimed",40,"limit",68,"remaining",28,"residents",12,"contractsKnown",true,"pendingReward",1200,"builds",List.of(object("text","Ратуша: 640 / 1000")),"resources",List.of(object("text","Не хватает: 128 · ","targetKey","block.minecraft.oak_log")),"contracts",List.of(object("text","Собрать ресурсы: 96 / 128")));return c;
 }
 static MenuScreen parent(CraftoUIClient c){var p=new MenuScreen();p.theme=c.lastTheme;return p;}
 static void size(Screen s,int w,int h){s.width=w;s.height=h;s.initialize();}
 static JsonObject form(){return object("id","invite","label","Пригласить в город","root","town","prefix","invite","fields",List.of(object("id","player","label","Ник игрока","type","player","required",true),object("id","role","label","Должность","type","choice","required",true,"choices",List.of("builder","assistant"))));}
 static CommandScreen commands(CraftoUIClient c,int w,int h,String scenario)throws Exception{
  var s=new CommandScreen(c,parent(c),scenario.equals("commands")?"":"Towny");size(s,w,h);int req=(Integer)get(s,"awaited");var list=new JsonArray();
  if(scenario.equals("commands"))for(String title:List.of("Город и жители","Компании","Городские постройки","Политики и должности","Казна и бюджет","Контракты"))list.add(object("id","module-"+title,"label",title,"type","module","detail","InternalPluginName"));
  else for(String title:List.of("Пригласить в город","Назначить на должность","Передать средства","Посмотреть список жителей"))list.add(object("id","form-"+title,"label",title,"type","form","detail","/town invite"));
  s.receive(object("op","list","request",req,"token","commands-token","module",scenario.equals("commands")?"":"Towny","moduleLabel","Город и жители","entries",list,"count",list.size(),"page",0,"pages",1));draw(s);
  if(!scenario.equals("commands")&&!scenario.equals("command-list")){
   s.receive(object("op","form","request",get(s,"awaited"),"id","Towny:invite","form",form(),"usage","/town invite <player> <role>"));draw(s);
   ((EditBox)get(s,"input")).setValue("sasha34");draw(s);
   if(scenario.equals("command-review")||scenario.equals("command-feedback")){
    s.receive(object("op","review","request",get(s,"awaited"),"confirmation","ticket-123","command","/town invite sasha34 builder"));draw(s);
    if(scenario.equals("command-feedback")){s.receive(object("op","dispatched","request",get(s,"awaited"),"message","Действие передано аддону"));s.feedback(Component.literal("Приглашение отправлено игроку sasha34."));draw(s);}
   }else {set(s,"suggestRequest",77);s.receive(object("op","suggest","request",77,"field",0,"entries",List.of("sasha34","alex","northern_builder","town_guard","player_five","player_six")));draw(s);}
  }return s;
 }
 static CivicScreen civic(CraftoUIClient c,int w,int h,String route)throws Exception{
  var s=new CivicScreen(c,parent(c),route);size(s,w,h);JsonArray rows=new JsonArray();var payload=object("op","view","request",get(s,"awaited"),"route",route,"title",CivicStyle.title(route),"token","civic-token","id","","rows",rows,"lines",List.of(),"page",0,"pages",1);
  if(route.equals("map")){
   JsonArray cells=new JsonArray();for(int z=-4;z<=4;z++)for(int x=-4;x<=4;x++)cells.add(object("x",x,"z",z,"status",Math.abs(x)<=1&&Math.abs(z)<=2?"own":x>2&&z<0?"other":"free","town",Math.abs(x)<=1?"NeverLand":"","owner",Math.abs(x)<=1?"NeverLand":"Свободно","action","plot:"+x+":"+z,"here",x==0&&z==0));
   payload.add("map",object("cx",0,"cz",0,"cells",cells));payload.add("lines",JSON.toJsonTree(List.of("Зелёный — ваш город · красный — другой город · серый — свободно")));
   for(String title:List.of("←","↑","К игроку","↓","→"))rows.add(object("title",title,"action",title));
  }else {
   String[] rarities={"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY"},items={"wooden_sword","stone_sword","iron_sword","diamond_sword","netherite_sword"};
   for(int i=0;i<5;i++)rows.add(object("title","Заказ охотника · "+(i+1),"detail","Победить мобов: "+(i+1)*5,"rarity",rarities[i],"reward","250","kind","Охота","icon","minecraft:"+items[i],"action","order-"+i,"reason",""));payload.addProperty("pages",2);
  }
  payload.add("rows",rows);
  for(int i=0;i<3;i++){payload.addProperty("request",(Integer)get(s,"awaited"));s.receive(payload);draw(s);if(!(Boolean)get(s,"busy"))break;}return s;
 }
 static void townCreation(CraftoUIClient c,String theme,int w,int h,Path out)throws Exception{
  c.townMember=false;var screen=new CommandScreen(c,parent(c),"Towny","Towny/create-town");size(screen,w,h);
  check(str(c.sent.getLast(),"op","").equals("open")&&str(c.sent.getLast(),"form","").equals("Towny/create-town"),"create button opens the named form directly");
  var form=object("id","create-town","label","Создать город","root","town","prefix","new","fields",List.of(object("id","name","label","Название города — без пробелов","type","token","required",true)));
  screen.receive(object("op","form","request",get(screen,"awaited"),"token","town-session","id","Towny/create-town","form",form,"usage","Город создаётся на текущем месте; условия проверяет Towny."));draw(screen);
  ((EditBox)get(screen,"input")).setValue("Речной_берег");draw(screen);var prepare=widget(screen,"Проверить действие");screen.setFocused(prepare);draw(screen);click(screen,prepare);
  check(str(c.sent.getLast(),"token","").equals("town-session")&&c.sent.getLast().getAsJsonObject("values").get("name").getAsString().equals("Речной_берег")&&!c.sent.getLast().has("command"),"creation submits a name without a client-selected command");
  screen.receive(object("op","review","request",get(screen,"awaited"),"confirmation","first-ticket","command","/town new Речной_берег"));draw(screen);var first=widget(screen,"Подтвердить");screen.setFocused(first);draw(screen);click(screen,first);
  screen.receive(object("op","dispatched","request",get(screen,"awaited"),"message","Команда передана Towny"));
  screen.receive(object("op","review","native",true,"request",get(screen,"awaited"),"confirmation","towny-ticket","command","Создание города: подтвердите условия и стоимость Towny"));draw(screen);
  check((Boolean)get(screen,"nativeReview"),"Towny cost confirmation remains inside the menu");render(screen,out.resolve("town-create-review-"+theme+"-"+w+"x"+h+".png"));
  var button=widget(screen,"Подтвердить");screen.setFocused(button);draw(screen);int sent=c.sent.size();click(screen,button);click(screen,button);
  check(c.sent.size()==sent+1&&str(c.sent.getLast(),"confirmation","").equals("towny-ticket"),"native Towny confirmation is submitted once with its original ticket");
  screen.receive(object("op","review","native",true,"request",get(screen,"awaited"),"confirmation","cancel-ticket","command","Подтверждение Towny"));draw(screen);screen.onClose();
  check(str(c.sent.getLast(),"op","").equals("cancel"),"Back cancels the pending Towny operation");
 }
 static Screen setup(CraftoUIClient c,int w,int h,String scenario)throws Exception{
  if(scenario.startsWith("command"))return commands(c,w,h,scenario);
  if(List.of("map","orders","mine").contains(scenario))return civic(c,w,h,scenario);
  if(scenario.startsWith("settings")){var s=new SettingsScreen(parent(c),c);size(s,w,h);if(scenario.equals("settings-hud")){set(s,"section",1);s.rebuildWidgets();}return s;}
  int tab=scenario.startsWith("contract")?1:scenario.equals("summary")?3:scenario.equals("city-legacy")?2:0;
  var s=new PersonalScreen(parent(c),c,tab);size(s,w,h);
  if(scenario.equals("search-empty")){((EditBox)get(s,"search")).setValue("нет_такого_раздела");draw(s);}
  if(scenario.equals("favorites")){set(s,"onlyFavorites",true);s.rebuildWidgets();}
  if(scenario.equals("contract-options")){set(s,"panelOptions",true);s.rebuildWidgets();}
  return s;
 }
 static void render(Screen s,Path path)throws Exception{
  var image=new BufferedImage(s.width*2,s.height*2,BufferedImage.TYPE_INT_ARGB);var g2=image.createGraphics();g2.scale(2,2);g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);{var frame=new GuiGraphicsExtractor(g2);s.extractBackground(frame,-1,-1,0);s.extractRenderState(frame,-1,-1,0);}g2.dispose();ImageIO.write(image,"PNG",path.toFile());renders++;
 }
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[0]),themes=Path.of(args[1]);List<String> scenarios=List.of("search","search-empty","favorites","contracts","contract-options","summary","city-legacy","settings","settings-hud","commands","command-list","command-form","command-review","command-feedback","map","orders","mine");
  for(String themeName:List.of("dark","classic"))for(int[] size:new int[][]{{829,563},{320,240},{320,180}})townCreation(client(JsonParser.parseString(Files.readString(themes.resolve(themeName+".json"))).getAsJsonObject()),themeName,size[0],size[1],out);
  for(String theme:List.of("dark","classic"))for(int[] size:new int[][]{{871,447},{520,340},{320,240},{320,180}})for(String scenario:scenarios){
   var c=client(JsonParser.parseString(Files.readString(themes.resolve(theme+".json"))).getAsJsonObject());var s=setup(c,size[0],size[1],scenario);draw(s);render(s,out.resolve("panel-"+theme+"-"+scenario+"-"+size[0]+"x"+size[1]+".png"));
   var footer=s.widgets.stream().filter(b->b.label.equals("← Назад")||b.label.equals("В меню")||b.label.equals("В игру")).toList();check(!footer.isEmpty()&&footer.stream().allMatch(b->b.x>=0&&b.y>=0&&b.x+b.w<=s.width&&b.y+b.h<=s.height),theme+" "+scenario+" "+size[0]+"x"+size[1]+" fixed navigation in bounds");
  }
  JsonObject theme=JsonParser.parseString(Files.readString(themes.resolve("dark.json"))).getAsJsonObject();var c=client(theme);var s=(PersonalScreen)setup(c,871,447,"search");
  var search=(EditBox)get(s,"search");search.setValue("склад");search.setCursorPosition(2);draw(s);check(((EditBox)get(s,"search")).getCursorPosition()==2,"search redraw preserves insertion cursor");check(s.widgets.stream().anyMatch(b->b.label.startsWith("Городской склад"))&&s.widgets.stream().noneMatch(b->b.label.startsWith("Личные заказы")),"search filters known menu labels");
  click(s,widget(s,"Городской склад"));check(c.launches.equals(List.of("entry-1")),"search launches exact server directory id");
  s=(PersonalScreen)setup(c,871,447,"search");click(s,s.widgets.stream().filter(b->b.label.equals("★")).findFirst().orElseThrow());draw(s);check(!c.preferences.favorites.contains("entry-1")&&c.settings.saves>0,"favorite toggle persists without navigating");
  s=(PersonalScreen)setup(c,320,180,"contracts");var contract=widget(s,"Поставка древесины");click(s,contract);draw(s);check(!c.preferences.automatic&&!c.preferences.pinned.contains(uid(0)),"contract pin switches to manual selection");
  s=(PersonalScreen)setup(c,320,180,"contract-options");int before=c.sent.size();s.mouseScrolled(s.left+5,s.bodyTop+5,0,-1);check(s.scroll>0&&c.sent.size()==before,"local preference scrolling causes no server requests");
  var p=parent(c);var settings=new SettingsScreen(p,c);size(settings,320,180);boolean blur=c.settings.blur;click(settings,widget(settings,"Размытие"));draw(settings);check(c.settings.blur!=blur,"offscreen setting becomes reachable through keyboard focus");settings.onClose();check(Minecraft.getInstance().gui.screen==p&&!p.suspended,"settings back resumes original menu");
  var command=commands(c,871,447,"command-form");var input=(EditBox)get(command,"input");input.setCursorPosition(2);input.insertText("X");draw(command);check(((EditBox)get(command,"input")).getValue().equals("saXsha34")&&((EditBox)get(command,"input")).getCursorPosition()==3,"command edits preserve value and cursor through rebuild");
  set(command,"suggestRequest",99);command.receive(object("op","suggest","request",98,"field",0,"entries",List.of("stale")));check(((List<?>)get(command,"suggestions")).isEmpty(),"outdated autocomplete response is discarded");
  set(command,"suggestRequest",100);command.receive(object("op","suggest","request",100,"field",1,"entries",List.of("wrong-field")));check(((List<?>)get(command,"suggestions")).isEmpty(),"suggestions for another field are discarded");
  command=commands(c,871,447,"command-form");click(command,widget(command,"Следующее поле"));draw(command);check((Integer)get(command,"fieldIndex")==1,"next field advances wizard");
  input=(EditBox)get(command,"input");input.setValue("builder");draw(command);before=c.sent.size();click(command,widget(command,"Проверить действие"));check(c.sent.size()==before+1&&str(c.sent.getLast(),"op","").equals("prepare")&&c.sent.getLast().getAsJsonObject("values").get("role").getAsString().equals("builder"),"prepare sends values only, no fabricated command");
  command=commands(c,871,447,"command-review");before=c.sent.size();var confirm=widget(command,"Подтвердить");click(command,confirm);click(command,confirm);check(c.sent.size()==before+1&&str(c.sent.getLast(),"confirmation","").equals("ticket-123"),"confirmation ticket is sent exactly once");
  command=commands(c,871,447,"command-review");before=c.sent.size();command.onClose();draw(command);check(c.sent.size()==before+1&&str(c.sent.getLast(),"op","").equals("cancel")&&get(command,"stage").equals("form"),"back from review cancels server ticket");
  command=commands(c,871,447,"commands");command.receive(object("op","list","request",(Integer)get(command,"awaited"),"token","new-token","module","Towny","moduleLabel","New page","entries",new JsonArray(),"count",0));before=c.sent.size();command.mouseClicked(new MouseButtonEvent(80,155),false);check(c.sent.size()==before,"response before frame blocks stale command controls");draw(command);
  command=commands(c,320,180,"command-review");var bottom=widget(command,"Подтвердить");command.setFocused(bottom);check(bottom.y+bottom.h<=command.bottom&&!bottom.isMouseOver(bottom.x+3,command.bottom+1),"command action stays clipped and keyboard reachable at large GUI scale");render(command,out.resolve("panel-command-review-320x180-scrolled.png"));
  var map=civic(c,320,180,"map");var pan=map.widgets.stream().filter(b->b.label.equals("К игроку")).findFirst().orElseThrow();map.setFocused(pan);before=c.sent.size();map.mouseClicked(new MouseButtonEvent(pan.x+3,pan.y+3),false);check(c.sent.size()==before+1&&str(c.sent.getLast(),"action","").equals("К игроку"),"map pan sends original server action after scrolling");
  var orders=civic(c,320,180,"orders");var cards=orders.widgets.stream().filter(b->b instanceof OrderCardButton).toList();check(cards.size()==5,"large GUI scale retains every order card instead of dropping it");var last=cards.getLast();orders.setFocused(last);check(last.y+last.h<=(Integer)get(orders,"bottom")&&!last.isMouseOver(last.x+2,(Integer)get(orders,"bottom")+1),"order clipping leaves footer clickable");render(orders,out.resolve("panel-orders-320x180-scrolled.png"));
  var icon=(net.minecraft.world.item.ItemStack)get(last,"icon");check(icon.id.equals("minecraft:netherite_sword")&&icon.glint,"legendary item and glint flag preserved");
  before=c.sent.size();click(orders,last);check(c.sent.size()==before+1&&str(c.sent.getLast(),"action","").equals("order-4"),"visible order sends its own server action id");
  var hud=new HudPositionScreen(s,c);size(hud,320,180);click(hud,widget(hud,"Слева"));check(c.preferences.x==0&&c.preferences.y==.5f,"HUD anchor controls retain saved positioning");
  s=(PersonalScreen)setup(c,871,447,"search");s.charTyped(new CharacterEvent("с"));s.charTyped(new CharacterEvent("к"));s.keyPressed(new KeyEvent(259));draw(s);check(((EditBox)get(s,"search")).getValue().equals("с"),"queued typing and backspace survive redraw before the next frame");
  c.townMember=false;s=(PersonalScreen)setup(c,871,447,"search");check(s.widgets.stream().noneMatch(b->b.label.equals("Город"))&&s.widgets.stream().anyMatch(b->b.label.equals("Приглашения")),"personal navigation replaces city with invitations for townless players");
  System.out.println("PASS "+checks+" panel workflow checks; "+renders+" production-screen renders with explicit AWT/input/transport doubles; no game runtime");
 }
}
