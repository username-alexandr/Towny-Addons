# Army acknowledged-state probe

Use only a fresh, stopped, loopback-only Folia 26.2 fixture with the explicit `ALLOW_DISPOSABLE_RAID_PROBE` marker. `prepare.py` refuses an existing destination, copies mutable runtime files and links immutable runtime JARs from the marked template. It installs the versions in `versions.yml`; build these artifacts first.

```sh
python scripts/army-async-runtime-probe/prepare.py --template /path/to/marked-template --server /dev/shm/army-native --towny /path/to/Towny-0.103.2.0.jar
python scripts/army-async-runtime-probe/build.py --server-dir /dev/shm/army-native --java-home /path/to/jdk25 --annotations /path/to/annotations.jar
python scripts/army-async-runtime-probe/run.py --server /dev/shm/army-native --java-home /path/to/jdk25
```

The runner starts two JVMs sequentially, verifies fresh success markers, exact unchanged JAR hashes, exit codes and absence of addon lifecycle errors. It stops/kills the child on timeout. Never start another native server concurrently. Proof lives under `plugins/ArmyAsyncRuntimeProbe`; full logs and hashes are under `validation`.

The probe creates a disposable town, residents, a claim, accepted building metadata and stock, then uses actual Army services/menu actions, Resources transfers, physical writer delays and Sieges camp receipts. It checks readable state during writes, equipment debit, quit-time duty accounting, AFK cutoff, replay, expired and ended wars, malformed provider results, restart recovery and shutdown under an external Suite lock. Periodic tasks are cancelled explicitly so each phase is controlled. The first phase retains a consumed Resources receipt awaiting Army credit and an Army debit awaiting camp acknowledgement.

Offline Player/InventoryView/EntityScheduler, the completed building metadata, virtual elapsed duty timestamps and accepted war records are explicit fixtures. No real network client, live PvP, physical military kit, bank backend or full 40-addon test is claimed. PlaceholderAPI is neither required nor tested here: service access uses the public Army API and scheduler API instead of reflecting optional expansion fields.

To repeat the same acknowledgement scenarios for Army 0.3.0, pass `--army-version 0.3.0` to the runner. Its default remains 0.2.2 for the historical evidence. The exact assertion is never removed; the requested version must match the installed artifact.
