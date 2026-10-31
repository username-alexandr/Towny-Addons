package cc.crafto.ui.client;
import net.minecraft.client.Minecraft;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Text-only controls match the existing dark theme; no item icon replacements. */
final class FlatButton extends Button {
    private final JsonObject theme;
    FlatButton(String label,int x,int y,int w,int h,Runnable action){this(label,x,y,w,h,action,null);}
    FlatButton(String label,int x,int y,int w,int h,Runnable action,JsonObject theme){super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.theme=theme;}
    private int color(String key,int fallback){return theme==null?fallback:UiTheme.color(theme,key);}
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        if(alpha==0)return;
        boolean over=isHoveredOrFocused();int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        g.nextStratum();g.fill(x,y,x+w,y+h,!active?color("disabled",0xFF23303C):over?color("hover",0xFF3D5163):color("button",0xFF2D3A49));if(over&&active)g.outline(x,y,w,h,color("accent",0xFF60D8B2));
        var font=Minecraft.getInstance().font;String text=getMessage().getString();if(font.width(text)>w-8)text=font.plainSubstrByWidth(text,Math.max(0,w-16))+"…";
        g.nextStratum();g.centeredText(font,text,x+w/2,y+(h-8)/2,active?color("text",0xFFE5F2F8):color("muted",0xFF9FAFC1));
    }
}
