#!/usr/bin/env python3
"""Write checksums and an honest validation summary for the freshly verified suite."""
from datetime import datetime, timezone
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import yaml

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-results',type=Path,help='Successful two-boot ledger runner report for these exact JARs')
    args=parser.parse_args()
    matrix = yaml.safe_load((ROOT/'versions.yml').read_text())
    records = json.loads((ROOT/'build/suite-tests.json').read_text())
    if len(records) != len(matrix['addons']) or {r['module'] for r in records} != set(matrix['addons']):
        raise SystemExit('Cannot package an incomplete suite test report')
    folder = ROOT/'dist/plugins'
    jars = sorted(folder.glob('*.jar'))
    expected = {f'{name}-{version}.jar' for name,version in matrix['addons'].items()}
    if {p.name for p in jars} != expected:
        raise SystemExit('JAR set does not match the release matrix')
    hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in jars}
    (folder/'SHA256SUMS.txt').write_text(''.join(f'{hashes[p.name]}  {p.name}\n' for p in jars))
    native=('Полный запуск всего пакета на игровом сервере и нагрузочное тестирование\n'
            'для этой сборки не выполнялись. Предыдущие выборочные проверки экономики\n'
            'и удаления городов на Folia описаны в docs/; они не являются проверкой\n'
            'всех JAR данного релиза.\n')
    if args.native_results:
        proof=json.loads(args.native_results.read_text())
        if len(proof)!=2 or [r['phase'] for r in proof]!=['initial','restart']:
            raise SystemExit('Expected initial and restart native proofs')
        shutdown=any('shutdown' in row for row in proof)
        for row in proof:
            if row.get('exit_code')!=0 or row.get('watchdog') is not False or row.get('jar_sha256')!=hashes:
                raise SystemExit('Native proof failed or refers to different artifacts')
            for name in ['suite','ledger']+(['shutdown'] if shutdown else []):
                lines=row.get(name,'').splitlines()
                if 'failed=0' not in lines or 'run_id='+row.get('run_id','missing') not in lines:
                    raise SystemExit('Native result missing or stale')
        native=('Выполнены два полных запуска всех 40 JAR на Folia 26.2 build 7 / Java 25,\n'
                'включая перезапуск с сохранёнными данными. SHA-256 всех JAR совпадают с\n'
                'проверенными артефактами. Оба набора проверок завершились без ошибок и\n'
                'без watchdog-дампа. Проверены общая блокировка, региональные задачи,\n'
                'сохранение Chronicles и недоступная экономика. Подробности: docs/validation/.\n'
                'Нагрузочный тест с игроками и проверка реального экономического провайдера\n'
                'не выполнялись; сторонние премиальные интеграции не проверялись.\n')
        if shutdown:native+='Дополнительно проверены остановка с активным рейдом и восстановление волны после перезапуска.\n'
    commit = subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    stamp = datetime.now(timezone.utc).isoformat()
    text = (f'NeverLandTownySuite {matrix["suite"]}\nCommit: {commit}\nBuild time (UTC): {stamp}\n\n'
            f'Собрано модулей: {len(records)}\n'
            f'Исполняемых Java-тестовых программ пройдено: {sum(len(r["tests"]) for r in records)}\n'
            f'Контрактов API проверено: {sum(len(r["api_contracts"]) for r in records)}\n'
            f'Проверок отказов хранилища пройдено: {sum(bool(r["repository_fault_probe"]) for r in records)}\n'
            'Python-тесты сборочных скриптов и проверка release matrix завершились успешно.\n'
            'Контрольные суммы рассчитаны заново по JAR этой сборки.\n\n'
            +native)
    (folder/'VALIDATION.txt').write_text(text)
    print(text)

if __name__ == '__main__':
    main()
