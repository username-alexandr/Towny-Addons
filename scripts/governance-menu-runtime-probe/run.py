"""Run only an explicitly disposable, loopback-bound Folia fixture; stop on timeout."""
import argparse, hashlib, json, subprocess, time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--server-dir',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--phase',choices=['1','2'],required=True);a=p.parse_args()
s=a.server_dir.resolve();proof=s/'plugins/GovernanceMenuRuntimeProbe'
if not (s/'ALLOW_DISPOSABLE_GOVERNANCE_PROBE').is_file():raise SystemExit('Disposable marker required')
props=dict(line.split('=',1) for line in (s/'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
if props.get('server-ip')!='127.0.0.1':raise SystemExit('Loopback bind required')
if (proof/'first-passed.txt').exists()!=(a.phase=='2') or (proof/'failed.txt').exists():raise SystemExit('Fresh first phase or successful previous phase required')
result=proof/('first-passed.txt' if a.phase=='1' else 'restart-passed.txt')
if result.exists():raise SystemExit('Evidence exists; archive explicitly before rerun')
(s/('governance-jars-'+a.phase+'.json')).write_text(json.dumps({j.name:hashlib.sha256(j.read_bytes()).hexdigest() for j in sorted((s/'plugins').glob('*.jar'))},indent=2)+'\n')
with (s/('governance-console-'+a.phase+'.log')).open('w') as log:
 process=subprocess.Popen([str(a.java_home/'bin/java'),'-XX:ActiveProcessorCount=2','-Xms256M','-Xmx1500M','-Dneverland.governanceProbe=true','-Dterminal.jline=false','-jar','server.jar','--nogui'],cwd=s,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
 try:
  deadline=time.monotonic()+150
  while process.poll() is None and time.monotonic()<deadline:time.sleep(.25)
 finally:
  if process.poll() is None:
   process.stdin.write('stop\n');process.stdin.flush()
   try:process.wait(timeout=40)
   except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=15)
if process.returncode!=0 or (proof/'failed.txt').exists() or not result.exists():raise SystemExit('Probe failed: inspect console and failed.txt')
print(result.read_text(),end='')
