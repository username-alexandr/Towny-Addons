#!/usr/bin/env python3
"""Build/test the server, common and API against supplied real runtime dependencies; client unchanged."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, zipfile

p=argparse.ArgumentParser(description=__doc__)
for key in ['java-home','libraries','luckperms','junit']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();root=Path(__file__).resolve().parents[1]
java=a.java_home.resolve()/'bin';jars=sorted(a.libraries.resolve().rglob('*.jar'))+[a.luckperms.resolve(),a.junit.resolve()]
os.chdir(root)
assert jars and all(path.is_file() for path in jars),'Missing runtime dependency'
base=root/'build/direct';main=base/'classes';tests=base/'tests'
for folder in [main,tests]:
    if folder.exists():shutil.rmtree(folder)
    folder.mkdir(parents=True)
cp=os.pathsep.join(map(str,jars))
def inputs():
    files={path for part in ['common','api','server'] for path in (root/part/'src').rglob('*') if path.is_file()}
    files.update([Path(__file__).resolve(),root/'gradle.properties'])
    return {str(path.relative_to(root)):hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(files)}
initial=inputs()
def compile(source_set,destination,classpath):
    sources=sorted(str(path) for part in ['common','api','server'] for path in (root/part/'src'/source_set/'java').rglob('*.java'))
    args=['-encoding','UTF-8','--release','25','-cp',classpath,'-d',str(destination),*sources]
    file=base/(source_set+'.args');file.write_text('\n'.join('"'+arg+'"' for arg in args))
    subprocess.run([str(java/'javac'),'@'+str(file)],check=True)
compile('main',main,cp);compile('test',tests,os.pathsep.join([str(main),cp]))
classpath=os.pathsep.join([str(tests),str(main),cp]+[str(root/part/'src'/kind/'resources') for part in ['common','api','server'] for kind in ['main','test']])
result=subprocess.run([str(java/'java'),'-cp',classpath,'org.junit.platform.console.ConsoleLauncher','execute','--scan-class-path','--disable-banner','--details=summary'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
(base/'tests.txt').write_text(result.stdout);print(result.stdout);result.check_returncode()
version=re.search(r'^version=(.+)$',(root/'gradle.properties').read_text(),re.M).group(1)
target=base/f'CraftoUI-Folia-26.2-{version}.jar'
if inputs()!=initial:raise SystemExit('Source changed during validation; JAR not published')
candidate=target.with_suffix('.jar.tmp')
with zipfile.ZipFile(candidate,'w',zipfile.ZIP_DEFLATED) as archive:
    entries={str(path.relative_to(main)):path.read_bytes() for path in main.rglob('*.class')}
    for part in ['common','api','server']:
        resources=root/part/'src/main/resources'
        for path in resources.rglob('*'):
            if path.is_file():
                name=str(path.relative_to(resources));data=path.read_bytes()
                if name=='plugin.yml':data=data.decode().replace('${version}',version).encode()
                entries[name]=data
    entries['META-INF/craftoui-build.json']=json.dumps({'version':version,'inputs':initial,'java_target':25,'validation':'server/common/API JUnit suite passed; client unchanged'},sort_keys=True).encode()
    for name,data in sorted(entries.items()):archive.writestr(zipfile.ZipInfo(name,(1980,1,1,0,0,0)),data,compress_type=zipfile.ZIP_DEFLATED)
os.replace(candidate,target)
manifest={'version':version,'target':'server/common/api only; existing client unchanged','jar':target.name,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'dependencies':{path.name:hashlib.sha256(path.read_bytes()).hexdigest() for path in jars}}
(base/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(target)
