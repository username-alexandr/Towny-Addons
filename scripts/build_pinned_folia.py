#!/usr/bin/env python3
"""Offline javac build against an already bootstrapped real Folia runtime and official plugin JARs."""
import argparse, json, os, re, shutil, subprocess, zipfile, xml.etree.ElementTree as ET
from pathlib import Path
from api_audit import ROOT, inventory
from run_smoke import run_tests

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--runtime-libraries',type=Path,required=True)
p.add_argument('--towny',type=Path,required=True)
p.add_argument('--placeholder',type=Path,required=True)
p.add_argument('--extra',type=Path,action='append',default=[])
p.add_argument('modules',nargs='+')
a=p.parse_args();java=a.java_home.resolve()/'bin';os.environ['JAVA']=str(java/'java')
dependencies=sorted(a.runtime_libraries.resolve().rglob('*.jar'))+[a.towny.resolve(),a.placeholder.resolve()]+[f.resolve() for f in a.extra]
assert all(f.is_file() for f in dependencies)
assert any('folia-api' in f.name for f in dependencies),'Use original Folia libraries, never API stubs'
cp=os.pathsep.join(map(str,dependencies));report=[];data=inventory()
(ROOT/'build').mkdir(exist_ok=True);(ROOT/'build/pinned-classpath.txt').write_text(cp)
for short in a.modules:
    module=ROOT/'modules'/('NeverLandTowny'+short if not short.startswith('NeverLandTowny') else short)
    if (module/'build.gradle.kts').exists():
        gradle=(module/'build.gradle.kts').read_text();version=re.search(r'^version = "([^"]+)"',gradle,re.M).group(1)
        sources=[module/'src/main/java']+[(module/s).resolve() for s in re.findall(r'java\.srcDir\("([^"]+)"\)',gradle)]
        resource_roots=[module/'src/main/resources']+[(module/s).resolve() for s in re.findall(r'resources\.srcDir\("([^"]+)"\)',gradle)]
    else:
        ns={'m':'http://maven.apache.org/POM/4.0.0'};pom=ET.parse(module/'pom.xml').getroot();version=pom.findtext('m:version',namespaces=ns)
        def expand(text):return Path(text.replace('${project.basedir}',str(module))).resolve()
        sources=[module/'src/main/java']+[expand(s.text) for s in pom.findall('.//m:configuration/m:sources/m:source',ns)]
        resource_roots=[module/'src/main/resources']+[expand(s.text) for s in pom.findall('.//m:configuration/m:resources/m:resource/m:directory',ns)]
    build=module/'build';classes=build/'classes/java/main';tests=build/'classes/java/test';libs=build/'libs'
    for path in [classes,tests,libs]:shutil.rmtree(path,ignore_errors=True);path.mkdir(parents=True)
    files=sorted({f for root in sources for f in root.rglob('*.java')})
    subprocess.run([str(java/'javac'),'--release','17','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,files)],check=True)
    for root in resource_roots:
        if root.exists():shutil.copytree(root,classes,dirs_exist_ok=True)
    descriptor=classes/'plugin.yml';descriptor.write_text(descriptor.read_text().replace('${version}',version))
    jar=libs/f'{module.name}-{version}.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        for file in sorted(classes.rglob('*')):
            if file.is_file():z.write(file,file.relative_to(classes).as_posix())
    test_sources=sorted((module/'src/test/java').rglob('*.java'))
    if test_sources:subprocess.run([str(java/'javac'),'--release','17','-encoding','UTF-8','-cp',str(classes)+os.pathsep+cp,'-d',str(tests),*map(str,test_sources)],check=True)
    test_cp=os.pathsep.join([str(tests),str(classes),cp]);names=run_tests(module,test_cp,data)
    (build/'smoke-classpath.txt').write_text(test_cp)
    report.append(dict(module=module.name,version=version,jar=str(jar.relative_to(ROOT)),tests=names))
    (ROOT/'build/pinned-tests.json').write_text(json.dumps(report,indent=2)+'\n')
    print('BUILD AND SMOKE PASS',jar.name,len(names),'programs',flush=True)
