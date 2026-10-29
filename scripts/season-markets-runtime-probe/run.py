#!/usr/bin/env python3
"""Serial foreground JVMs only. Caller must acquire the shared native window before running."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,sqlite3,shutil,contextlib,threading,sys
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--phase',choices=['first','broken','restart','all'],required=True);p.add_argument('--trace-sqlite',action='store_true');p.add_argument('--observe-sqlite',action='store_true');a=p.parse_args();server=a.server.resolve();phase=a.phase;proof=server/'plugins/SeasonMarketsRuntimeProbe';out=server/'validation-season-markets';out.mkdir(exist_ok=True)
if phase=='all':
 # Keep the filesystem namespace continuous across complete, strictly serial JVM restarts.
 for step in ['first','broken','restart']:
  command=[sys.executable,str(Path(__file__).resolve()),'--server',str(server),'--java-home',str(a.java_home.resolve()),'--phase',step]
  if a.trace_sqlite:command+=['--trace-sqlite']
  if a.observe_sqlite:command+=['--observe-sqlite']
  subprocess.run(command,check=True)
 raise SystemExit(0)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists() and not (proof/(phase+'-passed.txt')).exists(),'Refusing ambiguous rerun'
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};results=json.loads((out/'native-results.json').read_text()) if (out/'native-results.json').exists() else []
def stopped_files():
 image={}
 for module,name in [('NeverLandTownyContracts','server-materials.db'),('NeverLandTownySpecialization','production.sqlite')]:
  db=server/'plugins'/module/name
  if not db.exists():continue
  paths=[db]+[Path(str(db)+suffix) for suffix in ['-wal','-shm','-journal']]
  image[name]={p.name:{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size} for p in paths if p.exists()}
 return image
before=stopped_files();(out/(phase+'-before-files.json')).write_text(json.dumps(before,indent=2)+'\n')
assert all(list(files)==[name] for name,files in before.items()),'Stopped fixture retained a SQLite sidecar; preserve all files for diagnosis'
if results:
 for name,row in results[-1]['physical'].items():
  module='NeverLandTownyContracts' if name.endswith('.db') else 'NeverLandTownySpecialization'
  assert hashlib.sha256((server/'plugins'/module/name).read_bytes()).hexdigest()==row['sha256'],'DB changed between JVMs'
started=time.time();cmd=[str(a.java_home.resolve()/'bin/java'),'-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1700M','-Dterminal.jline=false','-Dterminal.ansi=false']
if (server/'cacerts').exists():cmd+=['-Djavax.net.ssl.trustStore='+str(server/'cacerts')]
cmd+=['-jar','server.jar','--nogui']
if a.trace_sqlite:
 trace=['strace','-f','-yy','-o',str(out/(phase+'-sqlite.strace')),'-e','trace=openat,close,unlink,unlinkat,rename,renameat,ftruncate,pwrite64,fsync,fdatasync']
 base=server/'plugins/NeverLandTownySpecialization/production.sqlite'
 for suffix in ['','-wal','-shm','-journal']:trace+=['-P',str(base)+suffix]
 cmd=trace+cmd
observer_stop=threading.Event();observer=None
if a.observe_sqlite:
 def watch():
  # Diagnostic raw reads only: never open an original database through SQLite.
  paths=[Path(str(server/'plugins/NeverLandTownySpecialization/production.sqlite')+suffix) for suffix in ['','-wal','-shm','-journal']];previous={}
  with (out/(phase+'-file-observer.jsonl')).open('w') as stream:
   while not observer_stop.is_set():
    for path in paths:
     try:
      stat=path.stat();signature=(stat.st_ino,stat.st_size,stat.st_mtime_ns,stat.st_ctime_ns)
      if previous.get(path)==signature:continue
      previous[path]=signature;data=path.read_bytes();row={'at':time.time_ns(),'file':path.name,'inode':stat.st_ino,'size':len(data),'sha256':hashlib.sha256(data).hexdigest(),'header':data[:100].hex()}
     except FileNotFoundError:
      if path not in previous or previous[path] is None:continue
      previous[path]=None;row={'at':time.time_ns(),'file':path.name,'absent':True}
     stream.write(json.dumps(row)+'\n');stream.flush()
    observer_stop.wait(0.01)
 observer=threading.Thread(target=watch,daemon=True);observer.start()
with (out/(phase+'.log')).open('w') as stream:
 proc=subprocess.Popen(cmd,cwd=server,stdout=stream,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True)
 try:proc.wait(timeout=240)
 except subprocess.TimeoutExpired:
  proc.stdin.write('stop\n');proc.stdin.flush()
  try:proc.wait(timeout=35)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
  raise SystemExit('Native probe timed out')
observer_stop.set()
if observer is not None:observer.join(timeout=2)
after=stopped_files();(out/(phase+'-after-files.json')).write_text(json.dumps(after,indent=2)+'\n')
log=(out/(phase+'.log')).read_text();passed=proof/(phase+'-passed.txt');assert proc.returncode==0 and passed.is_file() and passed.stat().st_mtime>=started and not (proof/'failed.txt').exists(),str(out/(phase+'.log'))
assert all(list(files)==[name] for name,files in after.items()),'Lifecycle retained a SQLite sidecar; preserve all files for diagnosis'
assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','ADMISSIONS PROBE FAILED'])
assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[SeasonMarketsRuntimeProbe]' in line) for line in log.splitlines())
physical={};folder=out/(phase+'-db');folder.mkdir()
for module,name in [('NeverLandTownyContracts','server-materials.db'),('NeverLandTownySpecialization','production.sqlite')]:
 db=server/'plugins'/module/name
 assert not Path(str(db)+'-wal').exists() and not Path(str(db)+'-shm').exists(),'Lifecycle failed to close WAL'
 copied=folder/name;shutil.copy2(db,copied);row={'sha256':hashlib.sha256(copied.read_bytes()).hexdigest(),'bytes':copied.stat().st_size};physical[name]=row
 with contextlib.closing(sqlite3.connect('file:'+str(copied)+'?mode=ro&immutable=1',uri=True)) as c:
  assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
  if name=='production.sqlite':
   row['schema']=c.execute('PRAGMA user_version').fetchone()[0];row['stock']=c.execute('SELECT town,product,available,reserved,produced,received,sent FROM stock ORDER BY town,product').fetchall();row['entries']=c.execute('SELECT kind,state,receipt FROM seasonal_entries ORDER BY created').fetchall();row['history_count']=c.execute('SELECT COUNT(*) FROM history').fetchone()[0];row['audit_pending']=c.execute('SELECT COUNT(*) FROM history h LEFT JOIN audit_ack a ON a.id=h.id WHERE a.id IS NULL').fetchone()[0]
   assert row['schema']==2 and row['audit_pending']==0 and sum(s[4] for s in row['stock'])==16
   assert sum(s[2]+s[3] for s in row['stock'])==16 and sum(s[5] for s in row['stock'])==sum(s[6] for s in row['stock'])==3
   assert len([e for e in row['entries'] if e[1]=='SWAPPED'])==1
  else:
   row['projects']=c.execute('SELECT state FROM cooperative_projects ORDER BY id').fetchall();row['deliveries']=c.execute('SELECT state FROM deliveries ORDER BY id').fetchall();row['balances']=c.execute('SELECT * FROM balances ORDER BY material').fetchall();assert row['projects']==[('COMPLETED',)]*2 and len(row['deliveries'])==4
results.append({'phase':phase,'result':passed.read_text(),'exit_code':proc.returncode,'jar_sha256':jars,'physical':physical});(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n');print(phase+': '+passed.read_text().strip(),flush=True)
