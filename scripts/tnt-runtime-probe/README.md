# TNT raids native probe

Opt-in fixture for a **fresh disposable loopback Folia 26.2 server**. Never install this probe on a real server: it creates towns, claims, entities, blocks and test data, changes settings in memory, and shuts the server down. It refuses to run without `ALLOW_DISPOSABLE_RAID_PROBE` and `server-ip=127.0.0.1`.

Prepare a clean server with Towny 0.103.2.0, Builds 0.8.40 and SiegesPlus 0.2.0. No logged-in player, Army or SiegeWar is required: the world test explicitly substitutes the engine's `Access` object. The production controller is separately exercised for missing Army, own/neutral/allied/newcomer towns and offline-defense guards; the world test does not certify a real Army/SiegeWar combat session.

Compile with Java 25 and a classpath file containing the real Folia API/runtime libraries and Towny JAR:

```sh
python scripts/tnt-runtime-probe/build.py --server /absolute/fresh-fixture --java-home /absolute/jdk25 --classpath-file /absolute/dependencies.classpath
python scripts/tnt-runtime-probe/run.py --server /absolute/fresh-fixture --java-home /absolute/jdk25
```

The runner performs initial and restart phases sequentially, collects console output after each process exits, records exact JAR hashes, and fails on missing proof, plugin lifecycle errors or watchdog output. The Java fixture checks real TNT contact/distant damage across a chunk boundary, one hit per door, physical destruction, chest/bedrock protection, duplicate blasts, final event cancellation, denied access, stale block replacement, module pause, HP/audit persistence and the real construction TextDisplay transform. An explicit latch blocks only the background storage worker to exercise replacement while damage is in flight.
