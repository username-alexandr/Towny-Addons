package cc.crafto.ui.client;
import cc.crafto.ui.*;
import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.*;
import static cc.crafto.ui.Protocol.*;

public final class CraftoUIClient implements ClientModInitializer {
    static final Logger LOG=LoggerFactory.getLogger("CraftoUI");
    static CraftoUIClient INSTANCE;
    ClientSettings settings;
    final cc.crafto.ui.MenuViewMemory viewMemory=new cc.crafto.ui.MenuViewMemory();
    private boolean ready,pendingOpen;
    private String home="main";
    private int ticks,attempts;
    private Object connection;
    private KeyMapping open,hudToggle,hudNext;
    ContractHud contractHud;
    PlacementControls placementControls;
    final UiNotifications notifications=new UiNotifications();
    java.util.List<JsonObject> directory=java.util.List.of();
    JsonObject city=new JsonObject();java.util.List<String> summary=java.util.List.of("Сводка загружается…");
    boolean commandsAvailable,civicAvailable;
    CivicScreen civicReturn;
    void civic(net.minecraft.client.gui.screens.Screen parent,String route){Minecraft.getInstance().gui.setScreen(new CivicScreen(this,parent,route));}
    void commands(net.minecraft.client.gui.screens.Screen parent,String module){if(commandsAvailable)Minecraft.getInstance().gui.setScreen(new CommandScreen(this,parent,module));}
    boolean townMember=true;
    boolean personalAvailable;private String directoryToken="";private KeyMapping personalKey;
    PersonalPreferences preferences(){
        var mc=Minecraft.getInstance();String server=mc.getCurrentServer()==null?"local":mc.getCurrentServer().ip;
        String player=mc.player==null?"":mc.player.getUUID().toString();
        return settings.profile(server.toLowerCase(java.util.Locale.ROOT)+"/"+player);
    }
    void personal(MenuScreen parent,int tab){
        if(tab==2&&civicAvailable){civic(parent,"city");return;}
        Minecraft.getInstance().gui.setScreen(new PersonalScreen(parent,this,tab));
        if(personalAvailable){send(Kind.DIRECTORY,new JsonObject());send(Kind.DASHBOARD,new JsonObject());}
    }
    void launch(String id,PersonalScreen screen){
        if(!personalAvailable||directoryToken.isEmpty()){send(Kind.DIRECTORY,new JsonObject());return;}
        send(Kind.LAUNCH,object("id",id,"token",directoryToken));
        // Keep the parent alive until the authorized server response; a denied request leaves this screen usable.
        send(Kind.DIRECTORY,new JsonObject());
    }
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message,overlay)->{if(!overlay&&Minecraft.getInstance().gui.screen() instanceof CommandScreen screen)screen.feedback(message);});
        INSTANCE=this;settings=ClientSettings.load();contractHud=new ContractHud(this);placementControls=new PlacementControls(this);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,Identifier.parse("craftoui:contracts"),(g,d)->{contractHud.extract(g);notifications.extract(g);placementControls.extract(g);});
        PayloadTypeRegistry.serverboundPlay().register(WirePayload.TYPE,WirePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WirePayload.TYPE,WirePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(WirePayload.TYPE,(payload,context)->{
            Object expected=context.client().getConnection();
            context.client().execute(()->{if(context.client().getConnection()==expected)receive(payload.bytes());});
        });
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->reset(handler));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset(null));
        open=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftoui.open",GLFW.GLFW_KEY_M,KeyMapping.Category.register(Identifier.parse("craftoui:menu"))));
        hudToggle=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftoui.contracts",GLFW.GLFW_KEY_H,open.getCategory()));
        hudNext=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftoui.contracts_next",GLFW.GLFW_KEY_J,open.getCategory()));
        personalKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftoui.personal",GLFW.GLFW_KEY_K,open.getCategory()));
        placementControls.register(open.getCategory());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.getConnection()==null||client.player==null)return;
            if(connection!=client.getConnection())reset(client.getConnection());
            while(hudToggle.consumeClick()){settings.contractsHud=!settings.contractsHud;settings.save();}
            while(hudNext.consumeClick()){contractHud.next();}
            while(personalKey.consumeClick()){if(ready&&client.gui.screen()==null)personal(null,0);}
            placementControls.tick();
            ticks++;
            if(ready&&civicAvailable&&ticks%200==0)send(Kind.CIVIC,object("op","poll"));
            if(!ready&&attempts<3&&ticks%60==1&&ClientPlayNetworking.canSend(WirePayload.TYPE)){attempts++;send(Kind.HELLO,object("min",VERSION,"max",VERSION,"features",java.util.List.of("native-menus-v1","contracts-hud-v1","menu-back-v1","personal-v1","commands-v1","city-screen-v1","civic-v1","building-preview-v1","placement-controls-v1")));}
            while(open.consumeClick()){
                if(client.gui.screen() instanceof MenuScreen menu){menu.onClose();continue;}
                if(client.gui.screen()!=null)continue;
                if(ready)request();
                else if(ClientPlayNetworking.canSend(WirePayload.TYPE)){pendingOpen=true;send(Kind.HELLO,object("min",VERSION,"max",VERSION,"features",java.util.List.of("native-menus-v1","contracts-hud-v1","menu-back-v1","personal-v1","commands-v1","city-screen-v1","civic-v1","building-preview-v1","placement-controls-v1")));}
                else client.player.sendOverlayMessage(Component.literal("На этом сервере CraftoUI недоступен."));
            }
        });
    }
    JsonObject lastTheme=new JsonObject();
    private void reset(Object handler){viewMemory.clear();townMember=true;lastTheme=new JsonObject();ready=false;pendingOpen=false;ticks=0;attempts=0;connection=handler;home="main";contractHud.clear();placementControls.clear();notifications.clear();directory=java.util.List.of();city=new JsonObject();summary=java.util.List.of("Сводка загружается…");directoryToken="";personalAvailable=false;commandsAvailable=false;civicAvailable=false;civicReturn=null;}
    String hudNextKey(){return hudNext.getTranslatedKeyMessage().getString();}
    private void request(){request(home);}
    void request(String id){send(Kind.REQUEST,object("menu",id));}
    void send(Kind kind,JsonObject body){
        if(Minecraft.getInstance().getConnection()!=null&&ClientPlayNetworking.canSend(WirePayload.TYPE))ClientPlayNetworking.send(new WirePayload(encode(kind,body)));
    }
    private void receive(byte[] bytes){
        Minecraft mc=Minecraft.getInstance();
        try {
            Message message=decode(bytes);JsonObject b=message.body();
            if(b.has("townMember")){boolean before=townMember;townMember=bool(b,"townMember",true);if(before!=townMember){if(mc.gui.screen() instanceof PersonalScreen ps)ps.updated();if(mc.gui.screen() instanceof CivicScreen cs)cs.accessChanged();}}
            if(message.kind()==Kind.WELCOME){
                ready=bool(b,"accepted",false)&&integer(b,"protocol",0)==VERSION;personalAvailable=ready&&b.has("features")&&b.getAsJsonArray("features").contains(new JsonPrimitive("personal-v1"));home=str(b,"home","main");commandsAvailable=ready&&b.has("features")&&b.getAsJsonArray("features").contains(new JsonPrimitive("commands-v1"));
                civicAvailable=ready&&b.has("features")&&b.getAsJsonArray("features").contains(new JsonPrimitive("civic-v1"));
                placementControls.supported(ready&&b.has("features")&&b.getAsJsonArray("features").contains(new JsonPrimitive("placement-controls-v1")));
                if(ready&&pendingOpen){pendingOpen=false;request();}
                if(!ready){
                    pendingOpen=false;contractHud.clear();
                    if(mc.gui.screen() instanceof MenuScreen m){m.detach();mc.gui.setScreen(null);}
                    else if(mc.gui.screen() instanceof SettingsScreen s){s.parent.detach();mc.gui.setScreen(null);}
                    if(mc.player!=null)mc.player.sendOverlayMessage(Component.literal(str(b,"message","CraftoUI: несовместимая версия.")));
                }
                return;
            }
            if(!ready||mc.player==null)return;
            if(message.kind()==Kind.CIVIC){if(str(b,"op","").equals("resume")){if(civicReturn!=null){var target=civicReturn;target.resume();mc.gui.setScreen(target);}else civic(null,"city");}else if(str(b,"op","").equals("show")){var current=mc.gui.screen();civic(current instanceof MenuScreen||current instanceof PersonalScreen?current:null,str(b,"route","city"));}else if(str(b,"op","").equals("notification")){if(preferences().notifications)notifications.add(str(b,"title",""),str(b,"message",""));}else if(mc.gui.screen() instanceof CivicScreen civic){if(str(b,"op","").equals("refresh"))civic.updated();else civic.receive(b);}return;}
            if(message.kind()==Kind.COMMANDS){if(commandsAvailable){if(str(b,"op","").equals("resume-native")){var screen=new CommandScreen(this,null,"Towny");screen.resumeNative(b);mc.gui.setScreen(screen);}else if(str(b,"op","").equals("show")){var current=mc.gui.screen();String module=str(b,"module",""),form=str(b,"form","");if(current instanceof AddonScreen addon&&module.isBlank()&&form.isBlank()){addon.openCommands();}else mc.gui.setScreen(new CommandScreen(this,current instanceof MenuScreen||current instanceof PersonalScreen?current:null,module,form));}else if(mc.gui.screen() instanceof CommandScreen commands)commands.receive(b);}return;}
            if(message.kind()==Kind.CONTRACTS){contractHud.update(ContractHudState.parse(b));if(mc.gui.screen() instanceof PersonalScreen ps)ps.updated();return;}
            if(message.kind()==Kind.DIRECTORY){
                var rows=b.getAsJsonArray("entries");if(rows==null||rows.size()>96)throw new IllegalArgumentException("Invalid directory");
                java.util.List<JsonObject> entries=new java.util.ArrayList<>();for(var row:rows){var entry=row.getAsJsonObject();if(str(entry,"id","").length()>240||str(entry,"label","").length()>100)throw new IllegalArgumentException("Invalid entry");entries.add(entry.deepCopy());}
                var aliases=new java.util.HashMap<String,String>();for(var entry:entries)if(entry.has("aliases")&&entry.get("aliases").isJsonArray())for(var alias:entry.getAsJsonArray("aliases"))aliases.put(alias.getAsString(),str(entry,"id",""));
                if(preferences().remapFavorites(aliases))settings.save();
                boolean changed=!directory.equals(entries);directory=java.util.List.copyOf(entries);directoryToken=str(b,"token","");if(changed&&mc.gui.screen() instanceof PersonalScreen ps)ps.updated();return;
            }
            if(message.kind()==Kind.DASHBOARD){
                city=b.getAsJsonObject("city").deepCopy();java.util.List<String> lines=new java.util.ArrayList<>();for(var line:b.getAsJsonArray("summary"))lines.add(line.getAsString());summary=java.util.List.copyOf(lines);
                if(bool(b,"notify",false)&&preferences().summary)notifications.add("Пока тебя не было",summary.isEmpty()?"Сводка доступна в меню быстрого доступа.":summary.getFirst()+" Открыть: "+personalKey.getTranslatedKeyMessage().getString());
                if(mc.gui.screen() instanceof PersonalScreen ps)ps.updated();return;
            }
            if(message.kind()==Kind.PLACEMENT){placementControls.receive(b);return;}
            if(message.kind()==Kind.BACK){if(mc.gui.screen() instanceof AddonScreen addon)addon.performBack(str(b,"navigation",""),integer(b,"slot",-1));return;}
            if(message.kind()==Kind.BUILD_PREVIEW){if(mc.gui.screen() instanceof AddonScreen addon)addon.buildingPreview(b);return;}
            if(message.kind()==Kind.NATIVE_MENU){
                if(bool(b,"presentationOnly",false)){if(mc.gui.screen() instanceof AddonScreen addon)addon.presentation(b);return;}
                if(civicReturn!=null)civicReturn.nativeEntered();
                if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen source
                    &&source.getMenu().getRowCount()*9==integer(b,"slots",0)
                    &&source.getTitle().getString().equals(str(b,"title","")))
                    mc.gui.setScreen(new AddonScreen(this,source,b));
                return;
            }
            if(message.kind()==Kind.OPEN){
                MenuSchema.validate(b.getAsJsonObject("screen"));
                MenuScreen next=new MenuScreen(this,b);
                if(mc.gui.screen() instanceof MenuScreen old)old.detach();
                if(mc.gui.screen() instanceof SettingsScreen settingsScreen)settingsScreen.parent.detach();
                if(mc.gui.screen() instanceof PersonalScreen ps){ps.launched();if(ps.parent!=null)ps.parent.detach();}
                if(mc.gui.screen() instanceof HudPositionScreen hs){hs.parent.launched();if(hs.parent.parent!=null)hs.parent.parent.detach();}
                mc.gui.setScreen(next);return;
            }
            MenuScreen screen=mc.gui.screen() instanceof MenuScreen m?m:mc.gui.screen() instanceof SettingsScreen s?s.parent:mc.gui.screen() instanceof PersonalScreen ps?ps.parent:mc.gui.screen() instanceof HudPositionScreen hs?hs.parent.parent:mc.gui.screen() instanceof CivicScreen cs?cs.ownerMenu():null;
            if(screen==null||!screen.session.equals(str(b,"session","")))return;
            switch(message.kind()) {
                case PATCH -> {if(screen.revision==b.get("revision").getAsLong()){screen.patch(b.getAsJsonObject("data"));if(b.has("components"))screen.patchComponents(b.getAsJsonArray("components"));}}
                case RESULT -> {if(mc.gui.screen()!=screen)return;String action=str(b,"clientAction","");if(!screen.acceptResult(b.get("sequence").getAsLong(),action))return;screen.notice(str(b,"message",""),bool(b,"success",false));if(bool(b,"success",false)){switch(action){case "settings" -> mc.gui.setScreen(new SettingsScreen(screen,this));case "city" -> personal(screen,2);default -> {}}}}
                case CLOSE -> {screen.detach();if(mc.gui.screen() instanceof PersonalScreen ps)ps.launched();if(mc.gui.screen() instanceof CivicScreen cs)cs.forcedClose();else mc.gui.setScreen(null);String msg=str(b,"message","");if(!msg.isBlank())mc.player.sendOverlayMessage(Component.literal(msg));}
                default -> {}
            }
        }catch(Exception e){LOG.warn("Ignored invalid CraftoUI payload: {}",e.toString());}
    }
}
