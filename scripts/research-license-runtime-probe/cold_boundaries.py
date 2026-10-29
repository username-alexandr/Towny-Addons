#!/usr/bin/env python3
"""Check isolated journal boundaries against the exact already-built Research artifact."""
from pathlib import Path
import argparse,os,subprocess,tempfile
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--libraries',type=Path,required=True);p.add_argument('--research-jar',type=Path,required=True);a=p.parse_args()
cp=os.pathsep.join([str(a.research_jar.resolve()),*map(str,a.libraries.resolve().rglob('*.jar'))]);source=Path(__file__).with_name('LicenseJournalBoundaries.java')
with tempfile.TemporaryDirectory(prefix='research-license-boundaries-') as output:
    subprocess.run([str(a.java_home.resolve()/'bin/javac'),'--release','17','-encoding','UTF-8','-cp',cp,'-d',output,str(source)],check=True)
    subprocess.run([str(a.java_home.resolve()/'bin/java'),'-cp',output+os.pathsep+cp,'ru.neverland.runtime.LicenseJournalBoundaries'],check=True)
