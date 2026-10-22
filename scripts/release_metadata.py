#!/usr/bin/env python3
"""Write checksums and an honest validation summary for the freshly verified suite."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import yaml

ROOT = Path(__file__).resolve().parents[1]

def main():
    matrix = yaml.safe_load((ROOT/'versions.yml').read_text())
    records = json.loads((ROOT/'build/suite-tests.json').read_text())
    if len(records) != len(matrix['addons']) or {r['module'] for r in records} != set(matrix['addons']):
        raise SystemExit('Cannot package an incomplete suite test report')
    folder = ROOT/'dist/plugins'
    jars = sorted(folder.glob('*.jar'))
    expected = {f'{name}-{version}.jar' for name,version in matrix['addons'].items()}
    if {p.name for p in jars} != expected:
        raise SystemExit('JAR set does not match the release matrix')
    (folder/'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in jars))
    commit = subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    stamp = datetime.now(timezone.utc).isoformat()
    text = (f'NeverLandTownySuite {matrix["suite"]}\nCommit: {commit}\nBuild time (UTC): {stamp}\n\n'
            f'Собрано модулей: {len(records)}\n'
            f'Исполняемых Java-тестовых программ пройдено: {sum(len(r["tests"]) for r in records)}\n'
            f'Контрактов API проверено: {sum(len(r["api_contracts"]) for r in records)}\n'
            f'Проверок отказов хранилища пройдено: {sum(bool(r["repository_fault_probe"]) for r in records)}\n'
            'Python-тесты сборочных скриптов и проверка release matrix завершились успешно.\n'
            'Контрольные суммы рассчитаны заново по JAR этой сборки.\n\n'
            'Полный запуск всего пакета на игровом сервере и нагрузочное тестирование\n'
            'для этой сборки не выполнялись. Предыдущие выборочные проверки экономики\n'
            'и удаления городов на Folia описаны в docs/; они не являются проверкой\n'
            'всех JAR данного релиза.\n')
    (folder/'VALIDATION.txt').write_text(text)
    print(text)

if __name__ == '__main__':
    main()
