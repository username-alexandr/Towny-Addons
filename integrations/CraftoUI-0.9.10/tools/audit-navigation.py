#!/usr/bin/env python3
"""Static route audit against the supplied real addon source tree (requires PyYAML)."""
import argparse,json,re
from pathlib import Path
import yaml
p=argparse.ArgumentParser();p.add_argument('addons',type=Path);a=p.parse_args()
root=Path(__file__).resolve().parents[1];r=root/'server/src/main/resources';out=root/'validation/navigation-0.7.2';out.mkdir(parents=True,exist_ok=True)
modules=json.loads((r/'addons.json').read_text())['modules'];evidence=[]
for m in modules:
 base=a.addons/'modules'/m['plugin'];y=yaml.safe_load((base/'src/main/resources/plugin.yml').read_text());parts=m['command'].split();command=parts[0]
 scripts=list((base/'src/main/java').rglob('*.java'));hits=[]
 if command=='town':
  for f in scripts:
   s=f.read_text()
   if re.search(r'(?:register(?:TownCommand)?\("'+parts[1]+r'"|TOWN_COMMANDS[^;]+"'+parts[1]+r'")',s):hits.append(str(f.relative_to(a.addons)))
  assert hits,m['command']
 else:
  assert any(k==command or command in (v or {}).get('aliases',[]) for k,v in y.get('commands',{}).items()),m['command']
  hits.append(str((base/'src/main/resources/plugin.yml').relative_to(a.addons)))
 assert any(m['permission'] in f.read_text() for f in scripts) or m['permission'] in y.get('permissions',{}),m['permission']
 evidence.append(dict(module=m['id'],plugin=m['plugin'],label=m['label'],command=m['command'],permission=m['permission'],source=hits))
routes=[]
quick=json.loads((r/'quick-actions.json').read_text());custom=json.loads((r/'actions.json').read_text());by_id={m['id']:m for m in modules}
def walk(nodes):
 for n in nodes:
  yield n
  yield from walk(n.get('children',[]))
for f in sorted((r/'menus').glob('*')):
 if f.suffix not in ('.json','.yml'):continue
 m=json.loads(f.read_text()) if f.suffix=='.json' else yaml.safe_load(f.read_text())
 if m['id']=='main':continue
 for n in walk(m['components']):
  if n.get('type')!='button':continue
  action=n.get('action','');target=action;permission=n.get('permission','')
  if action.startswith('addons:'):v=by_id[action[7:]];target='/'+v['command'];permission=v['permission']
  elif action.startswith('quick:'):v=quick[action[6:]];target='/'+v['command'];permission=v['permission']
  elif action.startswith('hub:'):target='меню '+action[4:]
  elif action in custom:target=custom[action].get('menu',custom[action]['type'])
  routes.append(dict(menu=m['title'],button=n['text'],action=action,target=target,permission=permission))
# All rarity item symbols are cross-checked against the actual Contracts implementation.
source=(a.addons/'modules/NeverLandTownyContracts/src/main/java/ru/neverland/mintcontracts/orders/PublicOrders.java').read_text()
source=source[source.index('private static Material iconMaterial'):source.index('private ItemStack icon(')]
style=(root/'common/src/main/java/cc/crafto/ui/OrderRarityStyle.java').read_text()
icons=re.findall(r'->Material\.([A-Z_]+)',source)
assert len(icons)==20
for icon in icons:assert '"'+icon.lower()+'"' in style,icon
(out/'source-audit.json').write_text(json.dumps(dict(addons=evidence,routes=routes,rarityIcons=icons),ensure_ascii=False,indent=2)+'\n')
report=['# Карта переходов CraftoUI 0.7.2','','Команды и права сверены с исходниками Towny-Addons. Это статическая сверка точек входа; не прохождение всех игровых операций.','','| Раздел | Кнопка | Открывается |','|---|---|---|']
report += [f"| {x['menu']} | {x['button']} | `{x['target']}` |" for x in routes]
report+=['','## Данные редкости','','Предметы всех сочетаний типа и редкости сверены с `PublicOrders.iconMaterial`. Значки, цвет названия и зачарованный блеск взяты из правил Contracts. Генерация заказов, вероятность редкости и награды не меняются.']
(root/'docs/NAVIGATION-0.7.2.md').write_text('\n'.join(report)+'\n')
print(f'PASS: {len(modules)} addon entry points, {len(routes)} menu buttons, {len(icons)} rarity item mappings')
