package cc.crafto.ui.client;
import cc.crafto.ui.*;import com.google.gson.*;import java.nio.file.*;import java.awt.image.*;import javax.imageio.*;import net.minecraft.client.*;import net.minecraft.client.gui.*;import net.minecraft.client.gui.screens.inventory.*;import net.minecraft.world.inventory.*;import net.minecraft.network.chat.*;import static cc.crafto.ui.Protocol.*;
/** Real production AddonScreen; platform/items/preview texture are explicit test doubles. */
public final class BuildMenuPreviewProbe{
 static int checks;static void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;}
 static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static GuiGraphicsExtractor render(AddonScreen s,int mx,int my,Path path)throws Exception{var image=new BufferedImage(s.width,s.height,2);var g=new GuiGraphicsExtractor(image.createGraphics());s.extractRenderState(g,mx,my,0);if(path!=null)ImageIO.write(image,"PNG",path.toFile());return g;}
 static int[] card(AddonScreen s,int slot)throws Exception{for(Object c:(java.util.List<?>)field(s,"cards")){var m=c.getClass().getDeclaredMethod("slot");m.setAccessible(true);if((Integer)m.invoke(c)!=slot)continue;int x=(Integer)c.getClass().getDeclaredMethod("x").invoke(c),y=(Integer)c.getClass().getDeclaredMethod("y").invoke(c);return new int[]{Math.round((Float)field(s,"left")+(x+8)*(Float)field(s,"scale")),Math.round((Float)field(s,"top")+(y+8)*(Float)field(s,"scale"))};}throw new AssertionError("No card "+slot);}
 public static void main(String[] args)throws Exception{Path out=Path.of(args[0]);
  for(String themeName:new String[]{"dark","classic"})for(int[] size:new int[][]{{900,600},{520,340},{320,180}}){
   var theme=JsonParser.parseString(Files.readString(Path.of(args[1],themeName+".json"))).getAsJsonObject();var menu=new ChestMenu();menu.item(11,"Ратуша");menu.item(13,"Сдать ресурсы");menu.item(15,"Получить чертёж");menu.item(22,"Назад");menu.item(24,"Обновить условия");Minecraft.getInstance().player.containerMenu=menu;
   var client=new CraftoUIClient();var options=object("viewKey","build/hall","buildingPreviewKey","preview","commandModule","NeverLandTownyBuilds","module","Городские здания","navigation","back-token","theme",theme,"presentation",object("previewSlot",11,"roles",object("back",22),"disabled",object()));
   var s=new AddonScreen(client,new ContainerScreen(menu,null,Component.literal("Ратуша")),options);s.width=size[0];s.height=size[1];String suffix=themeName+"-"+size[0]+"x"+size[1];
   render(s,0,0,null);var bar=AddonScreen.class.getDeclaredMethod("actionBar");bar.setAccessible(true);check(!(Boolean)bar.invoke(s),"no additional confirmation/action bar");
   var ps=AddonScreen.class.getDeclaredMethod("pageSize");ps.setAccessible(true);var page=AddonScreen.class.getDeclaredField("page");page.setAccessible(true);page.set(s,1/(Integer)ps.invoke(s));render(s,0,0,null);
   int[] button=card(s,13);BuildingPreviewTexture.draws=0;var g=render(s,button[0],button[1],out.resolve(suffix+"-donation-hover.png"));
   check((Integer)field(s,"selected")==13,"hover selects the one donation button");check(BuildingPreviewTexture.draws==0,"resource information is not covered by building preview");
   check(g.texts.stream().noneMatch(t->t.equals("Действия")||t.equals("Команды")||t.equals("Подтвердить")),"no confusing action or confirmation controls");
   StringBuilder visible=new StringBuilder(String.join("",g.texts));var layout=(AddonLayout)field(s,"layout");float scale=(Float)field(s,"scale"),left=(Float)field(s,"left"),top=(Float)field(s,"top");
   double x=left+(layout.detailX()+20)*scale,y=top+((Integer)field(s,"detailTop")+70)*scale;
   for(int i=0;i<25;i++){s.mouseScrolled(x,y,0,-1);visible.append(String.join("",render(s,(int)x,(int)y,null).texts));}
   String all=visible.toString().replaceAll("\\s+| ","");check(all.contains("Стоимостьизказны")&&all.contains("Необходимыересурсы"),"right pane carries captured building price and resource requirements");
   check(all.contains("Каменныекирпичи")&&all.contains("Дубовыедоски"),"actual server material names appear on the right");
   check(all.contains("ЛКМ")&&all.contains("складагорода"),"right pane describes both clicks on the same button");
  }
  Files.writeString(out.resolve("result.txt"),"PASS "+checks+" assertions; unchanged production CraftoUI 0.8.14 AddonScreen, captured server tooltip, explicit AWT/platform/texture doubles; 6 PNGs; no game client launched\n");System.out.println(Files.readString(out.resolve("result.txt")));
 }
}
