# Native Council spending fixture

Use only a stopped, explicitly marked disposable loopback Folia template. `prepare.py` refuses an existing target and requires `ALLOW_DISPOSABLE_RAID_PROBE`; it symlinks immutable runtime JARs and copies configurations and plugin candidates. `build.py` compiles against real Folia/Towny and addon JARs. `run.py` runs four sequential JVMs, corrupting only the fixture's new spending sidecar between stopped processes and restoring its confirmed copy afterward.

```
python scripts/council-spending-runtime-probe/prepare.py --template /path/to/stopped-template --server /dev/shm/new-limits-fixture --towny /path/to/Towny-0.103.2.0.jar
python scripts/council-spending-runtime-probe/build.py --server-dir /dev/shm/new-limits-fixture --java-home /path/to/jdk25
python scripts/council-spending-runtime-probe/run.py --server /dev/shm/new-limits-fixture --java-home /path/to/jdk25
```

Do not copy the probe JAR into a production pack. Exact artifact hashes, all input hashes, results, explicit substitutes and failed-pilot explanations are recorded in `validated-versions.json` and `docs/validation/city-systems-052/council-spending`.
