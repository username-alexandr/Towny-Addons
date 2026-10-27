#!/usr/bin/env python3
"""Two boots of a fresh, marked loopback fixture, collecting output only after each process exits."""
from pathlib import Path
import argparse,subprocess,os,time,json,hashlib
p=argparse.ArgumentParser();p.add_argument('server',type=Path);a=p.parse_args();root=a.server.resolve()
assert (root/'ALLOW_DISPOSABLE_PLACEMENT_PROBE').is_file() and 'server-ip=127.0.0.1' in (root/'server.properties').read_text()
results=[];proof=root/'plugins/PlacementRuntimeProbe';out=root/'validation';out.mkdir(exist_ok=True)
hashes={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (root/'plugins').glob('*.jar') if f.name.startswith(('NeverLandTowny','CraftoUI-'))}
for phase,file in [('initial','first-passed.txt'),('restart','restart-passed.txt')]:
 started=time.time();p=subprocess.Popen(['java','-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1800M','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','server.jar','--nogui'],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 try:log,_=p.communicate(timeout=180)
 except subprocess.TimeoutExpired:p.kill();log,_=p.communicate();(out/f'{phase}-console.log').write_text(log);raise SystemExit('Probe timed out')
 (out/f'{phase}-console.log').write_text(log)
 result=proof/file
 if p.returncode!=0 or not result.exists() or result.stat().st_mtime<started or (proof/'failed.txt').exists():raise SystemExit('Probe failed; see '+str(out/f'{phase}-console.log'))
 assert not any(s in log for s in ['FoliaWatchdogThread','Error occurred while disabling','Error occurred while enabling','Could not load plugin'])
 results.append({'phase':phase,'exit_code':p.returncode,'result':result.read_text(),'watchdog':False,'jar_sha256':hashes});(out/'results.json').write_text(json.dumps(results,indent=2)+'\n');print('PASS native placement '+phase+': '+result.read_text().strip(),flush=True)
