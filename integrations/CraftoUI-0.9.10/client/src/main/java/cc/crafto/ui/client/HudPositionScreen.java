package cc.crafto.ui.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
final class HudPositionScreen extends Screen {
    final PersonalScreen parent;private final CraftoUIClient client;private boolean dragging,returning;private double offsetX,offsetY;
    HudPositionScreen(PersonalScreen parent,CraftoUIClient client){super(Component.literal("Перетащите панель мышью"));this.parent=parent;this.client=client;}
    @Override protected void init(){
        boolean compact=width<280;int buttonWidth=compact?Math.max(40,Math.min(100,(width-24-8)/2)):62;
        int columns=compact?2:4,gap=compact?8:4,total=columns*buttonWidth+(columns-1)*gap,x=(width-total)/2;
        var theme=UiTheme.resolve(client.lastTheme,client.settings.darkTheme);
        String[] labels={"Справа","Слева","Сверху","← Назад"};Runnable[] actions={()->anchor(1,.5f),()->anchor(0,.5f),()->anchor(.5f,0),this::onClose};
        for(int i=0;i<4;i++){Runnable action=actions[i];addRenderableWidget(new CivicCardButton(labels[i],"","","","control",true,false,x+i%columns*(buttonWidth+gap),height-30-(compact?i/columns*26:0),buttonWidth,22,0,height,action,theme));}
    }
    private void anchor(float x,float y){var p=client.preferences();p.x=x;p.y=y;client.settings.save();}
    private ContractHud.Bounds bounds(){return client.contractHud.bounds(width,height,client.preferences().selected(client.contractHud.entries()));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){if(client.settings.blur)extractBlurredBackground(g);g.fill(0,0,width,height,0x60202A35);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){var theme=UiTheme.resolve(client.lastTheme,client.settings.darkTheme);CivicStyle.panel(g,8,6,width-16,30,UiTheme.color(theme,"panel"));CivicStyle.text(g,"Перетащите панель или выберите положение",16,17,width-32,UiTheme.color(theme,"text"));client.contractHud.preview(g);super.extractRenderState(g,x,y,d);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(super.mouseClicked(event,twice))return true;var b=bounds();if(event.button()==0&&event.x()>=b.x()&&event.x()<b.x()+b.w()&&event.y()>=b.y()&&event.y()<b.y()+b.h()){dragging=true;offsetX=event.x()-b.x();offsetY=event.y()-b.y();return true;}return false;}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(!dragging)return super.mouseDragged(event,dx,dy);var b=bounds();var p=client.preferences();p.x=(float)Math.clamp((event.x()-offsetX-8)/Math.max(1,width-16-b.w()),0,1);p.y=(float)Math.clamp((event.y()-offsetY-8)/Math.max(1,height-16-b.h()),0,1);return true;}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(dragging){dragging=false;client.settings.save();return true;}return super.mouseReleased(event);}
    @Override public void onClose(){client.settings.save();returning=true;parent.resume();minecraft.gui.setScreen(parent);}
    @Override public void removed(){if(!returning&&parent.parent!=null)parent.parent.releaseSession();}
    @Override public boolean isPauseScreen(){return false;}
}
