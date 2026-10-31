package cc.crafto.ui.client;
import cc.crafto.ui.*;import com.google.gson.*;import java.nio.file.*;import java.awt.image.*;import javax.imageio.*;import net.minecraft.client.*;import net.minecraft.client.gui.*;import net.minecraft.client.gui.screens.inventory.*;import net.minecraft.client.input.*;import net.minecraft.world.inventory.*;import net.minecraft.network.chat.*;import static cc.crafto.ui.Protocol.*;
public final class BuildMenuPreviewProbe{
 static int checks;static void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;}
 static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static GuiGraphicsExtractor render(AddonScreen s,int mx,int my,Path path)throws Exception{var image=new BufferedImage(s.width,s.height,2);var g=new GuiGraphicsExtractor(image.createGraphics());s.extractBackground(g,mx,my,0);s.extractRenderState(g,mx,my,0);if(path!=null)ImageIO.write(image,"PNG",path.toFile());return g;}
 static int[] card(AddonScreen s,int slot)throws Exception{for(Object c:(java.util.List<?>)field(s,"cards")){var m=c.getClass().getDeclaredMethod("slot");m.setAccessible(true);if((Integer)m.invoke(c)!=slot)continue;int x=(Integer)c.getClass().getDeclaredMethod("x").invoke(c),y=(Integer)c.getClass().getDeclaredMethod("y").invoke(c);return new int[]{Math.round((Float)field(s,"left")+(x+8)*(Float)field(s,"scale")),Math.round((Float)field(s,"top")+(y+8)*(Float)field(s,"scale"))};}throw new AssertionError("No card "+slot);}
 public static void main(String[] args)throws Exception{Path out=Path.of(args[0]);
  for(String themeName:new String[]{"dark","classic"})for(int[] size:new int[][]{{900,600},{520,340},{320,180}}){
   var theme=JsonParser.parseString(Files.readString(Path.of(args[1],themeName+".json"))).getAsJsonObject();var menu=new ChestMenu();menu.item(11,"Ратуша");menu.item(13,"Материалы из вашего инвентаря");menu.item(15,"Получить чертёж");menu.item(28,"Каменные кирпичи");menu.item(29,"Дубовые доски");menu.item(45,"Назад");menu.item(50,"Обновить условия");menu.item(53,"Сдать материалы");Minecraft.getInstance().player.containerMenu=menu;
   var client=new CraftoUIClient();var options=object("viewKey","build/hall","buildingPreviewKey","preview","commandModule","NeverLandTownyBuilds","module","Городские здания","navigation","back-token","theme",theme,"presentation",object("previewSlot",11,"roles",object("back",45,"confirm",53),"disabled",object("53","Нужны материалы")));
   var s=new AddonScreen(client,new ContainerScreen(menu,null,Component.literal("Ратуша")),options);s.width=size[0];s.height=size[1];String suffix=themeName+"-"+size[0]+"x"+size[1];
   BuildingPreviewTexture.draws=0;var g=render(s,0,0,out.resolve(suffix+"-idle.png"));check(BuildingPreviewTexture.draws==0,"no preview without hover");check(g.texts.stream().noneMatch(t->t.equals("Действия")||t.equals("Команды")),"no actions button in building menu");
   int[] hall=card(s,11);render(s,hall[0],hall[1],out.resolve(suffix+"-hall-hover.png"));check(BuildingPreviewTexture.draws==1,"hall hover draws preview");int rotated=BuildingPreviewTexture.rotations;s.mouseScrolled(hall[0],hall[1],0,-1);check(BuildingPreviewTexture.rotations==rotated+1,"wheel over hall rotates preview");
   var ps=AddonScreen.class.getDeclaredMethod("pageSize");ps.setAccessible(true);var page=AddonScreen.class.getDeclaredField("page");page.setAccessible(true);page.set(s,1/(Integer)ps.invoke(s));render(s,0,0,null);
   int[] source=card(s,13);render(s,source[0],source[1],out.resolve(suffix+"-source-hover.png"));check(BuildingPreviewTexture.draws==1,"source button hides preview");render(s,0,0,null);check(BuildingPreviewTexture.draws==1,"leaving hall hides preview despite selected card");
   s.keyPressed(new KeyEvent(296,0));check(client.commandOpens==0,"F7 does not open confusing building command menu");
   s.presentation(object("viewKey","stale","presentation",object("roles",object(),"disabled",object())));check(((java.util.Map<?,?>)field(s,"disabled")).containsKey(53),"late metadata cannot alter other view");
   s.presentation(object("viewKey","build/hall","presentation",object("previewSlot",11,"roles",object("back",45,"confirm",53),"disabled",object())));check(((java.util.Map<?,?>)field(s,"disabled")).isEmpty(),"same-menu update clears obsolete disabled reason");
   check(g.texts.stream().anyMatch(t->t.equals("Сдать материалы")),"confirm control keeps clear donation caption");
   client.placementControls.enabled=true;var placementOptions=options.deepCopy();placementOptions.addProperty("placementControls",true);
   var placement=new AddonScreen(client,new ContainerScreen(menu,null,Component.literal("Размещение здания")),placementOptions);
   check(placement.keyPressed(new KeyEvent(265,0))&&client.placementControls.keys==1,"actual placement screen forwards arrow to controller");
   check(placement.mouseScrolled(0,0,0,1)&&client.placementControls.wheels==1,"actual placement screen forwards wheel to controller");
   s.keyPressed(new KeyEvent(265,0));s.mouseScrolled(0,0,0,1);
   check(client.placementControls.keys==1&&client.placementControls.wheels==1,"ordinary building screen never forwards movement input");
  }
  Files.writeString(out.resolve("result.txt"),"PASS "+checks+" assertions; real AddonScreen with explicit AWT/platform/texture doubles; 18 PNGs; no launched game client\n");System.out.println(Files.readString(out.resolve("result.txt")));
 }
}
