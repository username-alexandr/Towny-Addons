#!/usr/bin/env python3
"""Compile the explicit opt-in fixture against real server/addon dependencies."""
from pathlib import Path
import argparse, os, subprocess
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--server',type=Path,required=True)
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--classpath-file',type=Path,required=True)
a=p.parse_args();server=a.server.resolve();src=Path(__file__).resolve().parent
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file()
classes=server/'probe-classes';classes.mkdir(exist_ok=True)
classpath=a.classpath_file.read_text().strip()+os.pathsep+os.pathsep.join(map(str,(server/'plugins').glob('*.jar')))
subprocess.run([str(a.java_home/'bin/javac'),'-encoding','UTF-8','-classpath',classpath,'-d',str(classes),str(src/'TntRuntimeProbe.java')],check=True)
subprocess.run([str(a.java_home/'bin/jar'),'--create','--file',str(server/'plugins/TntRuntimeProbe.jar'),'-C',str(classes),'.','-C',str(src),'plugin.yml'],check=True)
