#!/usr/bin/env python3
"""Three real Folia boots, exact artifacts, lifecycle errors and fresh assertion markers."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,yaml,socket,re
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--phase',choices=['first','restart','corrupt'])
a=p.parse_args();server=a.server.resolve();proof=server/'plugins/CrimeJusticeRuntimeProbe';out=server/'validation';out.mkdir(exist_ok=True)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists(), 'Use a fresh disposable fixture after failure'
if a.phase in (None,'first'):assert not (proof/'first-passed.txt').exists(), 'First phase requires a fresh disposable fixture'
if a.phase=='restart':assert (proof/'first-passed.txt').exists() and not (proof/'restart-passed.txt').exists()
if a.phase=='corrupt':assert (proof/'restart-passed.txt').exists() and not (proof/'corrupt-passed.txt').exists()
assert (server/'plugins/CrimeJusticeRuntimeProbe.jar').is_file(), 'Compile the native probe before running'
assert yaml.safe_load((server/'plugins/bStats/config.yml').read_text())['enabled'] is False
assert 'enabled=false' in (server/'plugins/faststats/config.properties').read_text()
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};results=json.loads((out/'native-results.json').read_text()) if (out/'native-results.json').exists() else []
for phase in ([a.phase] if a.phase else ['first','restart','corrupt']):
    if phase=='corrupt':
        (proof/'corrupt-mode').write_text('isolated fixture corruption only\n')
        (server/'plugins/NeverLandTownyJustice/dossiers.yml').write_text('schema: 99\ndossiers: damaged fixture\n')
        (server/'plugins/NeverLandTownyCrime/patrol-data.yml').write_text('schema: 99\npatrols: damaged fixture\n')
    port=int(re.search(r'^server-port=(\d+)$',(server/'server.properties').read_text(),re.M).group(1))
    with socket.socket() as probe:probe.bind(('127.0.0.1',port))
    started=time.time();command=[str(a.java_home.resolve()/'bin/java'),'-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1700M','-Dterminal.jline=false','-Dterminal.ansi=false']
    if (server/'cacerts').exists():command+=['-Djavax.net.ssl.trustStore='+str(server/'cacerts')]
    command+=['-jar','server.jar','--nogui']
    with (out/f'{phase}.log').open('w') as stream:
        proc=subprocess.Popen(command,cwd=server,stdout=stream,stderr=subprocess.STDOUT,text=True,stdin=subprocess.PIPE)
        try:proc.wait(timeout=240)
        except subprocess.TimeoutExpired:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:proc.wait(timeout=45)
            except subprocess.TimeoutExpired:proc.kill();proc.wait()
            raise SystemExit('Native crime justice probe timed out')
    log=(out/f'{phase}.log').read_text();passed=proof/(phase+'-passed.txt')
    if proc.returncode or not passed.exists() or passed.stat().st_mtime<started or (proof/'failed.txt').exists():raise SystemExit('Native crime justice probe failed: '+str(out/f'{phase}.log'))
    assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','Could not load plugin','ADMISSIONS PROBE FAILED'])
    assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[CrimeJusticeRuntimeProbe]' in line) for line in log.splitlines()), 'Addon error in native log'
    results.append(dict(phase=phase,exit_code=proc.returncode,result=passed.read_text(),jar_sha256=jars));(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(phase+': '+passed.read_text().strip(),flush=True)
