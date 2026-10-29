#!/usr/bin/env python3
"""Four real Folia boots, exact artifacts, lifecycle errors and fresh assertion markers."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,yaml,shutil
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True)
a=p.parse_args();server=a.server.resolve();proof=server/'plugins/ResearchLicenseRuntimeProbe';out=server/'validation';out.mkdir(exist_ok=True)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists() and not (proof/'first-passed.txt').exists(), 'Use a fresh disposable fixture'
assert (server/'plugins/ResearchLicenseRuntimeProbe.jar').is_file(), 'Compile the native probe before running'
assert yaml.safe_load((server/'plugins/bStats/config.yml').read_text())['enabled'] is False
assert 'enabled=false' in (server/'plugins/faststats/config.properties').read_text()
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};results=[]
for phase in ['first','restart','clock','corrupt']:
    if phase=='corrupt':
        (proof/'corrupt-input.txt').write_text('Deliberate optional journal corruption, original learned data unchanged\n')
        licenses=server/'plugins/NeverLandTownyResearch/licenses.yml';shutil.copy2(licenses,proof/'licenses-before-corruption.yml');licenses.write_text('schema: 999\nlicenses: {}\n')
        config=server/'plugins/NeverLandTownyResearch/config.yml';data=yaml.safe_load(config.read_text());data['licenses']['offer-seconds']='invalid';config.write_text(yaml.safe_dump(data,allow_unicode=True))
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
            raise SystemExit('Native research license probe timed out')
    log=(out/f'{phase}.log').read_text();passed=proof/(phase+'-passed.txt')
    if proc.returncode or not passed.exists() or passed.stat().st_mtime<started or (proof/'failed.txt').exists():raise SystemExit('Native research license probe failed: '+str(out/f'{phase}.log'))
    assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','Could not load plugin','RESEARCH LICENSE PROBE FAILED'])
    assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[ResearchLicenseRuntimeProbe]' in line) and 'AUDIT GAP:' not in line and not(phase=='corrupt' and '[NeverLandTownyResearch]' in line and ('лиценз' in line.lower())) for line in log.splitlines()), 'Unexpected addon error in native log'
    results.append(dict(phase=phase,exit_code=proc.returncode,result=passed.read_text(),jar_sha256=jars));(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(phase+': '+passed.read_text().strip(),flush=True)
