# Disposable native safety probe

This fixture creates a Towny town and exercises administrator commands, permissions, paused menus, raid identity, a real expired event deadline, durable module shutdown and two subsequent JVM starts. It must never be installed on a player server.

Requirements: loopback `server-ip=127.0.0.1`, `ALLOW_DISPOSABLE_RELIABILITY_PROBE` marker, Java 25, Purpur/Paper 26.2, Towny and dependencies, and exactly the suite JAR names/versions listed in `versions.yml`. Use an ordinary local filesystem for the disposable server, outside a synchronized source workspace. External plugin update checks may be unavailable in a network-limited environment.

```bash
python scripts/safety-runtime-probe/build.py --server-dir /tmp/safety-server --java-home /path/to/jdk25
python scripts/safety-runtime-probe/run.py --server-dir /tmp/safety-server --java-home /path/to/jdk25 --phase first
python scripts/safety-runtime-probe/run.py --server-dir /tmp/safety-server --java-home /path/to/jdk25 --phase disabled
python scripts/safety-runtime-probe/run.py --server-dir /tmp/safety-server --java-home /path/to/jdk25 --phase resumed
```

The builder accepts `--extra-jar` for compile-only annotations. Each successful phase stops the server. Keep the same data directory between phases. The runner rejects previous success/failure files and records addon JAR SHA-256 values. Proofs live in `plugins/NeverLandSafetyRuntimeProbe`; native logs are copied to `safety-<phase>-server.log`. Remove the probe JAR after the final phase. To repeat, create another disposable server from a clean snapshot; do not erase proof markers in an existing test town.

Candidate 0.34.0: 93 + 33 + 47 assertions on Purpur 26.2 build 2632, Towny 0.103.2.0, Java 25, SiegeWar 3.6.2. These checks establish controlled lifecycle behavior; they are not client visual QA or a multiplayer load test.
