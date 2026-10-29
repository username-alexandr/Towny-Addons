#!/usr/bin/env python3
"""Fresh loopback-only native fixture. Requires validated JARs and an explicitly marked stopped template."""
from pathlib import Path
import argparse,shutil,yaml,hashlib,uuid,time,json,zipfile
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--template',type=Path,required=True);p.add_argument('--server',type=Path,required=True);p.add_argument('--towny',type=Path,required=True);p.add_argument('--full-suite',action='store_true')
a=p.parse_args();template=a.template.resolve();server=a.server.resolve();root=Path(__file__).resolve().parents[2]
assert not server.exists(),'Refusing to overwrite existing server'
assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
def copy_runtime(src,dst):
    if Path(src).suffix=='.jar':
        Path(dst).symlink_to(Path(src).resolve(strict=True));return str(dst)
    return shutil.copy2(src,dst)
shutil.copytree(template,server,copy_function=copy_runtime,ignore=shutil.ignore_patterns('logs','plugins','crash-reports','validation','probe-classes','session.lock'))
plugins=server/'plugins';plugins.mkdir();shutil.copy2(a.towny,plugins/'Towny-0.103.2.0.jar')
versions=yaml.safe_load((root/'versions.yml').read_text())['addons']
names=sorted(versions) if a.full_suite else ['NeverLandTowny'+short for short in ['Citizens','Control','Builds','Districts','Population','Resources','Upkeep','Quests','Policies']]
expected={}
for name in names:
    jar=root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar';assert jar.is_file(),jar
    with zipfile.ZipFile(jar) as archive:
        manifest=json.loads(archive.read('META-INF/neverland-build.json'))
        assert manifest['module']==name and manifest['version']==versions[name],name
        assert all((root/path).is_file() and hashlib.sha256((root/path).read_bytes()).hexdigest()==sha for path,sha in manifest['inputs'].items()),'Stale validated JAR: '+name
        assert all(str(path.relative_to(root)) in manifest['inputs'] for path in (root/'modules'/name/'src').rglob('*') if path.is_file()),'Uncompiled source: '+name
    shutil.copy2(jar,plugins);expected[name]=versions[name]
probe=plugins/'QuestsIsolationRuntimeProbe';probe.mkdir();(probe/'expected-versions.yml').write_text(yaml.safe_dump(expected))
(server/'fixture.json').write_text(json.dumps({'full_suite':a.full_suite,'versions':expected},indent=2)+'\n')
for rel,data in [('Towny/settings/config.yml',{'economy':{'using_economy':False},'plugin':{'update_notifications':False}}),('bStats/config.yml',{'enabled':False})]:
    path=plugins/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(yaml.safe_dump(data))
path=plugins/'faststats/config.properties';path.parent.mkdir(exist_ok=True);path.write_text('enabled=false\n')
config=server/'config/paper-global.yml';data=yaml.safe_load(config.read_text());data.setdefault('update-checker',{})['enabled']=False;data.setdefault('threaded-regions',{})['threads']=2;config.write_text(yaml.safe_dump(data))
def named(s):
    b=bytearray(hashlib.md5(('admissions-052-'+s).encode()).digest());b[6]=(b[6]&15)|48;b[8]=(b[8]&63)|128;return str(uuid.UUID(bytes=bytes(b)))
t,r=named('town'),named('legacy');now=int(time.time()*1000)
path=plugins/'NeverLandTownyCitizens/citizens.yml';path.parent.mkdir();path.write_text(yaml.safe_dump({'schema':1,'records':{t+'/'+r:{'town':t,'resident':r,'status':'TEMPORARY','expires-at':now+86400000*7,'changed-at':now,'actor':'LegacyMayor','reason':'Legacy preserved'}}},allow_unicode=True))
print('Prepared isolated native quests isolation fixture:',server)

# Authentic old two-project file: newer branches inherited from packaged defaults.
source=yaml.safe_load((root/"modules/NeverLandTownyQuests/src/main/resources/quests.yml").read_text());source["projects"].pop("industrial_future");source["projects"].pop("green_future");source["projects"]["sanitation"]["name"]="Старый городской водопровод"
path=plugins/"NeverLandTownyQuests/quests.yml";path.parent.mkdir(exist_ok=True);path.write_text(yaml.safe_dump(source,allow_unicode=True,sort_keys=False))
