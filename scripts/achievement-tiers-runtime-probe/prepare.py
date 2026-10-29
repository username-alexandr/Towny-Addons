#!/usr/bin/env python3
"""Fresh loopback-only native fixture. Requires validated JARs and an explicitly marked stopped template."""
from pathlib import Path
import argparse,shutil,yaml,hashlib,uuid,time
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--template',type=Path,required=True);p.add_argument('--server',type=Path,required=True);p.add_argument('--towny',type=Path,required=True)
a=p.parse_args();template=a.template.resolve();server=a.server.resolve();root=Path(__file__).resolve().parents[2]
assert not server.exists(),'Refusing to overwrite existing server'
assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
shutil.copytree(template,server,ignore=shutil.ignore_patterns('logs','plugins','crash-reports','validation','probe-classes','session.lock'))
plugins=server/'plugins';plugins.mkdir();shutil.copy2(a.towny,plugins/'Towny-0.103.2.0.jar')
versions=yaml.safe_load((root/'versions.yml').read_text())['addons']
for short in ['Citizens','Control','Builds','Achievements']:
    name='NeverLandTowny'+short;jar=root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar';assert jar.is_file(),jar;shutil.copy2(jar,plugins)
for rel,data in [('Towny/settings/config.yml',{'economy':{'using_economy':False},'plugin':{'update_notifications':False}}),('bStats/config.yml',{'enabled':False})]:
    path=plugins/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(yaml.safe_dump(data))
path=plugins/'faststats/config.properties';path.parent.mkdir(exist_ok=True);path.write_text('enabled=false\n')
config=server/'config/paper-global.yml';data=yaml.safe_load(config.read_text());data.setdefault('update-checker',{})['enabled']=False;data.setdefault('threaded-regions',{})['threads']=2;config.write_text(yaml.safe_dump(data))
def named(s):
    b=bytearray(hashlib.md5(('admissions-052-'+s).encode()).digest());b[6]=(b[6]&15)|48;b[8]=(b[8]&63)|128;return str(uuid.UUID(bytes=bytes(b)))
t,r=named('town'),named('legacy');now=int(time.time()*1000)
path=plugins/'NeverLandTownyCitizens/citizens.yml';path.parent.mkdir();path.write_text(yaml.safe_dump({'schema':1,'records':{t+'/'+r:{'town':t,'resident':r,'status':'TEMPORARY','expires-at':now+86400000*7,'changed-at':now,'actor':'LegacyMayor','reason':'Legacy preserved'}}},allow_unicode=True))
print('Prepared isolated native achievement tiers fixture:',server)


properties=server/'server.properties';text=properties.read_text();import re;text=re.sub(r'^server-port=.*$', 'server-port=25673', text, flags=re.M);properties.write_text(text)
# Real legacy five-goal config and persisted two rewards without any tier metadata.
source=yaml.safe_load((root/'modules/NeverLandTownyAchievements/src/main/resources/achievements.yml').read_text())
originals=['first_million','population_1000','raid_veterans','all_buildings_v','first_wonder']
source['achievements']={key:source['achievements'][key] for key in originals}
folder=plugins/'NeverLandTownyAchievements';folder.mkdir();(folder/'achievements.yml').write_text(yaml.safe_dump(source,allow_unicode=True,sort_keys=False))
progress={key:{'definition':source['achievements'][key],'best':source['achievements'][key]['target'],'earned-at':123456} for key in ['first_million','first_wonder']}
(folder/'achievements-data.yml').write_text(yaml.safe_dump({'schema':1,'towns':{t:{'paused':False,'title':'first_million','progress':progress}},'cosmetics':{named('mayor'):{'town':t,'achievement':'first_million'}}},allow_unicode=True,sort_keys=False))
