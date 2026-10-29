#!/usr/bin/env python3
"""Fresh loopback-only native fixture. Requires validated JARs and an explicitly marked stopped template."""
from pathlib import Path
import argparse,shutil,yaml,hashlib,uuid,time,re
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--template',type=Path,required=True);p.add_argument('--server',type=Path,required=True);p.add_argument('--towny',type=Path,required=True);p.add_argument('--port',type=int,default=25676);p.add_argument('--control',type=Path)
a=p.parse_args();template=a.template.resolve();server=a.server.resolve();root=Path(__file__).resolve().parents[2]
assert not server.exists(),'Refusing to overwrite existing server'
assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
shutil.copytree(template,server,ignore=shutil.ignore_patterns('logs','plugins','crash-reports','validation','probe-classes','session.lock','libraries'))
(server/'libraries').symlink_to((template/'libraries').resolve(),target_is_directory=True)
assert 1024<=a.port<=65535
properties=server/'server.properties';properties.write_text(re.sub(r'^server-port=.*$',f'server-port={a.port}',properties.read_text(),flags=re.M))
plugins=server/'plugins';plugins.mkdir();shutil.copy2(a.towny,plugins/'Towny-0.103.2.0.jar')
versions=yaml.safe_load((root/'versions.yml').read_text())['addons']
for short in ['Control','TreasuryPlus','Justice','Espionage']:
    name='NeverLandTowny'+short;jar=a.control if short=='Control' and a.control else root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar';assert jar.is_file(),jar;shutil.copy2(jar,plugins)
for rel,data in [('Towny/settings/config.yml',{'economy':{'using_economy':False},'plugin':{'update_notifications':False}}),('bStats/config.yml',{'enabled':False})]:
    path=plugins/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(yaml.safe_dump(data))
path=plugins/'faststats/config.properties';path.parent.mkdir(exist_ok=True);path.write_text('enabled=false\n')
config=server/'config/paper-global.yml';data=yaml.safe_load(config.read_text());data.setdefault('update-checker',{})['enabled']=False;data.setdefault('threaded-regions',{})['threads']=2;config.write_text(yaml.safe_dump(data))
print('Prepared isolated native caller treasury fixture:',server)
