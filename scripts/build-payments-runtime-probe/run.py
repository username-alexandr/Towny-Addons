#!/usr/bin/env python3
"""Two real Folia boots, exact artifacts, lifecycle errors and fresh assertion markers."""
from pathlib import Path
import argparse,subprocess,time,json,hashlib,yaml
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--phase',choices=['first','restart'])
a=p.parse_args();server=a.server.resolve();proof=server/'plugins/BuildPaymentsRuntimeProbe';out=server/'validation';out.mkdir(exist_ok=True)
assert (server/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert not (proof/'failed.txt').exists(), 'Use a fresh disposable fixture after a failure'
if a.phase=='restart':assert (proof/'first-passed.txt').exists() and not (proof/'restart-passed.txt').exists(), 'Need exactly one completed first phase'
else:assert not (proof/'first-passed.txt').exists(), 'Use a fresh disposable fixture'
assert (server/'plugins/BuildPaymentsRuntimeProbe.jar').is_file(), 'Compile the native probe before running'
assert yaml.safe_load((server/'plugins/bStats/config.yml').read_text())['enabled'] is False
assert 'enabled=false' in (server/'plugins/faststats/config.properties').read_text()
jars={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'plugins').glob('*.jar')};results=json.loads((out/'native-results.json').read_text()) if a.phase=='restart' else []
if results:assert results[0]['jar_sha256']==jars, 'JARs must stay unchanged between phases'
for phase in ([a.phase] if a.phase else ['first','restart']):
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
            raise SystemExit('Native Build Payments probe timed out')
    log=(out/f'{phase}.log').read_text();passed=proof/(phase+'-passed.txt')
    if proc.returncode or not passed.exists() or passed.stat().st_mtime<started or (proof/'failed.txt').exists():raise SystemExit('Native Build Payments probe failed: '+str(out/f'{phase}.log'))
    assert not any(s in log for s in ['FoliaWatchdogThread','Could not pass event','Error occurred while disabling','Error occurred while enabling','Could not load plugin','BUILD PAYMENTS PROBE FAILED'])
    assert not any(' ERROR]' in line and ('[NeverLandTowny' in line or '[BuildPaymentsRuntimeProbe]' in line) for line in log.splitlines()), 'Addon error in native log'
    # Inspect real append-only evidence after the JVM has drained writers. A bank receipt's
    # creation time is not sufficient to order repeated states in the administrator view.
    records={}
    for journal in (server/'plugins/NeverLandTownyBuilds/audit').glob('*.tsv'):
        for line in journal.read_text().splitlines():
            body,checksum=line.rsplit('\t',1)
            assert hashlib.sha256(body.encode()).hexdigest()==checksum, 'Corrupt audit evidence'
            cells=body.split('\t')
            if cells[3]=='NeverLandTownyBuilds' and cells[22].startswith('project=') and '; phase=' in cells[22]:
                records.setdefault(cells[6],[]).append(cells)
    audited=0
    for operation,raw in yaml.safe_load((server/'plugins/NeverLandTownyBuilds/town-data.yml').read_text())['build-payments'].items():
        row=json.loads(raw)
        if row['phase'] not in ['COMPLETE','REJECTED','REFUNDED']:continue
        evidence=sorted(records[operation],key=lambda x:int(x[2]))
        assert len({entry[2] for entry in evidence})==len(evidence), 'Ambiguous state chronology'
        expected={'COMPLETE':'COMPLETED','REJECTED':'REJECTED','REFUNDED':'REFUNDED'}[row['phase']]
        assert evidence[-1][5]==expected and int(evidence[-1][2])==row['phaseAt'], 'Admin audit latest state differs from persisted receipt'
        assert evidence[-1][22].endswith('; phase='+row['auditedPhase']), 'Audit ACK does not name the final persisted transition'
        audited+=1
    assert audited>=5, 'Missing final payment evidence'
    (out/(phase+'-audit-check.txt')).write_text('PASS chronological terminal evidence for '+str(audited)+' native payment receipts\n')
    results.append(dict(phase=phase,exit_code=proc.returncode,result=passed.read_text(),audited_terminal_receipts=audited,jar_sha256=jars));(out/'native-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(phase+': '+passed.read_text().strip(),flush=True)
