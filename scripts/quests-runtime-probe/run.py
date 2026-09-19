"""Run the opt-in city project fixture in two fresh JVMs."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--server-dir', type=Path, required=True)
parser.add_argument('--java-home', type=Path, required=True)
args = parser.parse_args()
server = args.server_dir.resolve()
if not (server/'ALLOW_DISPOSABLE_RELIABILITY_PROBE').is_file():
    raise SystemExit('Disposable server marker required')
if 'server-ip=127.0.0.1' not in (server/'server.properties').read_text():
    raise SystemExit('Only a loopback test server is permitted')
proof = server/'plugins/NeverLandQuestsRuntimeProbe'
if proof.exists() and any(proof.glob('*-passed.txt')):
    raise SystemExit('Use a fresh disposable fixture; previous proof markers are preserved')
proof.mkdir(parents=True, exist_ok=True)
(proof/'plugins.json').write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest()
    for p in sorted((server/'plugins').glob('*.jar'))}, indent=2)+'\n')
command = [str(args.java_home/'bin/java'), '-XX:ActiveProcessorCount=2', '-Xms512M', '-Xmx2G',
           '-Dneverland.runtimeProbe=true', '-jar', 'purpur.jar', '--nogui']
for phase in ['first', 'restart']:
    with (server/('quests-'+phase+'.log')).open('w') as log:
        process = subprocess.Popen(command, cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            process.communicate(b'stop\n', timeout=45)
            raise SystemExit(phase+' timed out; inspect the native server log')
    if code != 0 or (proof/'failed.txt').exists() or not (proof/(phase+'-passed.txt')).is_file():
        raise SystemExit(phase+' failed; inspect '+str(server/('quests-'+phase+'.log')))
    print(phase+': '+(proof/(phase+'-passed.txt')).read_text().strip(), flush=True)
