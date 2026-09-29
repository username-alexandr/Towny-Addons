#!/usr/bin/env python3
"""Run a prepared disposable Folia directory; keep stdin open and stop cleanly on completion."""
import os,subprocess,time,sys,yaml
from pathlib import Path
server=Path(sys.argv[1]).resolve();java=os.environ.get('JAVA','java')
# Explicit opt-out is required before any native server process is started.
for relative,fields in [('plugins/bStats/config.yml',['enabled']),('plugins/PlaceholderAPI/config.yml',['check_updates','cloud_enabled']),('plugins/Vault/config.yml',['update-check'])]:
 data=yaml.safe_load((server/relative).read_text())
 if any(data.get(key) is not False for key in fields):raise SystemExit('Disable telemetry/update checks: '+relative)
properties=(server/'plugins/faststats/config.properties').read_text().splitlines()
if 'enabled=false' not in properties:raise SystemExit('Disable FastStats before testing')
print('Telemetry preflight: bStats and FastStats disabled; PlaceholderAPI cloud and updates disabled',flush=True)
log=server/'native-console.log';result=server/'plugins/NeverLandValidationProbe/result.txt'
result.unlink(missing_ok=True)
(result.parent/'command-outbox.yml').unlink(missing_ok=True)
with log.open('w') as output:
 p=subprocess.Popen([java,'-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx1500M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
 try:
  deadline=time.monotonic()+240
  while p.poll() is None and time.monotonic()<deadline:
   if result.exists():break
   time.sleep(1)
  if p.poll() is None:
   p.stdin.write('stop\n');p.stdin.flush()
   try:p.wait(timeout=50)
   except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=20)
 finally:
  if p.poll() is None:p.kill();p.wait()
if not result.exists():raise SystemExit('Native probe did not finish: '+str(log))
print(result.read_text())
sys.exit(0 if 'failed=0\n' in result.read_text() else 1)
