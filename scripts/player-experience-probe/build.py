import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--server-dir',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--classpath-extra',default='');a=p.parse_args()
s=a.server_dir.resolve();src=Path(__file__).parent.resolve();classes=s/'probe-build';classes.mkdir(exist_ok=True)
if not (s/'ALLOW_DISPOSABLE_PLAYER_EXPERIENCE_PROBE').is_file():raise SystemExit('Disposable marker required')
jars=[*s.joinpath('versions').rglob('*.jar'),*s.joinpath('libraries').rglob('*.jar'),*s.joinpath('plugins').glob('*.jar')];jars=[j for j in jars if j.name!='PlayerExperienceProbe.jar']+[Path(x) for x in a.classpath_extra.split(os.pathsep) if x]
subprocess.run([str(a.java_home/'bin/javac'),'-encoding','UTF-8','-d',str(classes),'-cp',os.pathsep.join(map(str,jars)),str(src/'PlayerExperienceProbe.java')],check=True)
subprocess.run([str(a.java_home/'bin/jar'),'--create','--file',str(s/'plugins/PlayerExperienceProbe.jar'),'-C',str(classes),'.','-C',str(src),'plugin.yml'],check=True)
