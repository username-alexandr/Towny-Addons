package cc.crafto.ui.client;

import cc.crafto.ui.PlacementInput;
import com.google.gson.JsonObject;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import static cc.crafto.ui.Protocol.*;

/** Display-only placement hint. All movement and rotation use the server's placement menu. */
final class PlacementControls {
    private final PlacementInput input=new PlacementInput();
    private Object level;private String position="";private boolean available,ready,anchored;
    PlacementControls(CraftoUIClient client){}
    void register(KeyMapping.Category category){/* No arrow bindings or hotbar-scroll interception. */}
    void clear(){input.clear();level=null;available=false;}
    void supported(boolean value){available=value;if(!value)input.clear();}
    void receive(JsonObject body){
        if(!available)return;
        if(!bool(body,"active",false)){input.clear();return;}
        level=Minecraft.getInstance().level;
        input.update(str(body,"key",""),integer(body,"slot",-1),body.has("sequence")?body.get("sequence").getAsLong():0,System.nanoTime());
        ready=bool(body,"ready",false);anchored=bool(body,"anchored",bool(body,"fixed",false));
        position=integer(body,"x",0)+" / "+integer(body,"y",0)+" / "+integer(body,"z",0);
    }
    private boolean visible(){
        var mc=Minecraft.getInstance();
        return available&&mc.player!=null&&mc.level!=null&&mc.level==level&&mc.gui.screen()==null&&mc.isWindowActive()&&!mc.isPaused()
            &&input.active(mc.player.getInventory().getSelectedSlot(),System.nanoTime());
    }
    boolean keyPressed(KeyEvent event){return false;}
    boolean scroll(double horizontal,double vertical){return false;}
    void tick(){}
    void extract(GuiGraphicsExtractor g){
        if(!visible())return;var mc=Minecraft.getInstance();
        String[] lines={"Размещение · "+position+(ready?"":" · Обновляю…"),
            "Shift + ПКМ чертежом — открыть меню",
            anchored?"Место улучшения уже закреплено":"В меню: перемещение, поворот, установка",
            "/nltplace — открыть то же меню"};
        float scale=Math.min(1f,Math.min((g.guiWidth()-16f)/350f,(g.guiHeight()-16f)/180f));int width=350,height=56;
        g.pose().pushMatrix();try{g.pose().translate(8,8);g.pose().scale(scale,scale);g.fill(0,0,width,height,0xD0181F29);
            for(int i=0;i<lines.length;i++)g.text(mc.font,mc.font.plainSubstrByWidth(lines[i],width-12),6,5+i*12,i==0?0xFF80C6E8:0xFFF0F4FA,false);
        }finally{g.pose().popMatrix();}
    }
}
