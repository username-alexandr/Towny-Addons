package cc.crafto.ui.client;
import cc.crafto.ui.OrderRarityStyle;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Rarity is visible as both a word and a color, including for unavailable offers. */
final class OrderCardButton extends Button {
    private final JsonObject theme;private final OrderRarityStyle rarity;private final String reward,kind,reason;private final ItemStack icon;
    private int clipTop=Integer.MIN_VALUE,clipBottom=Integer.MAX_VALUE;
    void clipTo(int top,int bottom){clipTop=top;clipBottom=bottom;}
    @Override public boolean isMouseOver(double x,double y){return y>=clipTop&&y<clipBottom&&super.isMouseOver(x,y);}
    OrderCardButton(String title,String rarity,String reward,String kind,String reason,String item,int x,int y,int w,int h,Runnable action,JsonObject theme){
        super(x,y,w,h,Component.literal(title),b->action.run(),DEFAULT_NARRATION);this.theme=theme;this.rarity=OrderRarityStyle.parse(rarity);this.reward=reward;this.kind=kind;this.reason=reason;
        ItemStack stack=ItemStack.EMPTY;try{if(!item.isBlank())stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item)));}catch(Exception ignored){}
        if(!stack.isEmpty()&&this.rarity!=null)stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,this.rarity.glint());this.icon=stack;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();if(y>=clipBottom||y+h<=clipTop)return;g.enableScissor(x,Math.max(y,clipTop),x+w,Math.min(y+h,clipBottom));var font=Minecraft.getInstance().font;
        int accent=rarity==null?UiTheme.color(theme,"border"):rarity.color;
        g.nextStratum();g.fill(x,y,x+w,y+h,UiTheme.color(theme,isHoveredOrFocused()?"hover":"button"));g.outline(x,y,w,h,accent);g.fill(x,y,x+3,y+h,accent);
        if(!icon.isEmpty())g.item(icon,x+9,y+6);
        String badge=rarity==null?(reward.isBlank()?"Раздел заказов":"Редкость не указана"):rarity.label;g.text(font,badge,x+(icon.isEmpty()?10:32),y+9,accent,false);
        boolean compact=h<60;int ty=y+(compact?21:23);
        if(compact){String title=getMessage().getString();if(font.width(title)>w-20)title=font.plainSubstrByWidth(title,Math.max(1,w-28))+"…";g.text(font,title,x+10,ty,UiTheme.color(theme,"text"),false);}
        else for(var line:font.split(getMessage(),w-20)){if(ty+font.lineHeight>y+h-20)break;g.text(font,line,x+10,ty,UiTheme.color(theme,"text"),false);ty+=font.lineHeight+2;}
        String footer=reason.isBlank()?(reward.isBlank()?kind:kind+" · "+reward+" монет"):reason;
        if(font.width(footer)>w-20)footer=font.plainSubstrByWidth(footer,Math.max(1,w-28))+"…";
        g.text(font,footer,x+10,y+h-(compact?11:14),UiTheme.color(theme,"muted"),false);
        if(isHoveredOrFocused())g.outline(x+2,y+2,w-4,h-4,UiTheme.color(theme,"accent"));g.disableScissor();
    }
}
