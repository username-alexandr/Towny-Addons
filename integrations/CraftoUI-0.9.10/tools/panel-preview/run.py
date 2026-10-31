#!/usr/bin/env python3
"""Render the production CivicScreen and its real civic widgets using explicit AWT/UI doubles.
Reuses the checked-in bitmap-font adapter; does not launch Minecraft or a network server.
"""
from pathlib import Path
import argparse,ast,subprocess,os
p=argparse.ArgumentParser();p.add_argument('--java-home',type=Path,required=True);p.add_argument('--minecraft',type=Path,required=True);p.add_argument('--gson',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--prepare-only',action='store_true');a=p.parse_args()
root=Path(__file__).resolve().parents[2];out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);src=out/'adapter';classes=out/'classes';classes.mkdir(exist_ok=True)
def dictionary(path):
    tree=ast.parse(path.read_text())
    return next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets))
sources=dictionary(root/'tools/city-lifecycle-test/run.py')
visual=dictionary(root/'tools/city-preview/run.py')
for key in ('net/minecraft/client/gui/Font.java','net/minecraft/client/gui/GuiGraphicsExtractor.java'):
    sources[key]=visual[key]
sources['net/minecraft/client/gui/GuiGraphicsExtractor.java']=sources['net/minecraft/client/gui/GuiGraphicsExtractor.java'].replace('public final Graphics2D g;', 'public final java.util.List<String> texts=new ArrayList<>();public final Graphics2D g;public GuiGraphicsExtractor(){this(new java.awt.image.BufferedImage(1920,1080,2).createGraphics());}').replace('f.draw(g,s,x,y,c);','texts.add(s);f.draw(g,s,x,y,c);').replace(' final Pose pose=', ' public void setTooltipForNextFrame(Font f,java.util.List<FormattedCharSequence> lines,int x,int y){}\n final Pose pose=')
sources.update({
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;import net.minecraft.client.gui.screens.Screen;public class Minecraft{private static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}public final net.minecraft.client.gui.Font font=new net.minecraft.client.gui.Font();public final Gui gui=new Gui();public static class Gui{public Screen screen;public void setScreen(Screen s){screen=s;}}}''',
'net/minecraft/client/gui/components/Button.java':'''package net.minecraft.client.gui.components;
import net.minecraft.network.chat.Component;import net.minecraft.client.gui.GuiGraphicsExtractor;import net.minecraft.client.gui.components.events.GuiEventListener;import java.util.function.Consumer;
public class Button implements GuiEventListener{protected static final Object DEFAULT_NARRATION=new Object();protected float alpha=1;public boolean active=true,hovered,focused;public int x,y,w,h;public final String label;public final Runnable press;private final Component message;public Tooltip tooltip;
 public Button(String label,int x,int y,int w,int h,Runnable action){this(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION);}
 public Button(int x,int y,int w,int h,Component m,Consumer<Button> action,Object n){this.x=x;this.y=y;this.w=w;this.h=h;message=m;label=m.getString();press=()->action.accept(this);}
 public void setTooltip(Tooltip t){tooltip=t;}public void setAlpha(float a){alpha=a;}public int getX(){return x;}public int getY(){return y;}public int getWidth(){return w;}public int getHeight(){return h;}public void setY(int v){y=v;}public Component getMessage(){return message;}public boolean isHoveredOrFocused(){return hovered||focused;}public boolean isMouseOver(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
 protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float d){}public void render(GuiGraphicsExtractor g,int mx,int my,float d){hovered=isMouseOver(mx,my);extractContents(g,mx,my,d);}
}''',
'cc/crafto/ui/client/UiTheme.java':'''package cc.crafto.ui.client;import com.google.gson.JsonObject;final class UiTheme{static JsonObject resolve(JsonObject t,boolean dark){return t;}static int color(JsonObject t,String k){return t.has(k)?(int)Long.parseLong(t.get(k).getAsString().substring(1),16):0xFF343B4A;}}'''
})
key='net/minecraft/client/gui/screens/Screen.java'
sources[key]=sources[key].replace('protected final Font font=new Font();','protected final Font font=Minecraft.getInstance().font;').replace('public void setFocused(GuiEventListener b){}','public void setFocused(GuiEventListener b){for(var widget:widgets)widget.focused=widget==b;}').replace('public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){}','public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){for(var b:widgets)b.render(g,x,y,d);}').replace('e.x()>=b.x&&e.x()<b.x+b.w&&e.y()>=b.y&&e.y()<b.y+b.h','b.isMouseOver(e.x(),e.y())')
for name in ('FlatButton','CityCardButton','CivicCardButton','CivicStyle'):
    sources.pop('cc/crafto/ui/client/'+name+'.java',None)
sources['cc/crafto/ui/client/CivicRecoveryProbe.java']=(root/'tools/city-lifecycle-test/CivicRecoveryProbe.java').read_text()
sources['cc/crafto/ui/client/CivicPageProbe.java']=(root/'tools/civic-page-preview/CivicPageProbe.java').read_text()
# Additional adapters and behavioural scenarios for the local pages and command workflow.
exec(compile((Path(__file__).parent/'adapters.py').read_text(),str(Path(__file__).parent/'adapters.py'),'exec'))
for name,code in sources.items():f=src/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(code)
if a.prepare_only:raise SystemExit(0)
production=[root/'common/src/main/java/cc/crafto/ui'/n for n in ('Protocol.java','CityDashboardLayout.java','OrderGridLayout.java','TownUi.java','CivicPageLayout.java','CommandForm.java','ContractHudState.java','PersonalPreferences.java','QuickSearch.java','OrderRarityStyle.java')]+[root/'client/src/main/java/cc/crafto/ui/client'/n for n in ('CivicScreen.java','CityCardButton.java','FlatButton.java','CivicCardButton.java','CivicStyle.java','PanelScreen.java','PersonalScreen.java','CommandScreen.java','SettingsScreen.java','HudPositionScreen.java','OrderCardButton.java')]
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-encoding','UTF-8','-cp',str(a.gson),'-d',str(classes),*map(str,src.rglob('*.java')),*map(str,production)],check=True)
cmd=[str(a.java_home/'bin/java'),'-Djava.awt.headless=true','-Dminecraft.jar='+str(a.minecraft),'-cp',os.pathsep.join((str(classes),str(a.gson)))]
subprocess.run(cmd+['cc.crafto.ui.client.CivicLifecycleProbe'],check=True)
subprocess.run(cmd+['cc.crafto.ui.client.CivicRecoveryProbe'],check=True)
subprocess.run(cmd+['cc.crafto.ui.client.CivicPageProbe',str(out),str(root/'server/src/main/resources/themes')],check=True)

subprocess.run(cmd+['cc.crafto.ui.client.PanelProbe',str(out),str(root/'server/src/main/resources/themes')],check=True)
subprocess.run(cmd+['cc.crafto.ui.client.WheelProbe',str(out),str(root/'server/src/main/resources/themes')],check=True)
subprocess.run(cmd+['cc.crafto.ui.client.RecentMenusProbe',str(out),str(root/'server/src/main/resources/themes')],check=True)
subprocess.run(cmd+['cc.crafto.ui.client.FinanceProbe',str(out),str(root/'server/src/main/resources/themes')],check=True)
