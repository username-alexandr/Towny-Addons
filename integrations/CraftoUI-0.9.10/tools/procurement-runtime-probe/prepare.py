"""Fresh isolated Folia fixture; exact validated sources and no replacement of existing data."""
from pathlib import Path
import argparse,shutil,yaml,zipfile,json,hashlib
p=argparse.ArgumentParser(description=__doc__)
for key in ['server','template','addons-root','towny','ui-jar']:p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();server=a.server.resolve();template=a.template.resolve();root=a.addons_root.resolve();ui=Path(__file__).resolve().parents[2]
assert not server.exists()
assert (template/'ALLOW_DISPOSABLE_RAID_PROBE').is_file() and 'server-ip=127.0.0.1' in (template/'server.properties').read_text()
def verified(jar,source,metadata):
    with zipfile.ZipFile(jar) as archive:manifest=json.loads(archive.read(metadata))
    assert all((source/path).is_file() and hashlib.sha256((source/path).read_bytes()).hexdigest()==sha for path,sha in manifest['inputs'].items()),'Stale artifact: '+str(jar)
    return manifest
versions=yaml.safe_load((root/'versions.yml').read_text())['addons'];jars=[]
for short in ['Citizens','Control','Builds','Contracts','TreasuryPlus']:
    name='NeverLandTowny'+short;jar=root/'modules'/name/'build/libs'/f'{name}-{versions[name]}.jar'
    manifest=verified(jar,root,'META-INF/neverland-build.json');assert manifest['version']==versions[name];jars.append(jar)
verified(a.ui_jar,ui,'META-INF/craftoui-build.json')
def copy_runtime(src,dst):
    if Path(src).suffix=='.jar':Path(dst).symlink_to(Path(src).resolve(strict=True));return str(dst)
    return shutil.copy2(src,dst)
shutil.copytree(template,server,copy_function=copy_runtime,ignore=shutil.ignore_patterns('logs','plugins','crash-reports','validation','probe-classes','session.lock'))
plugins=server/'plugins';plugins.mkdir()
for jar in [*jars,a.towny.resolve(),a.ui_jar.resolve()]:shutil.copy2(jar,plugins/jar.name)
for rel,data in [('Towny/settings/config.yml',{'economy':{'using_economy':False},'plugin':{'update_notifications':False}}),('bStats/config.yml',{'enabled':False}),('NeverLandTownyContracts/config.yml',{'contracts':{'max-active-per-town':10,'expiration-check-seconds':1},'building-orders':{'enabled':True,'default-unit-reward':'0.10','maximum-reward':'50000.00','duration-seconds':86400}})]:
    path=plugins/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(yaml.safe_dump(data))
path=plugins/'faststats/config.properties';path.parent.mkdir();path.write_text('enabled=false\n')
config=server/'config/paper-global.yml';data=yaml.safe_load(config.read_text());data.setdefault('update-checker',{})['enabled']=False;data.setdefault('threaded-regions',{})['threads']=2;config.write_text(yaml.safe_dump(data))
print('Prepared verified native CraftoUI procurement fixture:',server)
