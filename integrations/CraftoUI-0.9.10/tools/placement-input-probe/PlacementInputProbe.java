package cc.crafto.ui.client;
import static cc.crafto.ui.Protocol.*;
import net.minecraft.client.*;
import net.minecraft.client.input.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.fabricmc.fabric.api.client.keymapping.v1.*;
import net.fabricmc.fabric.api.event.client.player.*;
import java.nio.file.*;
public class PlacementInputProbe {
 static int checks;static void check(boolean b,String n){if(!b)throw new AssertionError(n);checks++;}
 static void ready(PlacementControls c){c.receive(object("active",true,"fixed",true,"anchored",false,"inputMode","menu","key","01234567-0123-0123-0123-0123456789ab","slot",0,"sequence",0,"ready",true,"x",10,"y",65,"z",20));}
 public static void main(String[] args)throws Exception{
  var mc=Minecraft.getInstance();var client=new CraftoUIClient();var controls=new PlacementControls(client);controls.register(new KeyMapping.Category());controls.supported(true);ready(controls);
  check(KeyMappingHelper.bindings.isEmpty(),"no placement arrow bindings registered");
  check(ClientHotbarScrollEvents.ALLOW.callback==null,"vanilla hotbar scrolling is never intercepted");
  for(int key:new int[]{265,264,263,262})check(!controls.keyPressed(new KeyEvent(key,0)),"arrows remain free");
  check(!controls.scroll(0,1)&&!controls.scroll(0,-1),"wheel does not move placement");
  controls.tick();check(client.packets.isEmpty(),"no legacy movement packets");
  var g=new GuiGraphicsExtractor();controls.extract(g);
  check(g.lines.size()==4,"compact four-line in-world hint");
  check(g.lines.stream().anyMatch(s->s.contains("Shift + ПКМ")&&s.contains("меню")),"hint explains how to open placement menu");
  check(g.lines.stream().anyMatch(s->s.contains("/nltplace")),"command fallback remains visible");
  check(g.lines.stream().noneMatch(s->s.contains("Колёсико")||s.contains("стрел")),"hint does not advertise removed controls");
  mc.gui.current=new AddonScreen(true);g.lines.clear();controls.extract(g);check(g.lines.isEmpty(),"hint hides while menu is open");
  check(!controls.keyPressed(new KeyEvent(265,0))&&!controls.scroll(0,1),"placement menu does not capture legacy inputs");
  controls.tick();check(client.packets.isEmpty(),"menu cannot send arrow/wheel movements");
  mc.gui.current=null;mc.player.inventory.slot=1;g.lines.clear();controls.extract(g);check(g.lines.isEmpty(),"different held item hides hint");mc.player.inventory.slot=0;
  controls.receive(object("active",false));g.lines.clear();controls.extract(g);check(g.lines.isEmpty(),"closed preview hides hint");
  ready(controls);controls.clear();g.lines.clear();controls.extract(g);check(g.lines.isEmpty(),"disconnect clears hint");
  Files.writeString(Path.of(args[0]),"PASS "+checks+" assertions; production PlacementControls with explicit Minecraft/input/transport adapters; no launched game client\n");System.out.println(Files.readString(Path.of(args[0])));
 }
}
