"""Publish only a validated artifact from unchanged sources; preserve the previous JAR on failure."""
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile
from contextlib import contextmanager
import fcntl

@contextmanager
def build_lock(directory):
    """Two compilers must never share the same classes directory or publish each other's output."""
    directory=Path(directory);directory.mkdir(parents=True,exist_ok=True)
    with (directory/'.pinned-build.lock').open('a') as lock:
        try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError as error:raise RuntimeError('This module is already building; previous JAR preserved.') from error
        try:yield
        finally:fcntl.flock(lock,fcntl.LOCK_UN)

def capture(root, paths):
    root = Path(root).resolve()
    result = {}
    for source in sorted(set(map(Path, paths))):
        relative = source.resolve().relative_to(root).as_posix()
        result[relative] = hashlib.sha256(source.read_bytes()).hexdigest()
    return result

def publish(classes, target, expected, current, details):
    target = Path(target)
    if current() != expected:
        raise RuntimeError('Sources changed during build; previous JAR preserved. Build again.')
    target.parent.mkdir(parents=True, exist_ok=True)
    descriptor = dict(details, schema=1, inputs=expected)
    descriptor['source_sha256'] = hashlib.sha256(json.dumps(expected,sort_keys=True).encode()).hexdigest()
    descriptor['tests_passed'] = True
    handle, name = tempfile.mkstemp(prefix='.validated-',suffix='.tmp',dir=target.parent)
    os.close(handle)
    temporary = Path(name)
    try:
        with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(Path(classes).rglob('*')):
                if file.is_file(): archive.write(file,file.relative_to(classes).as_posix())
            archive.writestr('META-INF/neverland-build.json',json.dumps(descriptor,sort_keys=True,ensure_ascii=False)+'\n')
        if current() != expected:
            raise RuntimeError('Sources changed while packaging; previous JAR preserved. Build again.')
        with temporary.open('rb') as stream: os.fsync(stream.fileno())
        os.replace(temporary,target)
    finally:
        temporary.unlink(missing_ok=True)
