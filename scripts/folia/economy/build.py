"""Compile isolated native test plugins; NEVER package fixture JARs as release plugins."""
import os, subprocess, sys
from pathlib import Path
server=Path(sys.argv[1]).resolve(); root=Path(__file__).resolve().parent
java=Path(os.environ['JAVA_HOME'])/'bin'
cp=os.pathsep.join(str(p) for parent in [server/'libraries',server/'plugins'] for p in parent.rglob('*.jar'))
for name,deps,extra in [('EconomyFixture',['Vault'],'loadbefore: [Towny]\n'),('EconomyProbe',['Towny','NeverLandTownyContracts','NeverLandTownyJobs','NeverLandTownyBuilds','NeverLandTownyCompanies','NeverLandTownyTaxes'],'')]:
    out=root/'build'/name;out.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(out),str(root/(name+'.java'))],check=True)
    (out/'plugin.yml').write_text(f'name: {name}\nversion: 1.0\nmain: {name}\napi-version: "26.2"\nfolia-supported: true\ndepend: [{", ".join(deps)}]\n'+extra)
    subprocess.run([str(java/'jar'),'cf',str(server/'plugins'/(name+'.jar')),'-C',str(out),'.'],check=True)
