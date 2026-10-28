#!/usr/bin/env python3
"""Read-only preflight of an installed Folia plugin set; never loads Java code or prints config values."""
from __future__ import annotations

import argparse
import json
import re
import struct
import zipfile
from pathlib import Path

import yaml

MAX_METADATA = 2 * 1024 * 1024


def metadata(archive: zipfile.ZipFile, name: str) -> dict:
    entry = archive.getinfo(name)
    if entry.file_size > MAX_METADATA:
        raise ValueError('descriptor is too large')
    value = yaml.safe_load(archive.read(entry).decode('utf-8-sig'))
    if not isinstance(value, dict):
        raise ValueError('descriptor must be a mapping')
    return value


def check(plugins: Path, java: int = 25, matrix: dict | None = None) -> dict:
    findings: list[dict] = []
    installed: list[dict] = []

    def issue(level: str, code: str, message: str, files=()):
        findings.append(dict(level=level, code=code, message=message, files=list(files)))

    if not plugins.is_dir():
        issue('error', 'PLUGINS_ABSENT', 'Папка plugins не найдена.')
        return dict(ok=False, plugins=[], findings=findings)
    for jar in sorted(plugins.glob('*.jar')):
        try:
            with zipfile.ZipFile(jar) as archive:
                descriptors = [n for n in ('paper-plugin.yml', 'plugin.yml') if n in archive.namelist()]
                if not descriptors:
                    issue('error', 'NO_DESCRIPTOR', 'JAR не является серверным плагином; проверьте, не положена ли сюда библиотека API.', [jar.name])
                    continue
                desc = metadata(archive, descriptors[0])
                name, main = desc.get('name'), desc.get('main')
                if not isinstance(name, str) or not re.fullmatch(r'[A-Za-z0-9_. -]{1,128}', name) or not isinstance(main, str):
                    raise ValueError('invalid plugin name/main')
                main_entry = main.replace('.', '/') + '.class'
                header = archive.open(main_entry).read(8)
                if len(header) != 8 or header[:4] != b'\xca\xfe\xba\xbe':
                    raise ValueError('main is not a Java class')
                major = struct.unpack('>H', header[6:8])[0]
                row = dict(file=jar.name, name=name, version=str(desc.get('version', '?')), java=max(1, major-44),
                           folia=desc.get('folia-supported') is True, hard=[], aliases=[], before=[], after=[])
                for key in ('depend', 'provides', 'loadbefore', 'softdepend'):
                    if key in desc and (not isinstance(desc[key], list) or any(not isinstance(x, str) for x in desc[key])):
                        raise ValueError('invalid dependency list: ' + key)
                row['hard'] = desc.get('depend', [])
                row['aliases'] = desc.get('provides', [])
                row['before'] = desc.get('loadbefore', [])
                row['after'] = desc.get('softdepend', [])
                if descriptors[0] == 'paper-plugin.yml':
                    deps = (desc.get('dependencies') or {}).get('server') or {}
                    if not isinstance(deps, dict):
                        raise ValueError('invalid Paper dependency mapping')
                    for dep, options in deps.items():
                        if not isinstance(dep, str) or not isinstance(options, dict):
                            raise ValueError('invalid Paper dependency')
                        if options.get('required', True):
                            row['hard'].append(dep)
                        if options.get('load') == 'BEFORE':
                            row['after'].append(dep)
                        elif options.get('load') == 'AFTER':
                            row['before'].append(dep)
                installed.append(row)
                if not row['folia']:
                    issue('error', 'FOLIA_UNDECLARED', 'Плагин не заявляет поддержку Folia.', [jar.name])
                if row['java'] > java:
                    issue('error', 'JAVA_TOO_OLD', f'Главному классу требуется Java {row["java"]}, указана Java {java}.', [jar.name])
                if name.startswith('NeverLandTowny'):
                    for required in ('ru/neverland/core/SuiteState.class', 'ru/neverland/core/SuiteScheduler.class'):
                        if required not in archive.namelist():
                            issue('error', 'SUITE_RUNTIME_ABSENT', 'Отсутствует общий механизм NeverLand.', [jar.name])
                    if matrix and str((matrix.get('addons') or {}).get(name, '')) != row['version']:
                        issue('warning', 'VERSION_MISMATCH', 'Версия отличается от проверяемого набора релиза.', [jar.name])
        except (OSError, ValueError, KeyError, zipfile.BadZipFile, yaml.YAMLError, UnicodeError, AttributeError, TypeError) as exc:
            # Exception messages may contain parsed YAML scalar values; do not echo them.
            issue('error', 'INVALID_JAR', 'Не удалось проверить JAR или его описание ('+type(exc).__name__+').', [jar.name])

    providers: dict[str, list[dict]] = {}
    for row in installed:
        for name in set([row['name'], *row['aliases']]):
            providers.setdefault(name.lower(), []).append(row)
    for name, rows in sorted(providers.items()):
        if len(rows) > 1:
            issue('error', 'DUPLICATE_NAME', 'Несколько JAR объявляют одно имя плагина: '+name, [r['file'] for r in rows])
    graph = {row['file']: set() for row in installed}
    for row in installed:
        for dep in row['hard']:
            choices = providers.get(dep.lower(), [])
            if not choices:
                issue('error', 'MISSING_DEPENDENCY', f'{row["name"]}: не установлена обязательная зависимость {dep}.', [row['file']])
            elif len(choices) == 1:
                graph[row['file']].add(choices[0]['file'])
        for dep in row['after']:
            if len(providers.get(dep.lower(), [])) == 1:
                graph[row['file']].add(providers[dep.lower()][0]['file'])
        for dep in row['before']:
            if len(providers.get(dep.lower(), [])) == 1:
                graph[providers[dep.lower()][0]['file']].add(row['file'])
    pending = dict((key, set(value)) for key, value in graph.items())
    while pending:
        ready = [key for key, value in pending.items() if not value]
        if not ready:
            issue('error', 'LOAD_CYCLE', 'Цикл порядка загрузки плагинов.', sorted(pending))
            break
        for key in ready:
            pending.pop(key)
        for value in pending.values():
            value.difference_update(ready)

    # Check user configuration syntax without logging secrets or modifying anything.
    for directory in sorted(plugins.glob('NeverLandTowny*')):
        if not directory.is_dir() or directory.is_symlink():
            continue
        for config in sorted(directory.glob('*.yml')):
            if config.name.endswith(('-state.yml', '-data.yml')) or config.stat().st_size > MAX_METADATA:
                continue
            try:
                value = yaml.safe_load(config.read_text(encoding='utf-8-sig'))
                if value is not None and not isinstance(value, dict):
                    raise ValueError('configuration root')
            except (OSError, UnicodeError, ValueError, yaml.YAMLError):
                issue('error', 'INVALID_CONFIG', 'Не удалось прочитать YAML; значения не выводятся.', [str(config.relative_to(plugins))])
    if any(r['name'].lower() == 'vault' for r in installed):
        issue('info', 'ECONOMY_RUNTIME_CHECK', 'Vault установлен. Наличие зарегистрированного провайдера экономики и готовность Towny нужно проверить после запуска командой /nltadmin; один JAR Vault этого не подтверждает.')
    if not installed:
        issue('error', 'NO_PLUGINS', 'Не найдено ни одного проверяемого серверного плагина.')
    return dict(ok=not any(f['level'] == 'error' for f in findings), plugins=installed, findings=findings)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plugins', type=Path, required=True)
    parser.add_argument('--java', type=int, default=25)
    parser.add_argument('--matrix', type=Path)
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args()
    if not 17 <= args.java <= 99:
        parser.error('--java должен быть номером версии Java, например 25')
    result = check(args.plugins, args.java, yaml.safe_load(args.matrix.read_text()) if args.matrix else None)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print('Проверено плагинов:', len(result['plugins']))
        for entry in result['findings']:
            print(entry['level'].upper()+': '+entry['message']+(' ['+', '.join(entry['files'])+']' if entry['files'] else ''))
        print('Проверка пройдена.' if result['ok'] else 'Перед запуском исправьте ошибки выше.')
    return 0 if result['ok'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
