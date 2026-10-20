"""Run an isolated loopback server, read the native probe, then stop cleanly."""
import os,sys,subprocess,time
from pathlib import Path
server=Path(sys.argv[1]).resolve();result=server/'plugins/EconomyProbe/result.txt';result.unlink(missing_ok=True)
(server/'plugins/EconomyProbe/native-blocks.log').unlink(missing_ok=True)
with (server/'economy-console.log').open('w') as log:
    p=subprocess.Popen([os.environ['JAVA'],'-Dterminal.jline=false','-Dterminal.ansi=false','-Xms256M','-Xmx1500M','-jar','server.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
    try:
        deadline=time.monotonic()+140
        dumped=False
        while p.poll() is None and time.monotonic()<deadline and not result.exists():
            if not dumped and time.monotonic()>deadline-100:
                with (server/'economy-threads.txt').open('w') as out:
                    subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/jcmd'),str(p.pid),'Thread.print'],stdout=out,stderr=subprocess.STDOUT,timeout=10)
                dumped=True
            time.sleep(1)
        if p.poll() is None:
            p.stdin.write('stop\n');p.stdin.flush()
            try:p.wait(timeout=40)
            except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
    finally:
        if p.poll() is None:p.kill();p.wait()
if not result.exists():raise SystemExit('No native result; inspect logs/latest.log')
print(result.read_text());sys.exit(0 if 'failed=0\n' in result.read_text() else 1)
