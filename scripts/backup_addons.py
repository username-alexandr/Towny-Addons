#!/usr/bin/env python3
"""Consistent offline backup of plugins, pending operations, signing keys and server configuration."""
from __future__ import annotations

import argparse
import contextlib
import fcntl
import hashlib
import json
import os
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def selected(server: Path) -> list[Path]:
    roots = [server / 'plugins', server / 'config']
    roots += [server / name for name in ('server.properties', 'bukkit.yml', 'spigot.yml', 'paper.yml', 'permissions.yml')]
    files = []
    for root in roots:
        if root.is_symlink():
            raise ValueError('Ссылки в наборе копирования не поддерживаются: '+str(root.relative_to(server)))
        if not root.exists():
            continue
        entries = root.rglob('*') if root.is_dir() else [root]
        for file in entries:
            if file.is_symlink():
                raise ValueError('Найдена ссылка в данных плагинов; копирование остановлено.')
            if file.is_file():
                files.append(file)
    return sorted(set(files))


def fingerprint(path: Path) -> tuple:
    s = path.stat()
    return s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns, s.st_ctime_ns


def backup(server: Path, destination: Path) -> dict:
    server, destination = server.resolve(), destination.absolute()
    if not (server / 'server.properties').is_file() or not (server / 'plugins').is_dir():
        raise ValueError('Укажите папку сервера с server.properties и plugins.')
    if destination.exists() or server == destination or server in destination.resolve().parents:
        raise ValueError('Нужен новый ZIP за пределами папки сервера.')
    locks = sorted(server.rglob('session.lock'))
    if not locks:
        raise ValueError('Не найдены блокировки миров. Копировать незнакомую папку как остановленный сервер нельзя.')
    temporary = None
    try:
        with contextlib.ExitStack() as stack:
            for lock in locks:
                if lock.is_symlink() or not lock.is_file():
                    raise ValueError('Неверный файл блокировки мира.')
                handle = stack.enter_context(lock.open('r+b'))
                try:
                    fcntl.lockf(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    raise ValueError('Мир занят работающим сервером. Остановите сервер перед копированием.') from None
            files = selected(server)
            before = {f: fingerprint(f) for f in files}
            manifest = dict(schema=1, created=datetime.now(timezone.utc).isoformat(), scope='plugins-and-server-config',
                            worlds_included=False, files=[])
            destination.parent.mkdir(parents=True, exist_ok=True)
            fd, name = tempfile.mkstemp(prefix='neverland-backup-', suffix='.partial', dir=destination.parent)
            temporary = Path(name)
            os.fchmod(fd, 0o600)
            with os.fdopen(fd, 'w+b') as stream:
                with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED, allowZip64=True) as archive:
                    for file in files:
                        name = file.relative_to(server).as_posix()
                        digest = hashlib.sha256()
                        with file.open('rb') as source, archive.open(name, 'w', force_zip64=True) as target:
                            while chunk := source.read(1024 * 1024):
                                target.write(chunk)
                                digest.update(chunk)
                        if fingerprint(file) != before[file]:
                            raise ValueError('Во время копирования изменились данные; согласованная копия не создана.')
                        manifest['files'].append(dict(path=name, bytes=before[file][2], sha256=digest.hexdigest()))
                    if selected(server) != files or any(fingerprint(f) != before[f] for f in files):
                        raise ValueError('Набор данных изменился; согласованная копия не создана.')
                    archive.writestr('NEVERLAND-BACKUP.json', json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
                stream.flush()
                os.fsync(stream.fileno())
            # No replacement of a pre-existing backup, even if another process creates it now.
            os.link(temporary, destination)
            temporary.unlink()
            temporary = None
            return dict(path=str(destination), files=len(files), bytes=sum(r['bytes'] for r in manifest['files']), worlds_included=False)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = backup(args.server, args.output)
    except (OSError, ValueError) as error:
        print('Копия не создана: '+str(error))
        return 1
    print('Сохранено файлов: '+str(result['files'])+'. Архив: '+result['path'])
    print('Включены все данные плагинов, очереди операций, базы с WAL и ключи подписей. Миры копируются отдельно.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
