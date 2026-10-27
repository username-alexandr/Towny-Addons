#!/usr/bin/env python3
"""Two boots of an isolated loopback fixture. Does not install or alter production plugins."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('server', type=Path)
p.add_argument('--java', default='java')
p.add_argument('--report', type=Path)
a = p.parse_args()
root = a.server.resolve()
assert (root/'ALLOW_DISPOSABLE_PAYOUT_LIMITS_PROBE').is_file()
assert 'server-ip=127.0.0.1' in (root/'server.properties').read_text()
out = (a.report or root/'validation').resolve()
out.mkdir(parents=True, exist_ok=True)
proof = root/'plugins/PayoutLimitsRuntimeProbe'
hashes = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in (root/'plugins').glob('*.jar')}
results = []
for phase in ['initial', 'restart']:
    started = time.time()
    proc = subprocess.Popen([a.java, '-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx1800M',
                             '-Dterminal.jline=false', '-Dterminal.ansi=false',
                             '-Dneverland.probe.phase='+phase, '-jar', 'server.jar', '--nogui'],
                            cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True)
    try:
        log, _ = proc.communicate(timeout=180)
    except subprocess.TimeoutExpired:
        proc.kill(); log, _ = proc.communicate()
        (out/(phase+'-console.log')).write_text(log)
        raise SystemExit('Native probe timed out')
    (out/(phase+'-console.log')).write_text(log)
    result = proof/(phase+'-result.txt')
    if proc.returncode or not result.exists() or result.stat().st_mtime < started:
        raise SystemExit('Missing or failed native proof: '+str(out/(phase+'-console.log')))
    report = result.read_text()
    assert 'phase='+phase in report and 'failed=0' in report, report
    assert not any(s in log for s in ['FoliaWatchdogThread', 'Error occurred while disabling',
                                     'Error occurred while enabling', 'Could not load plugin',
                                     'Публичные заказы приостановлены'])
    (out/(phase+'-checks.txt')).write_bytes(result.with_name(phase+'-checks.txt').read_bytes())
    results.append({'phase': phase, 'exit_code': proc.returncode, 'result': report,
                    'watchdog': False, 'jars': hashes})
    (out/'native-results.json').write_text(json.dumps(results, indent=2)+'\n')
    print('PASS native payout limits '+phase+': '+report.replace('\n', '; '), flush=True)
