#!/usr/bin/env python3
"""Fail when any real addon InventoryHolder is missing from CraftoUI's explicit allowlist."""
import argparse, json, re
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('addons',type=Path)
p.add_argument('--output',type=Path)
a=p.parse_args()
root=Path(__file__).resolve().parents[1]
modules=json.loads((root/'server/src/main/resources/addons.json').read_text())['modules']
listed={module['plugin'] for module in modules}
for directory in (a.addons/'modules').iterdir():
    if not directory.is_dir() or directory.name in listed: continue
    for path in directory.glob('src/main/java/**/*.java'):
        source=path.read_text()
        assert 'InventoryHolder' not in source and 'createInventory' not in source, f'Module with menus is absent from catalog: {path}'
evidence=[]; missing=[]
pattern=re.compile(r'\b(class|record|interface)\s+(\w+)\b[^;{}]*?\b(?:implements|extends)\s+([^;{}]+)')
for module in modules:
    known=set(module['holders']); found=set()
    for path in (a.addons/'modules'/module['plugin']/'src/main/java').rglob('*.java'):
        source=path.read_text(); package=re.search(r'\bpackage\s+([\w.]+);',source)
        if not package: continue
        declarations=[(kind,name,re.findall(r'[\w.$]+',parents)) for kind,name,parents in pattern.findall(source)]
        holder_types={'InventoryHolder','org.bukkit.inventory.InventoryHolder'}
        while True:
            added={name for _,name,parents in declarations if any(parent in holder_types for parent in parents)}-holder_types
            if not added: break
            holder_types.update(added)
        for kind,name,_ in declarations:
            if kind=='interface' or name not in holder_types: continue
            binary=package.group(1)+'.'+path.stem+('' if name==path.stem else '$'+name)
            found.add(binary)
            evidence.append({'plugin':module['plugin'],'holder':binary,'allowlisted':binary in known,'source':str(path.relative_to(a.addons))})
        # Anonymous menu holders cannot be explicitly named in the catalog safely.
        assert not re.search(r'new\s+InventoryHolder\s*\(',source),f'Anonymous holder needs review: {path}'
    missing.extend(sorted(found-known))
    assert found, f'No holders discovered for {module["plugin"]}; audit needs updating'
if a.output:
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps({'modules':len(modules),'holders':evidence,'missing':missing},ensure_ascii=False,indent=2)+'\n')
if missing: raise SystemExit('Missing CraftoUI menus:\n'+'\n'.join(missing))
print(f'PASS: {len(evidence)} inventory holders across {len(modules)} addons; no unrecognized menus')
