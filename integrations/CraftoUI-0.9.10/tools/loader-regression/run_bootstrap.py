#!/usr/bin/env python3
"""Actual Fabric 0.19.4/Knot bootstrap. A test-only preLaunch mod exits before Minecraft's window.

No authentication, server connection or game simulation. Never distribute the test-only JAR.
"""
from pathlib import Path
import argparse,json,os,subprocess,zipfile
p=argparse.ArgumentParser()
for name in ('java-home','minecraft','loader-dir','fabric-api','craftoui-jar','output'):p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True)
loader=a.loader_dir.resolve()/'fabric-loader-0.19.4.jar';mc=a.minecraft.resolve()
subprocess.run([str(a.java_home/'bin/javac'),'-proc:none','-cp',str(loader),'-d',str(classes),str(Path(__file__).with_name('BootstrapProbe.java'))],check=True)
probe=out/'TEST-ONLY-bootstrap.jar'
with zipfile.ZipFile(probe,'w') as z:
    z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'craftoui_bootstrap_probe','version':'1.0.0','environment':'client','entrypoints':{'preLaunch':['cc.crafto.ui.probe.BootstrapProbe']}}))
    for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
cp=[loader,mc/'minecraft-26.2.jar',*sorted((mc/'libraries').rglob('*.jar')),
    *sorted(a.loader_dir.glob('asm*.jar')),*a.loader_dir.glob('sponge-mixin*.jar')]
mods=[a.craftoui_jar.resolve(),a.fabric_api.resolve(),probe]
runtime=out/'runtime';runtime.mkdir(exist_ok=True)
cmd=[str(a.java_home/'bin/java'),'-Dfabric.gameVersion=26.2','-Dfabric.addMods='+os.pathsep.join(map(str,mods)),
     '-cp',os.pathsep.join(map(str,cp)),'net.fabricmc.loader.impl.launch.knot.KnotClient',
     '--gameDir',str(runtime),'--version','26.2','--username','CraftoBootstrapTest','--accessToken','0']
r=subprocess.run(cmd,cwd=runtime,capture_output=True,text=True,timeout=60)
text=r.stdout+r.stderr;(out/'bootstrap.log').write_text(text)
print(text,flush=True)
assert r.returncode==0,'Knot did not complete the bootstrap probe'
assert 'PASS actual Knot bootstrap selected fabricloader 0.19.4' in text
assert 'PASS actual Knot bootstrap selected mixinextras 0.5.5' in text
assert 'PASS ContainerEventHandler transformed before the game window' in text
