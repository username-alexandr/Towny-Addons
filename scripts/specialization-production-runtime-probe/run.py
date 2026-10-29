#!/usr/bin/env python3
"""Sequential foreground native runs with immutable input hashes and offline DB-copy inspection."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,sqlite3,shutil,contextlib
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--phase',choices=['first','broken','restart']);a=p.parse_args();server=a.server.resolve();proof=server/'plugins/SpecializationProductionRuntimeProbe';out=server/'validation-specialization';out.mkdir(exist_ok=True)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists() and (a.phase in ['broken','restart'] or not (proof/'first-passed.txt').exists()),'Fresh or explicitly continued disposable fixture required'
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};results=json.loads((out/'native-results.json').read_text()) if a.phase in ['broken','restart'] and (out/'native-results.json').exists() else [];previous=results[-1]['physical'] if results else {}
def snapshot(phase):
 result={};folder=out/(phase+'-db');folder.mkdir()
 for module,name in [('NeverLandTownyContracts','server-materials.db'),('NeverLandTownySpecialization','production.sqlite')]:
  db=server/'plugins'/module/name
  if not db.exists():continue
  assert not Path(str(db)+'-wal').exists() and not Path(str(db)+'-shm').exists(),'Database retained WAL after orderly lifecycle close'
  copied=folder/name;shutil.copy2(db,copied);result[name]={'sha256':hashlib.sha256(copied.read_bytes()).hexdigest(),'bytes':copied.stat().st_size}
  with contextlib.closing(sqlite3.connect('file:'+str(copied)+'?mode=ro',uri=True)) as conn:
   assert conn.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
   if name=='production.sqlite':
    result[name]['batches']=conn.execute('SELECT product,amount,state FROM batches ORDER BY product,amount').fetchall();result[name]['stock']=conn.execute('SELECT product,available,reserved,produced FROM stock ORDER BY product').fetchall();result[name]['projects']=conn.execute('SELECT state FROM joint_projects ORDER BY state').fetchall();result[name]['history_count']=conn.execute('SELECT COUNT(*) FROM history').fetchone()[0];result[name]['audit_pending']=conn.execute('SELECT COUNT(*) FROM history h LEFT JOIN audit_ack a ON a.id=h.id WHERE a.id IS NULL').fetchone()[0]
   else:result[name]['balances']=conn.execute('SELECT * FROM balances ORDER BY material').fetchall();result[name]['projects']=conn.execute('SELECT state FROM cooperative_projects ORDER BY id').fetchall();result[name]['deliveries']=conn.execute('SELECT state FROM deliveries ORDER BY id').fetchall()
 return result
for phase in [a.phase] if a.phase else ['first','broken','restart']:
 for filename,entry in previous.items():
  module='NeverLandTownyContracts' if filename.endswith('.db') else 'NeverLandTownySpecialization';db=server/'plugins'/module/filename;assert hashlib.sha256(db.read_bytes()).hexdigest()==entry['sha256'],'Database changed between JVMs'
 before=snapshot(phase+'-before-jvm');(out/(phase+'-before-jvm.json')).write_text(json.dumps(before,indent=2)+'\n');started=time.time();cmd=[str(a.java_home.resolve()/'bin/java'),'-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1700M','-Dterminal.jline=false','-Dterminal.ansi=false']
 if (server/'cacerts').exists():cmd+=['-Djavax.net.ssl.trustStore='+str(server/'cacerts')]
 cmd+=['-jar','server.jar','--nogui']
 with (out/(phase+'.log')).open('w') as stream:
  proc=subprocess.Popen(cmd,cwd=server,stdout=stream,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True)
  try:proc.wait(timeout=220)
  except subprocess.TimeoutExpired:
   proc.stdin.write('stop\n');proc.stdin.flush()
   try:proc.wait(timeout=35)
   except subprocess.TimeoutExpired:proc.kill();proc.wait()
   raise SystemExit('Native probe timed out; '+str(out/(phase+'.log')))
 log=(out/(phase+'.log')).read_text();passed=proof/(phase+'-passed.txt');assert proc.returncode==0 and passed.is_file() and passed.stat().st_mtime>=started and not (proof/'failed.txt').exists(),str(out/(phase+'.log'))
 assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','ADMISSIONS PROBE FAILED'])
 assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[SpecializationProductionRuntimeProbe]' in line) for line in log.splitlines()),'Unexpected addon error'
 physical=snapshot(phase);special=physical['production.sqlite'];contracts=physical['server-materials.db'];assert special['stock']==[('farm_provisions',0 if phase=='first' else 1,0,2 if phase=='first' else 3),('port_equipment',0,0,2)],special
 assert special['projects']==[('CANCELLED',),('COMPLETED',)] and special['audit_pending']==0,special
 assert contracts['balances']==[('CARROT',192,0),('OAK_LOG',256,0),('WHEAT',384,0),('WHITE_WOOL',128,0)],contracts
 assert len(contracts['deliveries'])==6 and contracts['projects']==[('COMPLETED',)]*3,contracts
 results.append({'phase':phase,'result':passed.read_text(),'exit_code':proc.returncode,'jar_sha256':jars,'physical':physical});previous=physical;(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n');print(phase+': '+passed.read_text().strip(),flush=True)
