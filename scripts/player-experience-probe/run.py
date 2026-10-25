"""Run only in a prepared disposable Folia fixture; never install this probe on a live server."""
import argparse, subprocess, time
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--server-dir',type=Path,required=True)
a=p.parse_args()
root=a.server_dir.resolve()
if not (root/'ALLOW_DISPOSABLE_PLAYER_EXPERIENCE_PROBE').is_file():
    raise SystemExit('Disposable fixture marker required')
proof=root/'plugins/PlayerExperienceProbe/passed.txt'
started=time.time()
log=root/'probe-console.log'
with log.open('w') as output:
    process=subprocess.Popen(['java','-Xms256M','-Xmx850M','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','server.jar','--nogui'],cwd=root,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
    try:
        process.wait(timeout=180)
    except subprocess.TimeoutExpired:
        process.stdin.write('stop\n')
        process.stdin.flush()
        try:
            process.wait(timeout=45)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        raise
if process.returncode!=0 or not proof.exists() or proof.stat().st_mtime<started:
    raise SystemExit(f'Probe failed, process exit {process.returncode}: see {root / "logs/latest.log"}')
print(proof.read_text())
