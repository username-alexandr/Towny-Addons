#!/usr/bin/env python3
"""Compile native probe against exact copied addon JARs and the patched Folia runtime."""
from pathlib import Path
import argparse,os,shutil,subprocess
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True)
a=p.parse_args();server=a.server.resolve();java=a.java_home.resolve()/'bin';src=Path(__file__).resolve().parent
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file()
classes=server/'probe-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
cp=a.classpath_file.read_text().strip()+os.pathsep+os.pathsep.join(map(str,(server/'plugins').glob('*.jar')))+os.pathsep+str(server/'versions/26.2/folia-26.2.jar')
subprocess.run([str(java/'javac'),'-encoding','UTF-8','-classpath',cp,'-d',str(classes),str(src.parent/'tnt-runtime-probe/TntRuntimeProbe.java'),str(src/'ReputationExplainProbe.java')],check=True)
subprocess.run([str(java/'jar'),'--create','--file',str(server/'plugins/TntRuntimeProbe.jar'),'-C',str(classes),'.','-C',str(src),'plugin.yml'],check=True)
