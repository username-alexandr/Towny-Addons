"""Two sequential Folia JVMs with exact JAR identity and native UI assertion markers."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True)
a=p.parse_args();server=a.server.resolve();proof=server/'plugins/CraftoUIProcurementProbe';out=server/'validation';out.mkdir(exist_ok=True)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'first-passed.txt').exists() and not (proof/'failed.txt').exists()
def hashes():return {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((server/'plugins').glob('*.jar'))}
jars=hashes();results=[]
for phase in ['first','restart']:
    assert hashes()==jars,'Installed JARs changed'
    started=time.time();command=[str(a.java_home.resolve()/'bin/java'),'-XX:ActiveProcessorCount=4','-Xms512M','-Xmx1700M','-Dterminal.jline=false','-Dterminal.ansi=false']
    if (server/'cacerts').exists():command+=['-Djavax.net.ssl.trustStore='+str(server/'cacerts')]
    command+=['-jar','server.jar','--nogui']
    with (out/f'{phase}.log').open('w') as stream:
        proc=subprocess.Popen(command,cwd=server,stdout=stream,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True)
        try:proc.wait(timeout=240)
        except subprocess.TimeoutExpired:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:proc.wait(timeout=40)
            except subprocess.TimeoutExpired:proc.kill();proc.wait()
            raise SystemExit('Native UI procurement timed out')
    log=(out/f'{phase}.log').read_text();passed=proof/f'{phase}-passed.txt'
    assert proc.returncode==0 and passed.is_file() and passed.stat().st_mtime>=started and not (proof/'failed.txt').exists(),str(out/f'{phase}.log')
    assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','Could not load plugin','PROCUREMENT PROBE FAILED'])
    assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[CraftoUI' in line) for line in log.splitlines()),'Unexpected addon error'
    results.append({'phase':phase,'exit_code':proc.returncode,'result':passed.read_text(),'jar_sha256':jars});(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(phase+': '+passed.read_text().strip(),flush=True)
