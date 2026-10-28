#!/usr/bin/env python3
"""Build the opt-in schematic import probe. Needs real patched Folia server classes, not just API."""
from pathlib import Path
import argparse, os, shutil, subprocess
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--server',type=Path,required=True)
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--classpath-file',type=Path,required=True)
a=p.parse_args();a.java_home=a.java_home.resolve();server=a.server.resolve();src=Path(__file__).resolve().parent
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file()
classes=server/'probe-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
classpath=a.classpath_file.read_text().strip()+os.pathsep+os.pathsep.join(map(str,(server/'plugins').glob('*.jar')))+os.pathsep+str(server/'versions/26.2/folia-26.2.jar')
subprocess.run([str(a.java_home/'bin/javac'),'-encoding','UTF-8','-classpath',classpath,'-d',str(classes),str(src.parent/'tnt-runtime-probe/TntRuntimeProbe.java'),str(src.parent/'forecast-runtime-probe/ForecastProbe.java'),str(src/'TaxPaymentsProbe.java')],check=True)
subprocess.run([str(a.java_home/'bin/jar'),'--create','--file',str(server/'plugins/TntRuntimeProbe.jar'),'-C',str(classes),'.','-C',str(src),'plugin.yml'],check=True)
