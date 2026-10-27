#!/usr/bin/env python3
"""Two native boots. Never run on an unmarked, existing production world."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,sqlite3,shutil
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True)
a=p.parse_args();server=a.server.resolve();proof=server/'plugins/TntRuntimeProbe';out=server/'validation';out.mkdir(exist_ok=True)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists() and not (proof/'first-passed.txt').exists(), 'Use a fresh disposable snapshot'
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};results=[]
for phase in ['first','restart']:
    started=time.time();command=[str(a.java_home/'bin/java'),'-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1700M','-Dterminal.jline=false','-Dterminal.ansi=false']
    if (server/'cacerts').exists():command+=['-Djavax.net.ssl.trustStore='+str(server/'cacerts')]
    command+=['-jar','server.jar','--nogui']
    proc=subprocess.Popen(command,cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    try:log,_=proc.communicate(timeout=150)
    except subprocess.TimeoutExpired:proc.kill();log,_=proc.communicate();(out/f'{phase}.log').write_text(log);raise SystemExit('Native TNT probe timed out')
    (out/f'{phase}.log').write_text(log);passed=proof/(phase+'-passed.txt')
    if proc.returncode or not passed.exists() or passed.stat().st_mtime<started or (proof/'failed.txt').exists():raise SystemExit('Native TNT probe failed: '+str(out/f'{phase}.log'))
    assert not any(s in log for s in ['FoliaWatchdogThread','Error occurred while disabling','Error occurred while enabling','Could not load plugin'])
    results.append(dict(phase=phase,exit_code=proc.returncode,result=passed.read_text(),jar_sha256=jars));(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n')
    if phase=='first':
        database=server/'plugins/NeverLandTownySiegesPlus/raids.db'
        with sqlite3.connect(database) as db:
            pending=db.execute('SELECT id,state,cents FROM building_repairs').fetchall()
        (out/'first-repair-checkpoint.json').write_text(json.dumps(pending))
        shutil.copy2(database,out/'first-closed-database.db')
        assert pending, 'Repair intent lost during native shutdown'
    print(phase+': '+passed.read_text().strip(),flush=True)
