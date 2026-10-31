"""Build only against a prepared disposable Folia test server. Never a release artifact."""
import argparse, os, pathlib, subprocess
p=argparse.ArgumentParser();p.add_argument('--server',type=pathlib.Path,required=True);p.add_argument('--java-home',type=pathlib.Path,required=True);a=p.parse_args()
root=a.server.resolve();source=pathlib.Path(__file__).resolve().parent
if not (root/'ALLOW_CRAFTO_UI_DISPOSABLE_TEST').is_file():raise SystemExit('Missing disposable test marker')
out=root/'civic-probe-classes';out.mkdir(exist_ok=True)
jars=[*root.glob('versions/**/*.jar'),*root.glob('libraries/**/*.jar'),*root.glob('plugins/*.jar')]
result=subprocess.run([str(a.java_home/'bin/javac'),'-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(out),str(source/'CivicProbe.java')])
if result.returncode:raise SystemExit(result.returncode)
subprocess.run([str(a.java_home/'bin/jar'),'--create','--file',str(root/'plugins/CivicProbe-TEST-ONLY.jar'),'-C',str(out),'.','-C',str(source),'plugin.yml'],check=True)
print('Disposable runtime probe built')
