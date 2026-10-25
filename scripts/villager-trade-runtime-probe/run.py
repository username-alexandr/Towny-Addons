"""Run a fresh disposable Folia fixture twice; retain successful checks and restart evidence."""
import argparse
from pathlib import Path
import subprocess

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-dir', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
a = p.parse_args()
root = a.server_dir.resolve()
if not (root / 'ALLOW_DISPOSABLE_VILLAGER_TRADE_PROBE').is_file():
    raise SystemExit('Disposable marker required')
props = dict(line.split('=', 1) for line in (root / 'server.properties').read_text().splitlines()
             if '=' in line and not line.startswith('#'))
if props.get('server-ip') != '127.0.0.1':
    raise SystemExit('Loopback-only test server required')
proof = root / 'plugins/VillagerTradeProbe'
if proof.exists():
    raise SystemExit('Use a fresh fixture; preserve previous evidence before resetting test data')
for run in (1, 2):
    passed = proof / 'passed.txt'
    if passed.exists(): passed.unlink()
    with (root / f'probe-{run}.log').open('w') as log:
        process = subprocess.Popen([str(a.java_home / 'bin/java'), '-Xms512M', '-Xmx1300M',
                                    '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'server.jar', '--nogui'],
                                   cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        try:
            process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.stdin.write('stop\n'); process.stdin.flush()
            try: process.wait(timeout=45)
            except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=15)
            raise
    if process.returncode or not passed.exists() or (proof / 'failed.txt').exists():
        raise SystemExit(f'Probe {run} failed; inspect probe-{run}.log')
    (root / f'checks-{run}.txt').write_text((proof / 'checks.txt').read_text())
    (root / f'passed-{run}.txt').write_text(passed.read_text())
    print(passed.read_text(), end='', flush=True)
