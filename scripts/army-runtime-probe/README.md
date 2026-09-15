# Army native runtime probe

Use only a disposable loopback Paper/Purpur 26.2 server with Java 25, Towny 0.103.2.0, Vault, Essentials economy, PlaceholderAPI and every addon from `versions.yml`. The runner requires the `ALLOW_DISPOSABLE_RELIABILITY_PROBE` marker. The plugin additionally requires `-Dneverland.runtimeProbe=true` and `server-ip=127.0.0.1`.

**The first phase writes a fixture legacy Army file, creates an ArmyAudit town, residents, claimed territory, construction metadata, city/Army resources and military records. Never install this probe on a player server.** Archive the initial disposable data before running it; restore that snapshot before repeating the first phase. Remove stale old addon JARs. Do not remove first-phase data before the restart phase.

```sh
python scripts/army-runtime-probe/build.py --server-dir /path/to/disposable --java-home /path/to/jdk25
python scripts/army-runtime-probe/run.py --server-dir /path/to/disposable --java-home /path/to/jdk25 --phase first
python scripts/army-runtime-probe/run.py --server-dir /path/to/disposable --java-home /path/to/jdk25 --phase restart
```

Use `--extra-jar /path/to/annotations.jar` for compilation if the runtime omits JetBrains annotations. Expected addon versions, contract capabilities and counts are generated from source by `build.py`.

The native scenario verifies all providers/versions, legacy migration/retirement, real operational infrastructure queries, RP age, admission/oath, rank gates, Council scope/revocation, real Resources reserve/consume/credit recovery, equipment and garrison, Citizens restrictions, discipline, menus/back navigation, command ownership/completion, Espionage defense, immutable API and main-thread guard. Restart verifies persisted records and finishes a real consumed resource transfer without a second debit. Repeated-query timing is recorded as a small diagnostic, not a TPS/load benchmark.

Player command/menu actors are in-process fixtures, not connected Minecraft clients. Building metadata represents prepared infrastructure; the probe does not build full physical buildings or simulate aircraft, boats, NPCs or physical military equipment. Training progression/failure gates are exercised by pure tests; the native fixture checks duty and interruption, not a real player's movement session.

Proof and per-assertion logs: `plugins/NeverLandArmyRuntimeProbe/`. Runner logs and exact installed JAR SHA-256 maps: `army-first.log`, `army-restart.log`, `army-first-jars.json`, `army-restart-jars.json`. Both phases must succeed. Stop the host and remove the probe JAR after checking.
