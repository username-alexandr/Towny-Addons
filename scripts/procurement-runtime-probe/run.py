#!/usr/bin/env python3
"""One foreground Folia boot per call; exact artifacts, independent offline copies and corruption isolation."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,sqlite3,shutil,contextlib,sys
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--phase',choices=['first','broken','restart','all'],required=True);a=p.parse_args();server=a.server.resolve();proof=server/'plugins/ProcurementRuntimeProbe';out=server/'validation-procurement';out.mkdir(exist_ok=True);phase=a.phase
if phase=='all':
 for part in ['first','broken','restart']:
  subprocess.run([sys.executable,str(Path(__file__).resolve()),'--server',str(server),'--java-home',str(a.java_home.resolve()),'--phase',part],check=True)
 raise SystemExit(0)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists() and not (proof/(phase+'-passed.txt')).exists(),'Fresh or explicitly continued disposable fixture required'
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};resultfile=out/'native-results.json';results=json.loads(resultfile.read_text()) if resultfile.exists() else [];ledger=server/'plugins/NeverLandTownyContracts/procurement.sqlite';preserved=ledger.with_suffix('.preserved')
if results:assert results[-1]['jar_sha256']==jars,'Do not change artifacts between native phases'
def physical(label):
 folder=out/label;folder.mkdir();value={}
 for relative in ['NeverLandTownyContracts/procurement.sqlite','NeverLandTownyContracts/contract-data.yml','NeverLandTownyTreasuryPlus/treasury-data.yml']:
  path=server/'plugins'/relative
  if not path.is_file():continue
  if path.suffix=='.sqlite':assert not Path(str(path)+'-wal').exists() and not Path(str(path)+'-shm').exists(),'Unclosed SQLite sidecar'
  dest=folder/path.name;shutil.copy2(path,dest);value[relative]={'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'bytes':dest.stat().st_size}
  if path.suffix=='.sqlite':
   with contextlib.closing(sqlite3.connect('file:'+str(dest)+'?mode=ro&immutable=1',uri=True)) as conn:
    assert conn.execute('PRAGMA integrity_check').fetchone()[0]=='ok';value[relative]['requests']=conn.execute('SELECT id,state,amount,cents FROM requests ORDER BY id').fetchall();value[relative]['history']=conn.execute('SELECT COUNT(*) FROM history').fetchone()[0];value[relative]['audit_pending']=conn.execute('SELECT COUNT(*) FROM history h LEFT JOIN audit_ack a ON a.id=h.id WHERE a.id IS NULL').fetchone()[0]
 return value
if phase=='broken':
 assert results[-1]['phase']=='first' and ledger.is_file() and not preserved.exists()
 assert hashlib.sha256(ledger.read_bytes()).hexdigest()==results[-1]['physical']['NeverLandTownyContracts/procurement.sqlite']['sha256']
 assert not Path(str(ledger)+'-wal').exists() and not Path(str(ledger)+'-shm').exists(),'Unclosed SQLite sidecar before fixture corruption'
 ledger.rename(preserved);ledger.mkdir()  # Explicit fixture corruption. Original durable bytes remain untouched.
elif phase=='restart':
 assert results[-1]['phase']=='broken' and ledger.is_dir() and preserved.is_file()
 ledger.rmdir();preserved.rename(ledger)
 assert hashlib.sha256(ledger.read_bytes()).hexdigest()==results[0]['physical']['NeverLandTownyContracts/procurement.sqlite']['sha256']
before=physical(phase+'-before');(out/(phase+'-before.json')).write_text(json.dumps(before,indent=2)+'\n')
started=time.time();cmd=[str(a.java_home.resolve()/'bin/java'),'-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1700M','-Dterminal.jline=false','-Dterminal.ansi=false']
if (server/'cacerts').exists():cmd+=['-Djavax.net.ssl.trustStore='+str(server/'cacerts')]
cmd+=['-jar','server.jar','--nogui']
with (out/(phase+'.log')).open('w') as stream:
 proc=subprocess.Popen(cmd,cwd=server,stdout=stream,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True)
 try:proc.wait(timeout=220)
 except subprocess.TimeoutExpired:
  proc.stdin.write('stop\n');proc.stdin.flush()
  try:proc.wait(timeout=35)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
  raise SystemExit('Native procurement timed out: '+str(out/(phase+'.log')))
log=(out/(phase+'.log')).read_text();passed=proof/(phase+'-passed.txt');assert proc.returncode==0 and passed.is_file() and passed.stat().st_mtime>=started and not (proof/'failed.txt').exists(),str(out/(phase+'.log'))
assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','PROCUREMENT PROBE FAILED'])
assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[ProcurementRuntimeProbe]' in line) for line in log.splitlines()),'Unexpected addon error'
lifecycle=(proof/(phase+'-lifecycle-after.txt')).read_text();assert all(value in lifecycle for value in ['started=false','closing=true','Completed normally','terminated=true','dbClosed=true']),lifecycle
if phase!='broken':
 gate=(proof/(phase+'-shutdown-writer.txt')).read_text();assert all(value in gate for value in ['disabled=true','suiteFree=true','released=true']),gate
 assert not (proof/(phase+'-shutdown-writer-error.txt')).exists()
snapshot=physical(phase+'-after')
if phase!='broken':
 accounting=snapshot['NeverLandTownyContracts/procurement.sqlite'];assert accounting['audit_pending']==0,accounting
 states=sorted(r[1] for r in accounting['requests']);assert states==(['FUNDED','PUBLISHED','REFUNDED'] if phase=='first' else ['PUBLISHED','PUBLISHED','REFUNDED']),states
else:assert hashlib.sha256(preserved.read_bytes()).hexdigest()==results[0]['physical']['NeverLandTownyContracts/procurement.sqlite']['sha256']
results.append({'phase':phase,'result':passed.read_text(),'exit_code':proc.returncode,'jar_sha256':jars,'physical':snapshot});resultfile.write_text(json.dumps(results,indent=2)+'\n');print(phase+': '+passed.read_text().strip(),flush=True)
