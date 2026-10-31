#!/usr/bin/env python3
"""Headless rendering of production CityCardButton with an AWT drawing adapter.
Uses bitmap fonts from a supplied, genuine Minecraft JAR. No game client is launched.
"""
from pathlib import Path
import argparse,subprocess,os
p=argparse.ArgumentParser();p.add_argument('--java-home',type=Path,required=True);p.add_argument('--minecraft',type=Path,required=True);p.add_argument('--gson',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[2];out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);src=out/'adapter';classes=out/'classes';classes.mkdir(exist_ok=True)
sources={
'net/minecraft/util/FormattedCharSequence.java':r'''package net.minecraft.util;
public record FormattedCharSequence(String text){public interface Sink{boolean accept(int i,Object style,int cp);}public void accept(Sink s){for(int i=0;i<text.length();i++)if(!s.accept(i,null,text.charAt(i)))break;}}''',
'net/minecraft/network/chat/Component.java':r'''package net.minecraft.network.chat;
public record Component(String text){public static Component literal(String t){return new Component(t);}public String getString(){return text;}}''',
'net/minecraft/client/Minecraft.java':r'''package net.minecraft.client;
public class Minecraft{private static final Minecraft INSTANCE=new Minecraft();public final net.minecraft.client.gui.Font font=new net.minecraft.client.gui.Font();public static Minecraft getInstance(){return INSTANCE;}}''',
'net/minecraft/client/gui/Font.java':r'''package net.minecraft.client.gui;
import java.awt.*;import java.awt.image.*;import java.util.*;import java.util.List;import java.util.zip.*;import javax.imageio.*;import com.google.gson.*;import net.minecraft.network.chat.Component;import net.minecraft.util.FormattedCharSequence;
public class Font{
 public final int lineHeight=9;record Glyph(BufferedImage image,int advance,int height,int ascent){}final Map<Integer,Glyph> glyphs=new HashMap<>();final Map<String,BufferedImage> tinted=new HashMap<>();
 public Font(){try(var z=new ZipFile(System.getProperty("minecraft.jar"))){var json=JsonParser.parseString(new String(z.getInputStream(z.getEntry("assets/minecraft/font/include/default.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  for(var e:json.getAsJsonArray("providers")){var p=e.getAsJsonObject();if(!p.get("type").getAsString().equals("bitmap"))continue;String name=p.get("file").getAsString().replace("minecraft:","assets/minecraft/textures/");var image=ImageIO.read(z.getInputStream(z.getEntry(name)));var rows=p.getAsJsonArray("chars");int ch=image.getHeight()/rows.size(),cw=image.getWidth()/rows.get(0).getAsString().codePointCount(0,rows.get(0).getAsString().length()),height=p.has("height")?p.get("height").getAsInt():8,ascent=p.get("ascent").getAsInt();
  for(int y=0;y<rows.size();y++){int[] row=rows.get(y).getAsString().codePoints().toArray();for(int x=0;x<row.length;x++){int cp=row[x];if(cp==0||glyphs.containsKey(cp))continue;var cell=image.getSubimage(x*cw,y*ch,cw,ch);int right=0;for(int gx=0;gx<cw;gx++)for(int gy=0;gy<ch;gy++)if((cell.getRGB(gx,gy)>>>24)>0)right=Math.max(right,gx+1);glyphs.put(cp,new Glyph(cell,(int)(right*(float)height/ch+.5f)+1,height,ascent));}}}
 }catch(Exception e){throw new RuntimeException(e);}}
 int advance(int cp){return cp==32||cp==160?4:glyphs.containsKey(cp)?glyphs.get(cp).advance:6;}
 public int width(String s){return s.codePoints().map(this::advance).sum();}
 public String plainSubstrByWidth(String s,int max){StringBuilder b=new StringBuilder();int w=0;for(int cp:s.codePoints().toArray()){w+=advance(cp);if(w>max)break;b.appendCodePoint(cp);}return b.toString();}
 public List<FormattedCharSequence> split(Component c,int max){List<FormattedCharSequence> lines=new ArrayList<>();String rest=c.text();while(!rest.isEmpty()){String line=plainSubstrByWidth(rest,Math.max(6,max));if(line.isEmpty())line=rest.substring(0,1);if(line.length()<rest.length()&&line.lastIndexOf(' ')>0)line=line.substring(0,line.lastIndexOf(' '));lines.add(new FormattedCharSequence(line));rest=rest.substring(line.length()).stripLeading();}return lines;}
 public void draw(Graphics2D g,String text,int x,int y,int color){for(int cp:text.codePoints().toArray()){var gl=glyphs.get(cp);if(gl!=null){String key=cp+":"+color;var img=tinted.computeIfAbsent(key,k->{var im=new BufferedImage(gl.image.getWidth(),gl.image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int ix=0;ix<im.getWidth();ix++)for(int iy=0;iy<im.getHeight();iy++){int alpha=gl.image.getRGB(ix,iy)>>>24;im.setRGB(ix,iy,alpha<<24|color&0xFFFFFF);}return im;});g.drawImage(img,x,y+7-gl.ascent,(int)(img.getWidth()*(float)gl.height/img.getHeight()),gl.height,null);}x+=advance(cp);}}
}''',
'net/minecraft/client/gui/GuiGraphicsExtractor.java':r'''package net.minecraft.client.gui;
import java.awt.*;import java.awt.geom.*;import java.util.*;import net.minecraft.util.FormattedCharSequence;
public class GuiGraphicsExtractor{public final Graphics2D g;public final AffineTransform base;public GuiGraphicsExtractor(Graphics2D g){this.g=g;this.base=g.getTransform();}public void nextStratum(){}public void fill(int x,int y,int xx,int yy,int c){g.setColor(new Color(c,true));g.fillRect(x,y,Math.max(0,xx-x),Math.max(0,yy-y));}public void outline(int x,int y,int w,int h,int c){fill(x,y,x+w,y+1,c);fill(x,y+h-1,x+w,y+h,c);fill(x,y,x+1,y+h,c);fill(x+w-1,y,x+w,y+h,c);}public void text(Font f,String s,int x,int y,int c,boolean shadow){f.draw(g,s,x,y,c);}public void text(Font f,FormattedCharSequence s,int x,int y,int c,boolean shadow){text(f,s.text(),x,y,c,shadow);}public void centeredText(Font f,String s,int x,int y,int c){text(f,s,x-f.width(s)/2,y,c,false);}
 final Deque<Shape> clips=new LinkedList<>();public void enableScissor(int x,int y,int xx,int yy){clips.push(g.getClip());try{var toGui=base.createInverse();toGui.concatenate(g.getTransform());var a=new Point2D.Double(x,y);var b=new Point2D.Double(xx,yy);toGui.transform(a,a);toGui.transform(b,b);var rect=new Rectangle2D.Double(Math.floor(a.x),Math.floor(a.y),Math.floor(b.x-a.x),Math.floor(b.y-a.y));g.clip(toGui.createInverse().createTransformedShape(rect));}catch(NoninvertibleTransformException e){throw new RuntimeException(e);}}public void disableScissor(){g.setClip(clips.pop());}
 final Pose pose=new Pose();public Pose pose(){return pose;}public class Pose{final Deque<AffineTransform> stack=new ArrayDeque<>();public void pushMatrix(){stack.push(g.getTransform());}public void popMatrix(){g.setTransform(stack.pop());}public void translate(float x,float y){g.translate(x,y);}public void scale(float x,float y){g.scale(x,y);}}
}''',
'net/minecraft/client/gui/components/Button.java':r'''package net.minecraft.client.gui.components;
import net.minecraft.network.chat.Component;import net.minecraft.client.gui.GuiGraphicsExtractor;import java.util.function.Consumer;
public abstract class Button{protected static final Object DEFAULT_NARRATION=new Object();protected float alpha=1;public boolean active=true;private int x,y,w,h;private final Component message;public Button(int x,int y,int w,int h,Component m,Consumer<Button> a,Object n){this.x=x;this.y=y;this.w=w;this.h=h;message=m;}public int getX(){return x;}public int getY(){return y;}public int getWidth(){return w;}public int getHeight(){return h;}public Component getMessage(){return message;}public boolean isHoveredOrFocused(){return false;}public boolean isMouseOver(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}protected abstract void extractContents(GuiGraphicsExtractor g,int x,int y,float d);}''',
'cc/crafto/ui/client/UiTheme.java':r'''package cc.crafto.ui.client;import com.google.gson.JsonObject;final class UiTheme{static int color(JsonObject t,String k){return (int)Long.parseLong(t.get(k).getAsString().substring(1),16);}}''',
'cc/crafto/ui/client/CityPreview.java':r'''package cc.crafto.ui.client;
import cc.crafto.ui.*;import com.google.gson.*;import net.minecraft.client.*;import net.minecraft.client.gui.*;import java.nio.file.*;import java.awt.image.*;import java.awt.RenderingHints;import javax.imageio.*;import static cc.crafto.ui.Protocol.*;
public class CityPreview{
 static JsonObject metric(String label,String value,String hint,String icon){return object("label",label,"value",value,"hint",hint,"icon",icon);}
 static JsonObject entry(String title,String detail,String icon,String category,int done,int total,boolean known){return object("title",title,"detail",detail,"presentation",object("icon",icon,"category",category,"status",known?"В работе":"Нет подсчёта","done",done,"total",total,"known",known));}
 static void card(GuiGraphicsExtractor g,JsonObject data,String kind,boolean selected,CityDashboardLayout.Box b,int left,int top,int bottom,int scroll,JsonObject theme){var button=new CityCardButton(data,kind,selected,left+b.x(),top+b.y()-scroll,b.width(),b.height(),top,bottom,()->{},theme);button.extractContents(g,0,0,0);if(button.isMouseOver(left+b.x()+1,top-1)||button.isMouseOver(left+b.x()+1,bottom))throw new AssertionError("Clipped widget accepts outside viewport");}
 static void render(Path out,Path themePath,int w,int h,boolean end)throws Exception{
  var theme=JsonParser.parseString(Files.readString(themePath)).getAsJsonObject();var font=Minecraft.getInstance().font;
  int left=Math.max(8,(w-720)/2),span=w-left*2,top=32,bottom=h-50;var layout=CityDashboardLayout.fit(span-8,bottom-top,7,3);int scroll=end?layout.maxScroll():0;
  var image=new BufferedImage(w*2,h*2,BufferedImage.TYPE_INT_ARGB);var g2=image.createGraphics();g2.scale(2,2);g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);var g=new GuiGraphicsExtractor(g2);
  g.fill(0,0,w,h,UiTheme.color(theme,"background"));g.centeredText(font,"NeverLand",w/2,12,UiTheme.color(theme,"text"));g.enableScissor(left,top,left+span,bottom);
  g.text(font,"Мэр · состояние вашего города",left+2,top+3-scroll,UiTheme.color(theme,"muted"),false);
  g.text(font,"Развитие города",left+2,top+layout.shortcutsY()-14-scroll,UiTheme.color(theme,"muted"),false);g.text(font,"Управление и события",left+2,top+layout.managementY()-14-scroll,UiTheme.color(theme,"muted"),false);g.disableScissor();
  var metrics=new JsonObject[]{metric("Казна","2 146 788 553,00","Баланс города","treasury"),metric("Жители","1","Мэр","residents"),metric("Занято участков","40","Лимит: 68","map"),metric("Можно захватить","28","Доступно по лимиту города","claim")};
  for(int i=0;i<metrics.length;i++)card(g,metrics[i],"metric",false,layout.metric(i),left,top,bottom,scroll,theme);
  String[] titles={"Постройки","Материалы","Общая цель","Карта участков","Казна","История города","Приглашения"},icons={"build","materials","goal","map","treasury","history","inbox"};
  for(int i=0;i<titles.length;i++)card(g,object("title",titles[i],"presentation",object("icon",icons[i])),"shortcut",false,layout.shortcut(i),left,top,bottom,scroll,theme);
  String[] tabs={"В работе · 3","Здания · 6","Контракты · 1"};for(int i=0;i<3;i++)card(g,object("title",tabs[i]),"tab",i==0,layout.tab(i),left,top,bottom,scroll,theme);
  var rows=new JsonObject[]{entry("Ратуша · этап 3","Установлено: 640 / 1000","build","Строительство",640,1000,true),entry("Собрать древесину","Прогресс: 96 / 128 · ваш вклад: 32","contract","Задание города",96,128,true),entry("Городской склад с длинным названием","Подойдите к площадке для подсчёта","build","Строительство",0,0,false)};
  for(int i=0;i<rows.length;i++)card(g,rows[i],"entry",false,layout.card(i),left,top,bottom,scroll,theme);
  if(layout.maxScroll()>0){int track=bottom-top,thumb=Math.max(14,track*track/layout.contentHeight()),y=top+(track-thumb)*scroll/layout.maxScroll();g.fill(left+span-3,top,left+span,bottom,UiTheme.color(theme,"panel"));g.fill(left+span-3,y,left+span,y+thumb,UiTheme.color(theme,"accent"));}
  new FlatButton("← Назад",left,h-26,78,20,()->{},theme).extractContents(g,0,0,0);new FlatButton("Обновить",left+span-88,h-26,88,20,()->{},theme).extractContents(g,0,0,0);new FlatButton("В игру",left+(span-68)/2,h-26,68,20,()->{},theme).extractContents(g,0,0,0);
  g.text(font,"Пример данных · предпросмотр отрисовки",left,bottom+7,UiTheme.color(theme,"muted"),false);g2.dispose();ImageIO.write(image,"PNG",out.toFile());
 }
 public static void main(String[] args)throws Exception{Path out=Path.of(args[0]),themes=Path.of(args[1]);for(String theme:new String[]{"dark","classic"})for(int[] size:new int[][]{{800,460},{520,340},{320,240},{320,180}})for(boolean end:new boolean[]{false,true})render(out.resolve(theme+"-"+size[0]+"x"+size[1]+(end?"-scrolled":"")+".png"),themes.resolve(theme+".json"),size[0],size[1],end);System.out.println("PASS 16 production-card renders; clipped mouse regions checked. AWT adapter, no game client.");}
}'''
}
for name,code in sources.items():f=src/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(code)
production=[root/'common/src/main/java/cc/crafto/ui'/n for n in ('Protocol.java','CityDashboardLayout.java')]+[root/'client/src/main/java/cc/crafto/ui/client'/n for n in ('CityCardButton.java','FlatButton.java')]
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-encoding','UTF-8','-cp',str(a.gson),'-d',str(classes),*[str(f) for f in src.rglob('*.java')],*map(str,production)],check=True)
subprocess.run([str(a.java_home/'bin/java'),'-Djava.awt.headless=true','-Dminecraft.jar='+str(a.minecraft),'-cp',os.pathsep.join((str(classes),str(a.gson))),'cc.crafto.ui.client.CityPreview',str(out),str(root/'server/src/main/resources/themes')],check=True)
