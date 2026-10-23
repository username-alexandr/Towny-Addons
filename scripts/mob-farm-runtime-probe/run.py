"""Run one test phase on an explicitly disposable server, stop it, retain evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-dir', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--phase', choices=['1', '2'], required=True)
a = p.parse_args()
s = a.server_dir.resolve()
if not (s / 'ALLOW_DISPOSABLE_MOB_FARM_PROBE').is_file():
    raise SystemExit('Disposable server marker required')
props = dict(line.split('=', 1) for line in (s / 'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
if props.get('server-ip') != '127.0.0.1':
    raise SystemExit('Test server must bind to 127.0.0.1')
proof = s / 'plugins/MobFarmProbe'
if (proof / 'phase1').exists() != (a.phase == '2'):
    raise SystemExit('First phase requires a fresh probe data directory; second requires phase1')
result = proof / 'result.txt'
if result.exists():
    if a.phase != '2' or 'failed=0' not in result.read_text():
        raise SystemExit('Previous proof exists or failed; archive it explicitly before retrying')
    result.unlink()
hashes = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((s / 'plugins').glob('*.jar'))}
(s / ('mob-farm-jars-' + a.phase + '.json')).write_text(json.dumps(hashes, indent=2) + '\n')
with (s / ('mob-farm-console-' + a.phase + '.log')).open('w') as log:
    process = subprocess.Popen([str(a.java_home / 'bin/java'), '-XX:ActiveProcessorCount=2', '-Xms512M', '-Xmx2G',
                                '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'server.jar', '--nogui'],
                               cwd=s, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.PIPE, text=True)
    try:
        deadline = time.monotonic() + 150
        while process.poll() is None and not result.exists() and time.monotonic() < deadline:
            time.sleep(0.25)
    finally:
        if process.poll() is None:
            process.stdin.write('stop\n')
            process.stdin.flush()
            try:
                process.wait(timeout=45)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=15)
if process.returncode != 0 or not result.exists() or 'failed=0' not in result.read_text():
    raise SystemExit('Probe failed; inspect the console log')
content = result.read_text()
(s / ('mob-farm-result-' + a.phase + '.txt')).write_text(content)
print(content, end='')
