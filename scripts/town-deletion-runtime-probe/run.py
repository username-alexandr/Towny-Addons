"""Run two isolated Folia lifecycles and cleanly stop each child; no production directory."""
import os,sys,subprocess,time
from pathlib import Path
server=Path(sys.argv[1]).resolve();result=server/'plugins/DeletionProbe/result.txt'
for phase in (1,2):
    result.unlink(missing_ok=True)
    with (server/f'deletion-console-{phase}.log').open('w') as log:
        p=subprocess.Popen([os.environ['JAVA'],'-Dterminal.jline=false','-Dterminal.ansi=false','-Xms256M','-Xmx1500M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
        try:
            deadline=time.monotonic()+240
            while p.poll() is None and time.monotonic()<deadline and not result.exists():time.sleep(1)
            if p.poll() is None:
                p.stdin.write('stop\n');p.stdin.flush()
                try:p.wait(timeout=40)
                except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
        finally:
            if p.poll() is None:p.kill();p.wait()
    if not result.exists():raise SystemExit(f'No native result; inspect deletion-console-{phase}.log')
    text=result.read_text();(server/f'deletion-result-{phase}.txt').write_text(text);print(text,flush=True)
    if 'failed=0\n' not in text:raise SystemExit(1)
