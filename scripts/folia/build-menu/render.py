#!/usr/bin/env python3
"""Render the unchanged CraftoUI AddonScreen with captured real Builds tooltip and explicit UI doubles."""
from pathlib import Path
import argparse,subprocess,shutil
p=argparse.ArgumentParser()
for name in ['craftoui','java-home','minecraft','gson','tooltip','output']:p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);cui=a.craftoui.resolve();thin=out/'renderer-source'
for rel in ['client/src/main/java/cc/crafto/ui/client/AddonScreen.java','tools/city-preview/run.py','tools/build-menu-preview/run.py']:
 dest=thin/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(cui/rel,dest)
for folder in ['common/src/main/java/cc/crafto/ui','server/src/main/resources/themes']:shutil.copytree(cui/folder,thin/folder,dirs_exist_ok=True)
shutil.copy2(Path(__file__).with_name('CompactMenuPreviewProbe.java'),thin/'tools/build-menu-preview/BuildMenuPreviewProbe.java')
r=thin/'tools/build-menu-preview/run.py';s=r.read_text().replace('public int getRowCount(){return 6;}','public int getRowCount(){return 3;}')
old='return (T)new ItemLore(java.util.List.of(Component.literal("Материалы сохраняются для здания."),Component.literal("Нужно ещё: 272")));'
new='''try{if(name.equals("Сдать ресурсы")){var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("tooltip.fixture")))).getAsJsonObject();var lines=new java.util.ArrayList<Component>();for(var line:json.getAsJsonArray("lines"))lines.add(Component.literal(line.getAsString()));return (T)new ItemLore(lines);}return (T)new ItemLore(java.util.List.of());}catch(Exception e){throw new RuntimeException(e);}'''
assert old in s;s=s.replace(old,new).replace("'-Djava.awt.headless=true'",repr('-Dtooltip.fixture='+str(a.tooltip.resolve()))+",'-Djava.awt.headless=true'")
r.write_text(s)
subprocess.run(['python3',str(r),'--java-home',str(a.java_home.resolve()),'--minecraft',str(a.minecraft.resolve()),'--gson',str(a.gson.resolve()),'--output',str(out)],check=True)
shutil.copy2(a.tooltip,out/'resource-tooltip.json')
