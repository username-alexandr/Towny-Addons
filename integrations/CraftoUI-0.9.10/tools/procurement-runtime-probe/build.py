"""Compile only the opt-in UI fixture, reusing the explicit Towny-Addons banking/player harness."""
from pathlib import Path
import argparse,os,subprocess,shutil
p=argparse.ArgumentParser(description=__doc__)
for key in ['server','java-home','addons-root']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();server=a.server.resolve();source=Path(__file__).resolve().parent
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file()
build=server/'craftoui-procurement-probe-build';build.mkdir(exist_ok=True);classes=build/'classes';classes.mkdir(exist_ok=True)
base=(a.addons_root/'scripts/procurement-runtime-probe/ProcurementProbe.java').read_text()
assert 'public final class ProcurementProbe extends JavaPlugin' in base
# Fixture-only subclassing; production addon and UI JARs stay byte-for-byte unchanged.
(build/'ProcurementProbe.java').write_text(base.replace('public final class ProcurementProbe extends JavaPlugin','public class ProcurementProbe extends JavaPlugin',1))
jars=sorted((server/'versions').rglob('*.jar'))+sorted((server/'libraries').rglob('*.jar'))+sorted((server/'plugins').glob('*.jar'))
jars=[j for j in jars if j.name!='CraftoUIProcurementProbe.jar']
subprocess.run([str(a.java_home.resolve()/'bin/javac'),'-encoding','UTF-8','-d',str(classes),'-cp',os.pathsep.join(map(str,jars)),str(build/'ProcurementProbe.java'),str(source/'ProcurementUiProbe.java')],check=True)
shutil.copy2(source/'plugin.yml',classes/'plugin.yml')
subprocess.run([str(a.java_home.resolve()/'bin/jar'),'--create','--file',str(server/'plugins/CraftoUIProcurementProbe.jar'),'-C',str(classes),'.'],check=True)
