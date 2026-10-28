import fcntl
import hashlib
import json
import multiprocessing
import sqlite3
import tempfile
import unittest
import zipfile
from pathlib import Path

from backup_addons import backup


def hold_world(file, pipe):
    with open(file, 'r+b') as stream:
        fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        pipe.send('locked')
        pipe.recv()


class AddonBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.server = self.root / 'server'
        self.data = self.server / 'plugins/NeverLandTownyContracts'
        self.data.mkdir(parents=True)
        (self.server / 'world').mkdir()
        (self.server / 'world/session.lock').write_bytes(b'lock-file-content')
        (self.server / 'server.properties').write_text('level-name=world\n')
        self.output = self.root / 'backup.zip'

    def tearDown(self):
        self.temp.cleanup()

    def test_pending_receipt_and_signing_key_are_in_same_snapshot(self):
        (self.data / 'secret.key').write_text('PRIVATE_KEY_TEST')
        (self.data / 'pending.yml').write_text('id: durable-unpaid-receipt\n')
        result = backup(self.server, self.output)
        self.assertEqual(result['files'], 3)
        self.assertFalse(result['worlds_included'])
        with zipfile.ZipFile(self.output) as archive:
            manifest = json.loads(archive.read('NEVERLAND-BACKUP.json'))
            for entry in manifest['files']:
                self.assertEqual(hashlib.sha256(archive.read(entry['path'])).hexdigest(), entry['sha256'])
            self.assertNotIn('PRIVATE_KEY_TEST', json.dumps(manifest))
            self.assertEqual(archive.read('plugins/NeverLandTownyContracts/secret.key'), b'PRIVATE_KEY_TEST')
            self.assertNotIn('world/session.lock', archive.namelist())
        self.assertEqual(self.output.stat().st_mode & 0o777, 0o600)

    def test_raw_sqlite_wal_recovers_committed_receipt(self):
        connection = sqlite3.connect(self.data / 'resources.db')
        connection.execute('PRAGMA journal_mode=WAL')
        connection.execute('PRAGMA wal_autocheckpoint=0')
        connection.execute('CREATE TABLE receipts(id TEXT PRIMARY KEY, state TEXT)')
        connection.commit()
        connection.execute("INSERT INTO receipts VALUES('one', 'PENDING')")
        connection.commit()
        try:
            backup(self.server, self.output)
            restored = self.root / 'restore'
            with zipfile.ZipFile(self.output) as archive:
                archive.extractall(restored)
            with sqlite3.connect(restored / 'plugins/NeverLandTownyContracts/resources.db') as restored_db:
                self.assertEqual(restored_db.execute('SELECT * FROM receipts').fetchall(), [('one', 'PENDING')])
        finally:
            connection.close()

    def test_live_world_lock_prevents_backup(self):
        parent, child = multiprocessing.Pipe()
        process = multiprocessing.Process(target=hold_world, args=(self.server / 'world/session.lock', child))
        process.start()
        try:
            self.assertEqual(parent.recv(), 'locked')
            with self.assertRaises(ValueError):
                backup(self.server, self.output)
            self.assertFalse(self.output.exists())
        finally:
            parent.send('release')
            process.join(timeout=3)
            if process.is_alive():
                process.terminate()

    def test_no_overwrite_no_recursive_backup_no_symlinks(self):
        self.output.write_bytes(b'previous-backup')
        with self.assertRaises(ValueError):
            backup(self.server, self.output)
        self.assertEqual(self.output.read_bytes(), b'previous-backup')
        with self.assertRaises(ValueError):
            backup(self.server, self.data / 'recursive.zip')
        (self.data / 'external').symlink_to(self.root / 'outside')
        with self.assertRaises(ValueError):
            backup(self.server, self.root / 'symlink.zip')


if __name__ == '__main__':
    unittest.main()
