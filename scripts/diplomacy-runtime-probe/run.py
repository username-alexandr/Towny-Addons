"""Run exactly one phase synchronously; invoke first and restart in separate calls."""
import argparse, subprocess
from pathlib import Path
import yaml
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-dir',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--phase',choices=['first','restart'],required=True)
a=p.parse_args();s=a.server_dir.resolve();proof=s/'plugins/NeverLandDiplomacyRuntimeProbe'
if not (s/'ALLOW_DISPOSABLE_RELIABILITY_PROBE').is_file():raise SystemExit('Disposable server marker required')
if not (s/'plugins/NeverLandDiplomacyRuntimeProbe.jar').is_file():raise SystemExit('Compile and install the probe first')
if (proof/'failed.txt').exists() or (proof/(a.phase+'-passed.txt')).exists():raise SystemExit('Existing proof: archive and explicitly restore a disposable snapshot first')
if a.phase=='restart' and not (proof/'first-passed.txt').exists():raise SystemExit('First phase must pass before restart')
with (s/('diplomacy-'+a.phase+'.log')).open('w') as log:
 result=subprocess.run([str(a.java_home.resolve()/'bin/java'),'-XX:ActiveProcessorCount=2','-Xms512M','-Xmx2G','-Dterminal.jline=false','-Dterminal.ansi=false','-Dneverland.runtimeProbe=true','-jar',next(s.glob('purpur-*.jar')).name,'--nogui'],cwd=s,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,timeout=120)
if (proof/'failed.txt').exists():raise SystemExit((proof/'failed.txt').read_text())
if result.returncode!=0 or not (proof/(a.phase+'-passed.txt')).exists():raise SystemExit('Missing successful proof; inspect diplomacy-'+a.phase+'.log')
y=yaml.safe_load((s/'plugins/NeverLandTownyDiplomacy/diplomacy.yml').read_text())
if not y['treaties'] or len(y['incidents'])!=1:raise SystemExit('Stopped server lost committed diplomacy state')
print(a.phase+': '+(proof/(a.phase+'-passed.txt')).read_text().strip()+f"; on-disk treaties: {len(y['treaties'])}; incidents: 1",flush=True)
