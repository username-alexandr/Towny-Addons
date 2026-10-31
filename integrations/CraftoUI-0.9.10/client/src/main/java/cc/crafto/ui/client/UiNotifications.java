package cc.crafto.ui.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
final class UiNotifications {
    private record Notice(String title,String message){}
    private final Deque<Notice> queue=new ArrayDeque<>();
    private Notice current;private long until;
    void clear(){queue.clear();current=null;until=0;}
    void add(String title,String message){if(queue.size()<8)queue.addLast(new Notice(title,message));}
    void extract(GuiGraphicsExtractor g){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.gui.screen()!=null)return;
        long now=System.nanoTime();if(current==null||now>=until){current=queue.pollFirst();until=now+7_000_000_000L;}if(current==null)return;
        int w=Math.min(280,g.guiWidth()-24),x=(g.guiWidth()-w)/2,y=16;
        var lines=mc.font.split(Component.literal(current.message()),w-24);int h=30+Math.min(3,lines.size())*11;
        g.fill(x,y,x+w,y+h,0xED182B29);g.outline(x,y,w,h,0xFF54D797);g.text(mc.font,current.title(),x+12,y+9,0xFF77E6B0,false);
        for(int i=0;i<Math.min(3,lines.size());i++)g.text(mc.font,lines.get(i),x+12,y+26+i*11,0xFFF0F4FA,false);
    }
}
