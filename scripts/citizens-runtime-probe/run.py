"""Run crash/restart assertions only on an explicitly marked disposable server."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--server-dir', type=Path, required=True)
parser.add_argument('--java-home', type=Path, required=True)
args = parser.parse_args()
server = args.server_dir.resolve()
if not (server/'ALLOW_DISPOSABLE_RELIABILITY_PROBE').is_file():
    raise SystemExit('Disposable server marker required')
jar = next(server.glob('purpur-*.jar'))
command = [str(args.java_home/'bin/java'), '-XX:ActiveProcessorCount=2', '-Xms512M', '-Xmx2G',
           '-Dneverland.runtimeProbe=true', '-jar', jar.name, '--nogui']
proof = server/'plugins/NeverLandCitizensRuntimeProbe'
if any((proof/name).exists() for name in ('first-passed.txt','restart-passed.txt','failed.txt')):
    raise SystemExit('Existing probe evidence found: archive it and explicitly reset this disposable fixture first')
for phase, expected, marker in [('first', 0, 'first-passed.txt'), ('restart', 0, 'restart-passed.txt')]:
    with (server/('citizens-'+phase+'.log')).open('w') as log:
        process = subprocess.Popen(command, cwd=server, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.PIPE)
        try:
            code = process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.communicate(b'stop\n', timeout=45)
            raise SystemExit(phase+' timed out')
    if code != expected or not (proof/marker).is_file():
        failure = proof/'failed.txt'
        raise SystemExit(f'{phase} failed: exit {code}, expected {expected}\n'+(failure.read_text() if failure.exists() else 'Inspect '+phase+'.log'))
    print(phase+': '+(proof/marker).read_text().strip(), flush=True)
