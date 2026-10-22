"""Compile the probe in an already bootstrapped, isolated Folia server directory."""
import os, subprocess, sys
from pathlib import Path
server=Path(sys.argv[1]).resolve();root=Path(__file__).resolve().parent
java=Path(os.environ['JAVA_HOME'])/'bin';out=root/'build';out.mkdir(exist_ok=True)
cp=os.pathsep.join(str(p) for parent in [server/'libraries',server/'plugins'] for p in parent.rglob('*.jar'))
subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(out),str(root/'DeletionProbe.java')],check=True)
(out/'plugin.yml').write_text('name: DeletionProbe\nversion: 1.0\nmain: DeletionProbe\napi-version: "26.2"\nfolia-supported: true\ndepend: [Towny, NeverLandTownyControl, NeverLandTownyBuilds]\n')
subprocess.run([str(java/'jar'),'cf',str(server/'plugins/DeletionProbe.jar'),'-C',str(out),'.'],check=True)
