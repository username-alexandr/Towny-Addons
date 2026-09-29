#!/usr/bin/env python3
"""Compile the disposable native probe against a bootstrapped Folia server."""
from pathlib import Path
import argparse,os,subprocess
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);args=p.parse_args()
root=Path(__file__).resolve().parents[2];server=args.server.resolve();classes=root/'build/folia-probe';classes.mkdir(parents=True,exist_ok=True)
java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
cp=os.pathsep.join(str(f.resolve()) for f in (server/'libraries').rglob('*.jar'))
if not cp:raise SystemExit('Bootstrap Folia first so server/libraries exists')
subprocess.run([str(java/'javac') if java else 'javac','--release','17','-cp',cp,'-d',str(classes),str(Path(__file__).with_name('FoliaProbe.java'))],check=True)
(server/'plugins').mkdir(exist_ok=True)
subprocess.run([str(java/'jar') if java else 'jar','cf',str(server/'plugins/ValidationProbe.jar'),'-C',str(classes),'.','-C',str(Path(__file__).parent),'plugin.yml'],check=True)
