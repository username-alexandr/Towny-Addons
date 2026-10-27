#!/usr/bin/env python3
"""Two full launches of an already prepared disposable Folia fixture with both validation probes."""
from pathlib import Path
import argparse,subprocess,os,shutil,json,uuid,hashlib,threading
p=argparse.ArgumentParser();p.add_argument('server',type=Path);p.add_argument('--expect-shutdown-error',action='store_true');a=p.parse_args();server=a.server.resolve()
if not (server/'ALLOW_DISPOSABLE_LEDGER_PROBE').is_file() or 'server-ip=127.0.0.1' not in (server/'server.properties').read_text():
    raise SystemExit('Only marked loopback fixtures are allowed')
results=[]
artifacts={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((server/'plugins').glob('NeverLandTowny*.jar'))}
has_shutdown=(server/'plugins/ShutdownProbe.jar').exists()
has_resources=(server/'plugins/ServerResourceProbe.jar').exists()
has_buildmenu=(server/'plugins/BuildMenuProbe.jar').exists()
if a.expect_shutdown_error and not has_shutdown:raise SystemExit('Negative regression requires ShutdownProbe.jar')
for phase in (['baseline'] if a.expect_shutdown_error else ['initial','restart']):
    run_id=uuid.uuid4().hex
    proof=server/'validation'/phase;proof.mkdir(parents=True,exist_ok=True)
    names=['NeverLandValidationProbe','NeverLandLedgerProbe']+(['NeverLandShutdownProbe'] if has_shutdown else [])+(['NeverLandServerResourceProbe'] if has_resources else [])+(['NeverLandBuildMenuProbe'] if has_buildmenu else [])
    files=[server/'plugins'/name/'runs'/run_id/'result.txt' for name in names]
    # Unique paths and receipt keys ensure an older result cannot satisfy this launch.
    # Collect stdout in memory, then publish a complete log after exit. A synchronized
    # workspace must not replace a still-open output file with an earlier snapshot.
    process=subprocess.Popen([os.environ.get('JAVA','java'),'-Dneverland.probe.run='+run_id,'-Dneverland.probe.activeRaid='+str(has_shutdown).lower(),'-Dneverland.probe.shutdown=true','-Dterminal.jline=false','-Dterminal.ansi=false','-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1800M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    lines=[];reader=threading.Thread(target=lambda:lines.extend(iter(process.stdout.readline,'')),daemon=True);reader.start();timed_out=False
    try:code=process.wait(timeout=180)
    except subprocess.TimeoutExpired:
        timed_out=True;process.kill();code=process.wait()
    reader.join(timeout=10);(proof/'console.log').write_text(''.join(lines))
    if timed_out:raise SystemExit('Native probe timeout: '+phase)
    log=(proof/'console.log').read_text()
    if a.expect_shutdown_error:
        if code!=0 or 'Error occurred while disabling NeverLandTownyEvents v0.4.11' not in log or 'FoliaRegionScheduler.scheduleInternalOnRegion' not in log:
            raise SystemExit('Original shutdown error was not reproduced; exit='+str(code)+': '+str(proof/'console.log'))
        (proof/'expected-failure.json').write_text(json.dumps({'run_id':run_id,'expected_failure':True,'events_version':'0.4.11','exit_code':code,'jar_sha256':artifacts},indent=2)+'\n')
        print('PASS negative regression: original Events 0.4.11 shutdown error reproduced',flush=True)
        continue
    if code!=0 or not all(f.exists() and 'failed=0\n' in f.read_text() and 'run_id='+run_id+'\n' in f.read_text() for f in files):
        raise SystemExit('Native validation failed: '+str(proof/'console.log'))
    for path in files:shutil.copy2(path,proof/(path.parents[2].name+'-result.txt'))
    shutil.copy2(files[1].with_name('checks.txt'),proof/'ledger-checks.txt')
    if has_shutdown:shutil.copy2(files[2].with_name('checks.txt'),proof/'shutdown-checks.txt')
    if has_resources:shutil.copy2(files[names.index('NeverLandServerResourceProbe')].with_name('checks.txt'),proof/'resource-checks.txt')
    if has_buildmenu:shutil.copy2(files[names.index('NeverLandBuildMenuProbe')].with_name('checks.txt'),proof/'build-menu-checks.txt')
    if 'FoliaWatchdogThread' in log:raise SystemExit('Watchdog warning during native validation: '+phase)
    if any(token in log for token in ['Error occurred while disabling','Error occurred while enabling','Could not load plugin']):raise SystemExit('Plugin lifecycle error during native validation: '+phase)
    row={'phase':phase,'run_id':run_id,'exit_code':code,'suite':files[0].read_text(),'ledger':files[1].read_text(),'watchdog':False,'jar_sha256':artifacts}
    if has_shutdown:row['shutdown']=files[2].read_text()
    if has_resources:row['resources']=files[names.index('NeverLandServerResourceProbe')].read_text()
    if has_buildmenu:
        row['build_menu']=files[names.index('NeverLandBuildMenuProbe')].read_text()
        row['craftoui_sha256']={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((server/'plugins').glob('CraftoUI-*.jar'))}
    results.append(row)
    (server/'validation/results.json').write_text(json.dumps(results,indent=2)+'\n')
    print('PASS native '+phase,flush=True)
