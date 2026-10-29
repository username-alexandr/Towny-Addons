#!/usr/bin/env python3
"""Three real Folia boots, exact artifacts, lifecycle errors and fresh assertion markers."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,yaml,sqlite3,contextlib,shutil
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--trace-sqlite',action='store_true');p.add_argument('--phase',choices=['first','broken','restart'])
a=p.parse_args();server=a.server.resolve();proof=server/'plugins/CooperativeQuestsRuntimeProbe';out=server/'validation';out.mkdir(exist_ok=True)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists() and (a.phase in ['broken','restart'] or not (proof/'first-passed.txt').exists()), 'Use a fresh or correctly continued disposable fixture'
assert (server/'plugins/CooperativeQuestsRuntimeProbe.jar').is_file(), 'Compile the native probe before running'
assert yaml.safe_load((server/'plugins/bStats/config.yml').read_text())['enabled'] is False
assert 'enabled=false' in (server/'plugins/faststats/config.properties').read_text()
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};results=json.loads((out/'native-results.json').read_text()) if a.phase in ['broken','restart'] and (out/'native-results.json').exists() else []
def raw_snapshot(database, folder):
    folder.mkdir()
    result={'files':{}}
    for f in sorted(database.parent.glob('server-materials.db*')):
        data=f.read_bytes();stat=f.stat()
        result['files'][f.name]={'size':len(data),'sha256':hashlib.sha256(data).hexdigest(),'inode':stat.st_ino,'mtime_ns':stat.st_mtime_ns}
        (folder/f.name).write_bytes(data)
    return result
for phase in [a.phase] if a.phase else ['first','broken','restart']:
    started=time.time();command=[str(a.java_home.resolve()/'bin/java'),'-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1700M','-Dterminal.jline=false','-Dterminal.ansi=false']
    if (server/'cacerts').exists():command+=['-Djavax.net.ssl.trustStore='+str(server/'cacerts')]
    command+=['-jar','server.jar','--nogui']
    if a.trace_sqlite:
        database=server/'plugins/NeverLandTownyContracts/server-materials.db'
        trace=['strace','-f','-tt','-qq','-yy','-e','trace=%file,fsync,fdatasync,ftruncate,write,pwrite64,close','-o',str(out/(phase+'-sqlite.strace'))]
        for f in [database,Path(str(database)+'-wal'),Path(str(database)+'-shm'),database.parent]:trace+=['-P',str(f)]
        command=trace+command
    database=server/'plugins/NeverLandTownyContracts/server-materials.db'
    before=raw_snapshot(database,out/(phase+'-before-jvm-raw'))
    (out/(phase+'-before-jvm.json')).write_text(json.dumps(before,indent=2)+'\n')
    with (out/f'{phase}.log').open('w') as stream:
        proc=subprocess.Popen(command,cwd=server,stdout=stream,stderr=subprocess.STDOUT,text=True,stdin=subprocess.PIPE)
        try:proc.wait(timeout=240)
        except subprocess.TimeoutExpired:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:proc.wait(timeout=45)
            except subprocess.TimeoutExpired:proc.kill();proc.wait()
            raise SystemExit('Native cooperative quests probe timed out')
    database=server/'plugins/NeverLandTownyContracts/server-materials.db'
    raw=out/(phase+'-sqlite-raw');physical=raw_snapshot(database,raw)
    inspection=out/(phase+'-sqlite-inspect');shutil.copytree(raw,inspection)
    with contextlib.closing(sqlite3.connect('file:'+str(inspection/'server-materials.db')+'?mode=ro',uri=True)) as db:
        physical['balances']=db.execute('SELECT * FROM balances').fetchall();physical['projects']=db.execute('SELECT id,state FROM cooperative_projects').fetchall();physical['deliveries']=db.execute('SELECT id,state FROM deliveries').fetchall()
    (out/(phase+'-sqlite.json')).write_text(json.dumps(physical,indent=2)+'\n')
    assert sorted(physical['balances'])==[('GLASS',64,0),('OAK_LOG',244,116)], 'Physical SQLite lost acknowledged quantities after JVM exit'
    assert len(physical['projects'])==2 and len(physical['deliveries'])==4, 'Physical SQLite lost acknowledged project/delivery rows'
    log=(out/f'{phase}.log').read_text();passed=proof/(phase+'-passed.txt')
    if proc.returncode or not passed.exists() or passed.stat().st_mtime<started or (proof/'failed.txt').exists():raise SystemExit('Native cooperative quests probe failed: '+str(out/f'{phase}.log'))
    assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','Could not load plugin','ADMISSIONS PROBE FAILED'])
    assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[CooperativeQuestsRuntimeProbe]' in line) for line in log.splitlines()), 'Unexpected addon error in native log'
    results.append(dict(phase=phase,exit_code=proc.returncode,result=passed.read_text(),jar_sha256=jars));(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(phase+': '+passed.read_text().strip(),flush=True)
