package cc.crafto.ui.client;

import com.google.gson.*;
import cc.crafto.ui.AddonLayout;
import cc.crafto.ui.OrderRarityStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Uses the original live ChestMenu. No synthetic Bukkit clicks or client-defined commands. */
final class AddonScreen extends ContainerScreen {
    private final String viewKey;
    private final Map<String,Integer> roles=new LinkedHashMap<>();
    private final Map<Integer,String> disabled=new HashMap<>();
    private final Map<String,Integer> toolbar=new LinkedHashMap<>();private int statusSlot=-1;
    private final List<Control> toolbarControls=new ArrayList<>();private int toolbarFocus=-1;
    private final List<Integer> adminFooter=new ArrayList<>();private final List<FooterControl> adminFooterControls=new ArrayList<>();private int adminFooterFocus=-1;
    private record FooterControl(int slot,int x,int y,int w,int h){boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
    private final List<Control> navigationControls=new ArrayList<>();
    private final Set<Integer> auxiliary=new LinkedHashSet<>(),hidden=new HashSet<>();
    private boolean moreOpen,detailsOpen,layoutDirty,moreKeyboard,adminMenu;private int moreFocus,moreCount,moreVisible,moreScroll,moreX,moreY,moreW,moreH;private double wheelRemainder;
    private final List<MoreControl> moreControls=new ArrayList<>();
    private record MoreControl(String role,int slot,int x,int y,int w){boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+24;}}
    private record Control(String role,int slot,int x,int y,int w){boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+25;}}
    private final String buildingPreviewKey;
    private final boolean placementMenu,stylePreview,ordersUi,warehouseUi;private int previewSlot=-1;
    private final BuildingPreviewTexture buildingImage=new BuildingPreviewTexture();
    private int previewX,previewY,previewSize;
    private boolean previewUnavailable;
    void buildingPreview(JsonObject body){if(!buildingPreviewKey.isEmpty()&&buildingPreviewKey.equals(str(body,"key",""))){previewUnavailable=!bool(body,"available",true);if(!previewUnavailable)buildingImage.accept(body);}}
    @Override public void removed(){remember();buildingImage.close();super.removed();}
    private final CraftoUIClient client;
    private final JsonObject theme;
    private final String module,commandModule;
    private final String navigation,backLabel;
    private final int preferredWidth,preferredHeight,requestedColumns;
    private int designWidth,designHeight,columns,detailTop,detailHeight;
    private AddonLayout layout;
    private final boolean market;
    private final List<Integer> controls=new ArrayList<>();
    private final List<Card> cards=new ArrayList<>();
    private final List<Integer> slots=new ArrayList<>();
    private boolean inventoryMode,automaticInventory,navigationPress;
    private int page,selected=-1,detailScroll;
    private float scale,left,top;
    private long lastClick;
    private int previousMouseX=Integer.MIN_VALUE,previousMouseY=Integer.MIN_VALUE;
    private record Card(int slot,int x,int y,int w,int h){boolean contains(double mx,double my){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}}
    AddonScreen(CraftoUIClient client,ContainerScreen original,JsonObject options){
        super(original.getMenu(),net.minecraft.client.Minecraft.getInstance().player.getInventory(),original.getTitle());
        buildingPreviewKey=str(options,"buildingPreviewKey","");placementMenu=bool(options,"placementControls",false);stylePreview=bool(options,"stylePreview",false);ordersUi=bool(options,"ordersUi",false);warehouseUi=bool(options,"warehouseUi",false);
        this.client=client;commandModule=str(options,"commandModule","");module=str(options,"module","NeverLand");
        navigation=str(options,"navigation","");backLabel=str(options,"backLabel","В игру");
        viewKey=str(options,"viewKey",commandModule+"/"+title.getString());
        var position=client.viewMemory.get(viewKey);page=position.page();selected=position.selected();detailScroll=position.scroll();
        readPresentation(options);

        preferredWidth=Math.clamp(integer(options,"width",620),280,900);preferredHeight=Math.clamp(integer(options,"height",420),156,650);
        requestedColumns=Math.clamp(integer(options,"columns",3),1,4);
        inventoryMode=warehouseUi||bool(options,"inventory",false)||!menu.getCarried().isEmpty();
        market=str(options,"layout","cards").equals("market")&&menu.getRowCount()==6;
        client.lastTheme=options.getAsJsonObject("theme").deepCopy();
        theme=UiTheme.resolve(client.lastTheme,client.settings.darkTheme);
    }
    void presentation(JsonObject body){if(viewKey.equals(str(body,"viewKey",""))){readPresentation(body);layoutDirty=true;}}
    private void readPresentation(JsonObject options){
        roles.clear();disabled.clear();auxiliary.clear();hidden.clear();toolbar.clear();adminFooter.clear();adminMenu=false;previewSlot=-1;statusSlot=-1;
        if(options.has("presentation")&&options.get("presentation").isJsonObject()){
            var presentation=options.getAsJsonObject("presentation");
            adminMenu=bool(presentation,"adminMenu",false);
            for(String key:List.of("auxiliary","hidden"))if(presentation.has(key))for(var e:presentation.getAsJsonArray(key)){
                int slot=e.getAsInt();if(slot>=0&&slot<menu.getRowCount()*9)(key.equals("auxiliary")?auxiliary:hidden).add(slot);
            }
            if(presentation.has("adminFooter"))for(var e:presentation.getAsJsonArray("adminFooter")){
                int slot=e.getAsInt();if(slot>=0&&slot<menu.getRowCount()*9&&!adminFooter.contains(slot))adminFooter.add(slot);
            }
            int preview=integer(presentation,"previewSlot",-1);if(preview>=0&&preview<menu.getRowCount()*9)previewSlot=preview;
            int status=integer(presentation,"statusSlot",-1);if(status>=0&&status<menu.getRowCount()*9)statusSlot=status;
            if(presentation.has("toolbar"))for(var entry:presentation.getAsJsonObject("toolbar").entrySet()){
                int slot=entry.getValue().getAsInt();if(Set.of("primary","info","help","order","refresh").contains(entry.getKey())&&slot>=0&&slot<menu.getRowCount()*9)toolbar.put(entry.getKey(),slot);
            }
            if(presentation.has("roles"))for(var e:presentation.getAsJsonObject("roles").entrySet()){
                int slot=e.getValue().getAsInt();if(slot>=0&&slot<menu.getRowCount()*9&&Set.of("back","close","previous","next","confirm","cancel").contains(e.getKey()))roles.put(e.getKey(),slot);
            }
            if(presentation.has("disabled"))for(var e:presentation.getAsJsonObject("disabled").entrySet())try{int slot=Integer.parseInt(e.getKey());if(slot>=0&&slot<menu.getRowCount()*9)disabled.put(slot,e.getValue().getAsString());}catch(NumberFormatException ignored){}
        }
    }
    boolean isPlacementMenu(){return placementMenu&&menu.getCarried().isEmpty();}
    private boolean buildingMenu(){return commandModule.equals("NeverLandTownyBuilds");}
    private int color(String key){try{return (int)Long.parseLong(str(theme,key,"#FF343B4A").substring(1),16);}catch(Exception e){return 0xff343b4a;}}
    private String clip(String s,int w){return font.width(s)<=w?s:font.plainSubstrByWidth(s,Math.max(0,w-font.width("…")))+"…";}
    private void box(GuiGraphicsExtractor g,int x,int y,int w,int h,String key){if(w>0&&h>0)CivicStyle.panel(g,x,y,w,h,color(key));}
    private int listWidth(){return layout.gridW();}
    private int rows(){return layout.rows();}
    private int adminFooterColumns(){return Math.clamp((designWidth-24)/180,1,3);}
    private int visibleAdminFooter(){return Math.min(adminFooter.size(),adminFooterColumns()*2);}
    private int pageSize(){return rows()*columns;}
    private int pages(){return Math.max(1,(slots.size()+pageSize()-1)/pageSize());}
    private int headerBackWidth(){return designWidth<400?26:78;}
    private void collect(){
        slots.clear();controls.clear();for(int i=0;i<menu.getRowCount()*9;i++){
            ItemStack item=menu.getSlot(i).getItem();
            if(!item.isEmpty()&&!item.getHoverName().getString().isBlank()&&!navigationSlot(i)&&!hidden.contains(i)&&!adminFooter.contains(i)){if(auxiliary.contains(i)||market&&i>=45)controls.add(i);else slots.add(i);}
        }
        if(commandModule.equals("NeverLandTownyContracts"))slots.sort(Comparator.<Integer>comparingInt(i->{var r=rarity(menu.getSlot(i).getItem());return r==null?-1:r.ordinal();}).reversed().thenComparingInt(i->i));
        page=Math.clamp(page,0,pages()-1);
        if(!slots.contains(selected)&&!controls.contains(selected)&&!adminFooter.contains(selected)&&!roles.containsValue(selected)&&!toolbar.containsValue(selected)){selected=slots.isEmpty()?-1:slots.get(page*pageSize());detailScroll=0;}
    }
    private List<Component> lore(ItemStack item){var value=item.get(DataComponents.LORE);return value==null?List.of():value.lines();}
    private OrderRarityStyle rarity(ItemStack item){if(!commandModule.equals("NeverLandTownyContracts"))return null;for(var line:lore(item)){var r=OrderRarityStyle.lore(line.getString());if(r!=null)return r;}return null;}
    private boolean navigationSlot(int slot){
        if(slot==statusSlot||toolbar.containsValue(slot))return true;
        for(var e:roles.entrySet())if(e.getValue()==slot)return true;return false;
    }
    private boolean actionBar(){return roles.keySet().stream().anyMatch(r->Set.of("confirm","cancel").contains(r));}
    private boolean infoDialog(){return detailsOpen&&toolbar.getOrDefault("info",-2)==selected;}
    private void remember(){client.viewMemory.put(viewKey,page,selected,detailScroll,Map.of());}
    private void geometry(){
        layout=AddonLayout.fit(width,height,preferredWidth,preferredHeight,client.settings.scale,requestedColumns,market,detailsOpen&&!infoDialog()||!buildingPreviewKey.isEmpty()||actionBar());
        designWidth=layout.width();designHeight=layout.height();columns=layout.columns();scale=layout.scale();left=(width-designWidth*scale)/2;top=(height-designHeight*scale)/2;
        if(buildingPreviewKey.isEmpty()&&!actionBar()&&!detailsOpen&&adminFooter.isEmpty()){
            int count=0;for(int i=0;i<menu.getRowCount()*9;i++){var item=menu.getSlot(i).getItem();if(!item.isEmpty()&&!item.getHoverName().getString().isBlank()&&!navigationSlot(i)&&!hidden.contains(i)&&!auxiliary.contains(i)&&!adminFooter.contains(i)&&(!market||i<45))count++;}
            int height=Math.min(preferredHeight,Math.max(180,118+((Math.max(1,count)+columns-1)/columns)*AddonLayout.STRIDE));
            layout=AddonLayout.fit(width,this.height,preferredWidth,height,client.settings.scale,requestedColumns,market,false);
            designWidth=layout.width();designHeight=layout.height();columns=layout.columns();scale=layout.scale();left=(width-designWidth*scale)/2;top=(this.height-designHeight*scale)/2;
        }
        if(actionBar())layout=layout.reserveActionBar();
        if(!toolbar.isEmpty()&&!infoDialog())layout=layout.reserveToolbar();
        if(!adminFooter.isEmpty())layout=layout.reserveAdminFooter((visibleAdminFooter()+adminFooterColumns()-1)/adminFooterColumns());
        designWidth=layout.width();designHeight=layout.height();columns=layout.columns();scale=layout.scale();left=(width-designWidth*scale)/2;top=(this.height-designHeight*scale)/2;
    }
    private void updateInventoryMode(){
        if(warehouseUi){inventoryMode=true;automaticInventory=false;return;}
        // Native clicks are predicted locally, then may be cancelled by the addon.
        // Allow the server response to settle before showing a genuine carried stack.
        if(automaticInventory&&menu.getCarried().isEmpty()){inventoryMode=false;automaticInventory=false;}
        if(!inventoryMode&&!menu.getCarried().isEmpty()&&System.nanoTime()-lastClick>=250_000_000L){inventoryMode=true;automaticInventory=true;}
    }
    @Override protected void init(){
        super.init();
        if(warehouseUi&&width>=300)leftPos=(width-warehouseWidth())/2;
    }
    private int warehouseWidth(){return warehouseUi&&width>=300?Math.min(352,width-16):imageWidth;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        if(inventoryMode){super.extractRenderState(g,mouseX,mouseY,delta);button(g,backLabel,inventoryBackX(),topPos+4,60,22,mouseX,mouseY);return;}
        geometry();collect();cards.clear();navigationControls.clear();toolbarControls.clear();adminFooterControls.clear();
        if(mouseX!=previousMouseX||mouseY!=previousMouseY){moreKeyboard=false;toolbarFocus=-1;adminFooterFocus=-1;}
        double mx=(mouseX-left)/scale,my=(mouseY-top)/scale;
        g.pose().pushMatrix();g.pose().translate(left,top);g.pose().scale(scale,scale);
        box(g,0,0,designWidth,designHeight,"background");
        int headerX=headerBackWidth()+30,headerWidth=designWidth-headerX-50;
        button(g,designWidth<400?"←":"← "+backLabel,16,12,headerBackWidth(),26,mx,my);
        g.text(font,clip(module,headerWidth),headerX,13,color("muted"),false);
        g.text(font,clip(title.getString(),headerWidth),headerX,31,color("text"),false);
        if(!backLabel.equals("В игру")||roles.containsKey("back"))button(g,"×",designWidth-38,12,24,24,mx,my);
        g.fill(16,52,designWidth-16,53,color("border"));
        if(!adminFooter.isEmpty()){
            int rows=(visibleAdminFooter()+adminFooterColumns()-1)/adminFooterColumns();
            g.fill(16,designHeight-57-rows*56,designWidth-16,designHeight-56-rows*56,color("border"));
        }
        int cell=(listWidth()-(columns-1)*8)/columns;
        int start=page*pageSize(),end=Math.min(slots.size(),start+pageSize());
        for(int index=start;index<end;index++){
            int local=index-start,slot=slots.get(index),x=layout.gridX()+(local%columns)*(cell+8),y=layout.gridY()+(local/columns)*AddonLayout.STRIDE;
            Card card=new Card(slot,x,y,cell,AddonLayout.CARD_HEIGHT);cards.add(card);
            if(!moreOpen&&!detailsOpen&&(mouseX!=previousMouseX||mouseY!=previousMouseY)&&card.contains(mx,my)&&selected!=slot){selected=slot;detailScroll=0;}
            ItemStack item=menu.getSlot(slot).getItem();boolean over=card.contains(mx,my)&&!moreOpen&&!detailsOpen;
            box(g,x,y,cell,AddonLayout.CARD_HEIGHT,over?"hover":"button");
            if(over)g.outline(x,y,cell,AddonLayout.CARD_HEIGHT,CivicStyle.mix(color("panel"),color("accent"),50));
            var rarity=rarity(item);if(rarity!=null)g.fill(x,y+4,x+2,y+AddonLayout.CARD_HEIGHT-4,rarity.color);
            g.nextStratum();
            if(adminMenu){
                CivicStyle.text(g,item.getHoverName().getString(),x+10,y+5,cell-20,color(disabled.containsKey(slot)?"muted":"text"));
                var description=lore(item);
                if(!description.isEmpty()){
                    g.pose().pushMatrix();g.pose().translate(x+10,y+22);g.pose().scale(0.78f,0.78f);
                    CivicStyle.wrapped(g,description.getFirst().getString(),0,0,(int)((cell-20)/0.78f),2,color("muted"));
                    g.pose().popMatrix();
                }
            }else{
                g.item(item,x+8,y+13);g.itemDecorations(font,item,x+8,y+13);
                if(rarity!=null){CivicStyle.text(g,item.getHoverName().getString(),x+32,y+7,cell-40,color("text"));CivicStyle.text(g,rarity.label,x+32,y+24,cell-40,rarity.color);}
                else {int lines=Math.min(2,CivicStyle.wrap(item.getHoverName().getString(),cell-40).size());CivicStyle.wrapped(g,item.getHoverName().getString(),x+32,y+(AddonLayout.CARD_HEIGHT-font.lineHeight-Math.max(0,lines-1)*12)/2,cell-40,2,color(disabled.containsKey(slot)?"muted":"text"));}
            }
        }
        if(slots.isEmpty()){
            box(g,layout.gridX(),layout.gridY(),layout.gridW(),rows()*AddonLayout.STRIDE-8,"panel");
            int ty=layout.gridY()+12;String message=market?"В этом разделе пока нет товаров. Проверьте другой рынок или выставьте предложение.":"Здесь пока ничего нет";
            for(var line:font.split(Component.literal(message),layout.gridW()-20)){if(ty+font.lineHeight>layout.gridY()+rows()*AddonLayout.STRIDE-12)break;g.text(font,line,layout.gridX()+10,ty,color("muted"),false);ty+=font.lineHeight+3;}
        }

        boolean previewHovered=!buildingPreviewKey.isEmpty()&&(stylePreview||cards.stream().anyMatch(card->card.slot==previewSlot&&card.contains(mx,my)));
        int dx=layout.detailX(),dy=layout.detailY(),dw=layout.detailW(),dh=layout.detailH();
        if((detailsOpen||previewHovered)&&dw==0){dx=16;dy=58;dw=designWidth-32;dh=designHeight-101-(actionBar()?32:0);}
        if(infoDialog()){dx=16;dy=58;dw=designWidth-32;dh=designHeight-108;}
        detailTop=dy;detailHeight=dh;previewSize=0;
        if(dw>0&&dh>24){
        box(g,dx,dy,dw,dh,"panel");
        previewSize=0;
        if(previewHovered&&dh>=50){
            int area=Math.min(dw-20,Math.max(16,dh-(dh<150?42:133)));previewX=dx+(dw-area)/2;previewY=dy+22;previewSize=area;
            g.text(font,stylePreview?"Облик небольшого города · ↻":"Схема здания · ↻",dx+10,dy+8,color("accent"),false);
            if(buildingImage.ready())buildingImage.draw(g,previewX,previewY,area);else g.text(font,previewUnavailable?"Схема недоступна":"Загрузка схемы…",dx+10,dy+area/2,color("muted"),false);
            g.text(font,clip("Колесо мыши — повернуть",dw-20),dx+10,dy+area+25,color("muted"),false);
            dy+=area+43;dh-=area+43;
        }
        if(selected>=0&&dh>=28){
            ItemStack item=menu.getSlot(selected).getItem();
            var description=ItemInfoCard.layout(item.getHoverName().getString(),lore(item).stream().map(Component::getString).toList(),disabled.getOrDefault(selected,""),dw,true);
            detailScroll=ItemInfoCard.draw(g,description,item,theme,dx,dy,dh,detailScroll,false);
        }else if(dh>=28)g.text(font,"Описание",dx+12,dy+12,color("muted"),false);
        }
        if(actionBar()){
            var names=new ArrayList<String>();
            if(roles.containsKey("cancel"))names.add("cancel");
            for(String role:List.of("confirm"))if(roles.containsKey(role))names.add(role);
            int controlWidth=Math.max(30,(designWidth-32-Math.max(0,names.size()-1)*6)/Math.max(1,names.size()));
            for(int i=0;i<names.size();i++){
                String role=names.get(i);int slot=roles.getOrDefault(role,-1),x=16+i*(controlWidth+6),y=designHeight-75;
                String label=switch(role){case "back"->"Отмена";case "cancel"->slot>=0?menu.getSlot(slot).getItem().getHoverName().getString():"Отмена";case "previous"->"← Страница";case "next"->"Страница →";default->slot>=0?menu.getSlot(slot).getItem().getHoverName().getString():"Подтвердить";};
                navigationControls.add(new Control(role,slot,x,y,controlWidth));button(g,label,x,y,controlWidth,25,mx,my);
                if(slot>=0&&(mouseX!=previousMouseX||mouseY!=previousMouseY)&&inside(mx,my,x,y,controlWidth,25))selected=slot;
                if(disabled.containsKey(slot))g.outline(x,y,controlWidth,25,color("disabled"));
            }
        }
        if(!adminFooter.isEmpty()){
            int cols=adminFooterColumns(),visible=visibleAdminFooter(),rows=(visible+cols-1)/cols;
            int footerCell=(designWidth-32-(cols-1)*8)/cols,firstY=designHeight-49-rows*56;
            for(int i=0;i<visible;i++){
                int slot=adminFooter.get(i),x=16+i%cols*(footerCell+8),y=firstY+i/cols*56;
                var item=menu.getSlot(slot).getItem();if(item.isEmpty())continue;
                var control=new FooterControl(slot,x,y,footerCell,50);adminFooterControls.add(control);
                boolean over=control.contains(mx,my)||adminFooterFocus==i;
                box(g,x,y,footerCell,50,over?"hover":"button");if(over)g.outline(x,y,footerCell,50,color("accent"));
                g.nextStratum();CivicStyle.text(g,item.getHoverName().getString(),x+10,y+8,footerCell-20,color("text"));
                var lore=lore(item);if(!lore.isEmpty()){
                    g.pose().pushMatrix();g.pose().translate(x+10,y+28);g.pose().scale(0.82f,0.82f);
                    CivicStyle.wrapped(g,lore.getFirst().getString(),0,0,(int)((footerCell-20)/0.82f),2,color("muted"));
                    g.pose().popMatrix();
                }
            }
        }
        int fy=designHeight-35;
        if(!toolbar.isEmpty()&&!infoDialog()){
            int buttonWidth=Math.min(150,(designWidth-32-(toolbar.size()-1)*6)/toolbar.size()),index=0;
            for(var entry:toolbar.entrySet()){
                int x=16+index++*(buttonWidth+6),y=designHeight-64,slot=entry.getValue();if(menu.getSlot(slot).getItem().isEmpty())continue;
                toolbarControls.add(new Control(entry.getKey(),slot,x,y,buttonWidth));boolean focused=toolbarFocus==index-1;
                String label=switch(entry.getKey()){case "order"->"Заказать материалы";case "refresh"->"Обновить условия";default->menu.getSlot(slot).getItem().getHoverName().getString();};
                button(g,label,x,y,buttonWidth,25,focused?x+2:mx,focused?y+2:my);
            }
            if(statusSlot>=0)g.text(font,clip(menu.getSlot(statusSlot).getItem().getHoverName().getString(),designWidth-32),16,designHeight-80,color("muted"),false);
        }
        if(!infoDialog()&&(pages()>1||roles.containsKey("previous")||roles.containsKey("next"))){
            button(g,"‹",16,fy,26,22,mx,my);g.text(font,(page+1)+" / "+pages(),51,fy+7,color("muted"),false);button(g,"›",105,fy,26,22,mx,my);
        }else if(!infoDialog())g.text(font,slots.size()+(market?" товаров":" действий"),16,fy+7,color("muted"),false);
        if(infoDialog())button(g,"К заказам",designWidth-112,fy,96,22,mx,my);else button(g,moreOpen?"Скрыть":"Ещё",designWidth-78,fy,62,22,mx,my);
        if(moreOpen)drawMore(g,mx,my);
        g.pose().popMatrix();previousMouseX=mouseX;previousMouseY=mouseY;layoutDirty=false;
        if(!moreOpen&&!detailsOpen&&layout.detailW()==0)for(var c:cards)if(c.contains(mx,my)){if(c.slot!=previewSlot||buildingPreviewKey.isEmpty())tooltip(g,c.slot,mouseX,mouseY);break;}
        if(!moreOpen&&!detailsOpen)for(var c:adminFooterControls)if(c.contains(mx,my)){tooltip(g,c.slot,mouseX,mouseY);break;}
        if(!moreOpen&&!infoDialog()){
            for(var control:toolbarControls)if(control.contains(mx,my)){tooltip(g,control.slot,mouseX,mouseY);break;}
            if(statusSlot>=0&&inside(mx,my,16,designHeight-84,designWidth-32,12))tooltip(g,statusSlot,mouseX,mouseY);
        }
    }
    private void drawMore(GuiGraphicsExtractor g,double mx,double my){
        moreControls.clear();var list=new ArrayList<MoreControl>();
        for(int slot:controls)list.add(new MoreControl("native",slot,0,0,0));
        for(int i=visibleAdminFooter();i<adminFooter.size();i++)list.add(new MoreControl("native",adminFooter.get(i),0,0,0));
        if(selected>=0&&((buildingPreviewKey.isEmpty()&&!actionBar())||layout.detailW()==0))list.add(new MoreControl("description",-1,0,0,0));
        list.add(new MoreControl("inventory",-1,0,0,0));if(client.commandsAvailable&&!buildingMenu())list.add(new MoreControl("commands",-1,0,0,0));
        int count=Math.max(1,Math.min(list.size(),(designHeight-110)/26));moreScroll=Math.clamp(moreScroll,0,Math.max(0,list.size()-count));moreCount=list.size();moreVisible=count;
        moreW=Math.min(250,designWidth-32);moreH=count*26+12;moreX=designWidth-moreW-16;moreY=designHeight-43-moreH;
        g.nextStratum();box(g,moreX,moreY,moreW,moreH,"panel");g.outline(moreX,moreY,moreW,moreH,CivicStyle.mix(color("panel"),color("border"),35));
        for(int i=0;i<count;i++){var entry=list.get(i+moreScroll);var c=new MoreControl(entry.role,entry.slot,moreX+6,moreY+6+i*26,moreW-12);moreControls.add(c);
            String text=switch(c.role){case "description"->detailsOpen?"Скрыть описание":"Описание / условия";case "inventory"->"Предметы · F6";case "commands"->"Все команды · F7";default->menu.getSlot(c.slot).getItem().getHoverName().getString();};
            boolean focused=moreKeyboard&&i+moreScroll==moreFocus;button(g,text,c.x,c.y,c.w,24,moreKeyboard?(focused?c.x+2:-1):mx,moreKeyboard?(focused?c.y+2:-1):my);
        }
        if(list.size()>count)g.fill(moreX+moreW-3,moreY+4,moreX+moreW-2,moreY+moreH-4,color("accent"));
    }
    private void tooltip(GuiGraphicsExtractor g,int slot,int x,int y){
        var item=menu.getSlot(slot).getItem();
        ItemInfoCard.tooltip(g,item,item.getHoverName().getString(),lore(item).stream().map(Component::getString).toList(),disabled.getOrDefault(slot,""),theme,x,y,width,height);
    }
    private void chooseMore(MoreControl c){
        moreOpen=false;layoutDirty=true;
        switch(c.role){case "description"->{detailsOpen=!detailsOpen;detailScroll=0;}case "inventory"->inventoryMode=true;case "commands"->openCommands();default->{selected=c.slot;activate(c.slot,0,false);}}
    }
    private void chooseToolbar(Control control){
        toolbarFocus=-1;
        if(control.role.equals("primary")&&ordersUi&&client.civicAvailable){
            remember();if(minecraft.player!=null)minecraft.player.closeContainer();
            minecraft.gui.setScreen(new CivicScreen(client,null,"mine",true));return;
        }
        if(control.role.equals("info")){selected=control.slot;detailsOpen=true;detailScroll=0;layoutDirty=true;}
        else activate(control.slot,0,false);
    }
    private void flip(int direction){
        if(direction<0&&page==0&&roles.containsKey("previous")){activate(roles.get("previous"),0,false);return;}
        if(direction>0&&page+1>=pages()&&roles.containsKey("next")){activate(roles.get("next"),0,false);return;}
        int next=Math.clamp(page+direction,0,pages()-1);if(next!=page){page=next;layoutDirty=true;}
    }
    private void button(GuiGraphicsExtractor g,String text,int x,int y,int w,int h,double mx,double my){box(g,x,y,w,h,mx>=x&&my>=y&&mx<x+w&&my<y+h?"hover":"button");g.nextStratum();g.centeredText(font,Component.literal(clip(text,w-8)),x+w/2,y+(h-font.lineHeight)/2,color("text"));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        // Minecraft 26.2 calls this before extractRenderState in the same frame.
        // Resolve the mode first, then blur/shade once for both cards and slots.
        updateInventoryMode();
        if(client.settings.blur)extractBlurredBackground(g);
        g.fill(0,0,width,height,color("shade"));
        if(!inventoryMode)return;
        box(g,leftPos-8,topPos-8,warehouseWidth()+16,imageHeight+16,"background");
        if(warehouseUi){
            if(warehouseWidth()>=300){
                g.fill(leftPos+178,topPos+10,leftPos+179,topPos+imageHeight-10,color("border"));
                CivicStyle.icon(g,"materials",leftPos+192,topPos+29,color("accent"));
                CivicStyle.text(g,"Хранилище",leftPos+214,topPos+32,warehouseWidth()-230,color("text"));
                CivicStyle.detailWrapped(g,"Предметы остаются в настоящем инвентаре аддона.",leftPos+192,topPos+61,warehouseWidth()-210,4,color("muted"));
                CivicStyle.detailWrapped(g,"Нажмите на предмет, чтобы переместить его. Shift + клик — быстрое перемещение.",leftPos+192,topPos+119,warehouseWidth()-210,6,color("muted"));
            }
        }
        for(var slot:menu.slots)box(g,leftPos+slot.x-1,topPos+slot.y-1,18,18,"button");
        if(!warehouseUi)g.text(font,menu.getCarried().isEmpty()?"F6 — карточки":"Положите предмет в ячейку",leftPos,topPos-21,color("text"),false);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY){
        g.text(font,clip(title.getString(),imageWidth-14),titleLabelX,titleLabelY,color("text"),false);
        g.text(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,color("muted"),false);
    }
    private boolean inside(double x,double y,int bx,int by,int bw,int bh){return x>=bx&&y>=by&&x<bx+bw&&y<by+bh;}
    private int inventoryBackX(){return warehouseUi&&warehouseWidth()>=300?leftPos+warehouseWidth()-70:Math.max(4,leftPos-68);}
    private void back(){
        remember();
        if(!menu.getCarried().isEmpty())return;
        if(navigation.isEmpty()){if(roles.containsKey("back"))activate(roles.get("back"),0,false);else onClose();}else client.send(Kind.BACK,object("navigation",navigation));
    }
    void performBack(String token,int slot){if(navigation.equals(token)&&!token.isEmpty()&&menu.getCarried().isEmpty()){lastClick=0;activate(slot,0,false);}}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(inventoryMode){if(event.button()==0&&inside(event.x(),event.y(),inventoryBackX(),topPos+4,60,22)){navigationPress=true;back();return true;}automaticInventory=false;return super.mouseClicked(event,doubleClick);}
        if(layoutDirty)return true;geometry();double x=(event.x()-left)/scale,y=(event.y()-top)/scale;
        if(event.button()==0&&(!backLabel.equals("В игру")||roles.containsKey("back"))&&inside(x,y,designWidth-38,12,24,24)){onClose();return true;}
        if(event.button()==0&&inside(x,y,16,12,headerBackWidth(),26)){back();return true;}
        int fy=designHeight-35;
        if(infoDialog()){if(event.button()==0&&inside(x,y,designWidth-112,fy,96,22)){detailsOpen=false;layoutDirty=true;}return true;}
        if(event.button()==0&&inside(x,y,designWidth-78,fy,62,22)){moreOpen=!moreOpen;moreFocus=0;moreScroll=0;layoutDirty=true;return true;}
        if(moreOpen){
            if(event.button()==0)for(var c:moreControls)if(c.contains(x,y)){
                chooseMore(c);return true;
            }
            moreOpen=false;layoutDirty=true;return true;
        }
        if(event.button()==0)for(var control:toolbarControls)if(control.contains(x,y)){
            chooseToolbar(control);return true;
        }
        if(event.button()==0)for(var control:adminFooterControls)if(control.contains(x,y)){
            selected=control.slot;activate(control.slot,0,false);return true;
        }
        if(event.button()==0)for(Control c:navigationControls)if(c.contains(x,y)){activate(c.slot,0,false);return true;}
        if(event.button()==0&&previewSize>0&&inside(x,y,previewX,previewY-22,previewSize,previewSize+43)){buildingImage.rotate();return true;}
        if(event.button()==0&&(pages()>1||roles.containsKey("previous")||roles.containsKey("next"))){
            if(inside(x,y,16,fy,26,22)){flip(-1);return true;}if(inside(x,y,105,fy,26,22)){flip(1);return true;}}
        if(detailsOpen&&layout.detailW()==0)return true;
        if(event.button()!=0&&event.button()!=1)return true;
        for(Card card:cards)if(card.contains(x,y)){selected=card.slot;activate(card.slot,event.button(),(event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0);break;}
        return true;
    }
    void openCommands(){if(!menu.getCarried().isEmpty())return;onClose();client.commands(null,commandModule);}
    private void activate(int slot,int button,boolean shift){
        remember();
        if(disabled.containsKey(slot)){selected=slot;detailScroll=0;if(minecraft.player!=null)minecraft.player.sendOverlayMessage(Component.literal(disabled.get(slot)));return;}
        long now=System.nanoTime();if(now-lastClick<150_000_000L)return;
        if(minecraft.player==null||minecraft.gameMode==null||minecraft.player.containerMenu!=menu||slot<0||slot>=menu.getRowCount()*9)return;
        lastClick=now;
        minecraft.gameMode.handleContainerInput(menu.containerId,slot,button,shift?ContainerInput.QUICK_MOVE:ContainerInput.PICKUP,minecraft.player);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        // A navigation press never begins a slot drag. Vanilla release outside the inventory can drop the carried stack.
        if(navigationPress&&event.button()==0){navigationPress=false;return true;}
        return inventoryMode?super.mouseReleased(event):true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){return navigationPress||!inventoryMode?true:super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(isPlacementMenu()&&client.placementControls.scroll(horizontal,vertical))return true;
        if(inventoryMode)return super.mouseScrolled(x,y,horizontal,vertical);
        if(!moreOpen&&!buildingPreviewKey.isEmpty()&&(stylePreview&&previewSize>0&&inside((x-left)/scale,(y-top)/scale,previewX,previewY,previewSize,previewSize)
            ||cards.stream().anyMatch(card->card.slot==previewSlot&&card.contains((x-left)/scale,(y-top)/scale)))){if(Double.isFinite(vertical)&&vertical!=0)buildingImage.rotate();return true;}
        if(!Double.isFinite(vertical)||vertical==0)return false;double mx=(x-left)/scale,my=(y-top)/scale;
        if(!inside(mx,my,0,0,designWidth,designHeight))return false;if(layoutDirty)return true;
        if(moreOpen){moreScroll=Math.max(0,moreScroll+(vertical<0?1:-1));layoutDirty=true;return true;}
        if(detailsOpen||layout.detailW()>0&&inside(mx,my,layout.detailX(),detailTop,layout.detailW(),detailHeight)){detailScroll=Math.max(0,detailScroll-(int)(Math.signum(vertical)*24));return true;}
        if(Math.signum(wheelRemainder)!=Math.signum(vertical))wheelRemainder=0;wheelRemainder+=vertical;
        if(Math.abs(wheelRemainder)>=1){wheelRemainder=0;flip(vertical<0?1:-1);}return true;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(isPlacementMenu()&&client.placementControls.keyPressed(event))return true;
        if(client.commandsAvailable&&!buildingMenu()&&event.key()==GLFW.GLFW_KEY_F7){openCommands();return true;}
        if(event.key()==GLFW.GLFW_KEY_BACKSPACE){if(moreOpen||detailsOpen){moreOpen=false;detailsOpen=false;layoutDirty=true;}else back();return true;}
        if(event.key()==GLFW.GLFW_KEY_F6){if(!warehouseUi&&menu.getCarried().isEmpty()){inventoryMode=!inventoryMode;automaticInventory=false;}return true;}
        if(inventoryMode)return super.keyPressed(event);
        if(event.key()==GLFW.GLFW_KEY_ESCAPE){if(moreOpen||detailsOpen){moreOpen=false;detailsOpen=false;layoutDirty=true;}else onClose();return true;}
        if(layoutDirty)return true;
        if(!moreOpen&&!detailsOpen&&(event.key()==GLFW.GLFW_KEY_PAGE_UP||event.key()==GLFW.GLFW_KEY_PAGE_DOWN)){flip(event.key()==GLFW.GLFW_KEY_PAGE_UP?-1:1);return true;}
        if(moreOpen){
            if(event.key()==GLFW.GLFW_KEY_TAB||event.key()==GLFW.GLFW_KEY_UP||event.key()==GLFW.GLFW_KEY_DOWN){
                moreKeyboard=true;int direction=event.key()==GLFW.GLFW_KEY_UP||(event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1;
                moreFocus=Math.floorMod(moreFocus+direction,Math.max(1,moreCount));
                if(moreFocus<moreScroll)moreScroll=moreFocus;else if(moreFocus>=moreScroll+moreVisible)moreScroll=moreFocus-moreVisible+1;
                layoutDirty=true;
            }else if(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_SPACE){int i=moreFocus-moreScroll;if(i>=0&&i<moreControls.size())chooseMore(moreControls.get(i));}
            return true;
        }
        if(detailsOpen){if(event.key()==GLFW.GLFW_KEY_PAGE_UP||event.key()==GLFW.GLFW_KEY_PAGE_DOWN)detailScroll=Math.max(0,detailScroll+(event.key()==GLFW.GLFW_KEY_PAGE_DOWN?1:-1)*Math.max(24,detailHeight-20));return true;}
        if(toolbarFocus>=0){
            if(event.key()==GLFW.GLFW_KEY_TAB){int next=toolbarFocus+((event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1);
                if(next<0){toolbarFocus=-1;if(!cards.isEmpty())selected=cards.getLast().slot;}
                else if(next>=toolbarControls.size()){toolbarFocus=-1;moreOpen=true;moreKeyboard=true;moreFocus=0;moreScroll=0;layoutDirty=true;}
                else toolbarFocus=next;return true;
            }
            if(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_SPACE){if(toolbarFocus<toolbarControls.size())chooseToolbar(toolbarControls.get(toolbarFocus));return true;}
        }
        if(adminFooterFocus>=0){
            if(event.key()==GLFW.GLFW_KEY_TAB){int next=adminFooterFocus+((event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1);
                if(next<0){adminFooterFocus=-1;if(!cards.isEmpty())selected=cards.getLast().slot;}
                else if(next>=adminFooterControls.size()){adminFooterFocus=-1;moreOpen=true;moreKeyboard=true;moreFocus=0;moreScroll=0;layoutDirty=true;}
                else adminFooterFocus=next;return true;
            }
            if(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_SPACE){if(adminFooterFocus<adminFooterControls.size())activate(adminFooterControls.get(adminFooterFocus).slot,0,false);return true;}
        }
        if(event.key()==GLFW.GLFW_KEY_TAB&&cards.isEmpty()&&!adminFooterControls.isEmpty()){adminFooterFocus=0;return true;}
        if(event.key()==GLFW.GLFW_KEY_TAB&&!cards.isEmpty()){
            int index=0;for(int i=0;i<cards.size();i++)if(cards.get(i).slot==selected)index=i;
            int direction=(event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1;
            if(direction>0&&index==cards.size()-1){if(!adminFooterControls.isEmpty())adminFooterFocus=0;else if(!toolbarControls.isEmpty())toolbarFocus=0;else{moreOpen=true;moreKeyboard=true;moreFocus=0;moreScroll=0;layoutDirty=true;}return true;}
            selected=cards.get(Math.floorMod(index+direction,cards.size())).slot;detailScroll=0;return true;
        }
        if(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_SPACE){activate(selected,0,(event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0);return true;}
        return true;
    }
}
