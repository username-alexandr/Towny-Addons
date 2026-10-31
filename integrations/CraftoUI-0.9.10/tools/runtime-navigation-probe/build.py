#!/usr/bin/env python3
import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--server-dir',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
source=Path(__file__).resolve().parent;root=a.server_dir.resolve();out=root/'navigation-probe-build';out.mkdir(exist_ok=True)
cp=os.pathsep.join(str(f) for folder in ('libraries','versions','plugins') for f in (root/folder).rglob('*.jar') if 'NavigationProbe' not in f.name)
subprocess.run([str(a.java_home/'bin/javac'),'--release','25','-encoding','UTF-8','-cp',cp,'-d',str(out),str(source/'NavigationProbe.java')],check=True)
subprocess.run([str(a.java_home/'bin/jar'),'--create','--file',str(root/'plugins/NavigationProbe-TEST-ONLY.jar'),'-C',str(out),'.','-C',str(source),'plugin.yml'],check=True)
