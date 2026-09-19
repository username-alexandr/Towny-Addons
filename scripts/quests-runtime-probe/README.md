# Disposable Quests integration fixture

Never install this probe on a player server. It requires `ALLOW_DISPOSABLE_RELIABILITY_PROBE`, a loopback `server-ip=127.0.0.1`, `-Dneverland.runtimeProbe=true`, a prepared Purpur 26.2 server, Java 25, Towny and the complete addon suite with dependencies.

Before the first run, copy Quests' default `quests.yml` into the disposable plugin folder and set only `projects.sanitation.stages.water.hold-seconds` to `2`. Set `simulation.interval-seconds` to `1` in its config. Leave the follow-up chain's 600-second hold unchanged. Do not reuse an existing town or proof directory.

```bash
python scripts/quests-runtime-probe/build.py --server-dir /tmp/quest-server --java-home /path/to/jdk25
python scripts/quests-runtime-probe/run.py --server-dir /tmp/quest-server --java-home /path/to/jdk25
```

The builder accepts `--extra-jar` for compile-only annotations. The runner records exact plugin SHA-256 values and preserves two JVM pass markers. Both runs stop the server themselves.

The fixture creates real Towny claims and a mayor, opens native Bukkit inventories, and exercises actual Quests and Policies services. Controlled public Builds, Districts and Resources API responses isolate operational buildings, paused/stale water supply and disconnected districts. It verifies permission-aware project start, follow-up locking, online holding, neutral cancellation, one-time completion, real policy cost/benefit, provider disable and durable restoration in another JVM. This verifies the consumer integration; it does not simulate construction, production balancing, client rendering or server load.
