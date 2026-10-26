#!/usr/bin/env python3
"""Two full launches of an already prepared disposable Folia fixture with both validation probes."""
from pathlib import Path
import argparse,subprocess,os,shutil,json,uuid,hashlib
p=argparse.ArgumentParser();p.add_argument('server',type=Path);a=p.parse_args();server=a.server.resolve()
if not (server/'ALLOW_DISPOSABLE_LEDGER_PROBE').is_file() or 'server-ip=127.0.0.1' not in (server/'server.properties').read_text():
    raise SystemExit('Only marked loopback fixtures are allowed')
results=[]
artifacts={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((server/'plugins').glob('NeverLandTowny*.jar'))}
for phase in ['initial','restart']:
    run_id=uuid.uuid4().hex
    proof=server/'validation'/phase;proof.mkdir(parents=True,exist_ok=True)
    files=[server/'plugins'/name/'runs'/run_id/'result.txt' for name in ['NeverLandValidationProbe','NeverLandLedgerProbe']]
    # Unique paths and receipt keys ensure an older result cannot satisfy this launch.
    with (proof/'console.log').open('w') as output:
        process=subprocess.Popen([os.environ.get('JAVA','java'),'-Dneverland.probe.run='+run_id,'-Dneverland.probe.shutdown=true','-Dterminal.jline=false','-Dterminal.ansi=false','-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1800M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
        try:code=process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.kill();process.wait();raise SystemExit('Native probe timeout: '+phase)
    if code!=0 or not all(f.exists() and 'failed=0\n' in f.read_text() and 'run_id='+run_id+'\n' in f.read_text() for f in files):
        raise SystemExit('Native validation failed: '+str(proof/'console.log'))
    for path in files:shutil.copy2(path,proof/(path.parents[2].name+'-result.txt'))
    shutil.copy2(files[1].with_name('checks.txt'),proof/'ledger-checks.txt')
    log=(proof/'console.log').read_text()
    if 'FoliaWatchdogThread' in log:raise SystemExit('Watchdog warning during native validation: '+phase)
    results.append({'phase':phase,'run_id':run_id,'exit_code':code,'suite':files[0].read_text(),'ledger':files[1].read_text(),'watchdog':False,'jar_sha256':artifacts})
    (server/'validation/results.json').write_text(json.dumps(results,indent=2)+'\n')
    print('PASS native '+phase,flush=True)
