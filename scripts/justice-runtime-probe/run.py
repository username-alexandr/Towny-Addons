"""Run exactly one phase synchronously; invoke first and restart in separate calls."""
import argparse, subprocess
from pathlib import Path
import yaml, zipfile, collections, hashlib, json
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-dir',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--phase',choices=['first','restart'],required=True)
a=p.parse_args();s=a.server_dir.resolve();proof=s/'plugins/NeverLandJusticeRuntimeProbe'
if not (s/'ALLOW_DISPOSABLE_RELIABILITY_PROBE').is_file():raise SystemExit('Disposable server marker required')
if not (s/'plugins/NeverLandJusticeRuntimeProbe.jar').is_file():raise SystemExit('Compile and install the probe first')
if (proof/'failed.txt').exists() or (proof/(a.phase+'-passed.txt')).exists():raise SystemExit('Existing proof: archive and explicitly restore a disposable snapshot first')
if a.phase=='restart' and not (proof/'first-passed.txt').exists():raise SystemExit('First phase must pass before restart')
expected=yaml.safe_load((Path(__file__).resolve().parents[2]/'versions.yml').read_text())['addons']
found=collections.defaultdict(list); hashes={}
for jar in (s/'plugins').glob('*.jar'):
    with zipfile.ZipFile(jar) as z:
        if 'plugin.yml' not in z.namelist(): continue
        descriptor=yaml.safe_load(z.read('plugin.yml'))
    name=descriptor['name']
    if name.startswith('NeverLandTowny'):
        found[name].append(str(descriptor['version'])); hashes[jar.name]=hashlib.sha256(jar.read_bytes()).hexdigest()
if dict(found)!={n:[v] for n,v in expected.items()}:
    raise SystemExit('Installed addon names/versions must exactly match versions.yml; remove duplicate old JARs')
(s/('justice-'+a.phase+'-jars.json')).write_text(json.dumps(hashes,indent=2)+'\n')
with (s/('justice-'+a.phase+'.log')).open('w') as log:
 result=subprocess.run([str(a.java_home.resolve()/'bin/java'),'-XX:ActiveProcessorCount=2','-Xms512M','-Xmx2G','-Dterminal.jline=false','-Dterminal.ansi=false','-Dneverland.runtimeProbe=true','-jar',next(s.glob('purpur-*.jar')).name,'--nogui'],cwd=s,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,timeout=120)
if (proof/'failed.txt').exists():raise SystemExit((proof/'failed.txt').read_text())
if result.returncode!=0 or not (proof/(a.phase+'-passed.txt')).exists():raise SystemExit('Missing successful proof; inspect justice-'+a.phase+'.log')
print(a.phase+': '+(proof/(a.phase+'-passed.txt')).read_text().strip(),flush=True)
