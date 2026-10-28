#!/usr/bin/env python3
"""Two opt-in Folia boots, current artifact hashes, durable receipts and fresh pass markers."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,sqlite3
from prepare import models
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True)
a=p.parse_args();a.java_home=a.java_home.resolve();server=a.server.resolve();proof=server/'plugins/TntRuntimeProbe';out=server/'validation';out.mkdir(exist_ok=True)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists() and not (proof/'first-passed.txt').exists(), 'Use a fresh disposable snapshot'
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};results=[]
for phase in ['first','restart']:
    if phase=='restart':models(server,changed=True)
    started=time.time();command=[str(a.java_home/'bin/java'),'-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1700M','-Dterminal.jline=false','-Dterminal.ansi=false']
    if (server/'cacerts').exists():command+=['-Djavax.net.ssl.trustStore='+str(server/'cacerts')]
    command+=['-jar','server.jar','--nogui']
    with (out/f'{phase}.log').open('w') as stream:
        proc=subprocess.Popen(command,cwd=server,stdin=subprocess.PIPE,stdout=stream,stderr=subprocess.STDOUT,text=True)
        try:proc.wait(timeout=240)
        except subprocess.TimeoutExpired:proc.kill();proc.wait();raise SystemExit('Native schematic probe timed out')
    log=(out/f'{phase}.log').read_text();passed=proof/(phase+'-passed.txt')
    if proc.returncode or not passed.exists() or passed.stat().st_mtime<started or (proof/'failed.txt').exists():raise SystemExit('Native schematic probe failed: '+str(out/f'{phase}.log'))
    assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','Could not load plugin','TNT PROBE FAILED'])
    assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[TntRuntimeProbe]' in line) for line in log.splitlines()), 'Addon error in native log'
    results.append(dict(phase=phase,exit_code=proc.returncode,result=passed.read_text(),jar_sha256=jars));(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(phase+': '+passed.read_text().strip(),flush=True)
