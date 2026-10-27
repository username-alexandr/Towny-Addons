# Camp and raid loot native probe

Run only on a fresh, disposable Folia 26.2 loopback server. This destructive fixture creates towns, claims, camps, buildings, accounts, items and test records, then shuts down the server twice. The `ALLOW_DISPOSABLE_RAID_PROBE` marker and `server-ip=127.0.0.1` are mandatory.

Install real Towny 0.103.2.0, Builds 0.8.41, SiegesPlus 0.3.0 and Control 0.4.17. Use Java 25 and the real Folia dependencies classpath.

```sh
python scripts/camp-loot-runtime-probe/build.py --server /absolute/fresh-fixture --java-home /absolute/jdk25 --classpath-file /absolute/dependencies.classpath
python scripts/tnt-runtime-probe/run.py --server /absolute/fresh-fixture --java-home /absolute/jdk25
```

The base TNT tests run first. The extension checks real camp block markers and their loss/restart persistence, rejects a missing army roster, inspects the real administrator inventories, captures the registered primary lectern, protects another lectern, and runs production `LootBank` through genuine Towny `Account` observers. A deterministic external economy adapter and an explicit readiness override substitute for Vault; no live economy-provider integration is claimed. Camp rows and combat authorization are explicitly seeded; positive player/Army muster and SiegeWar integration require a separate playtest.

The destination warehouse is deliberately full. Money is transferred once, resource cargo remains durably reserved, and a full server restart occurs. After space is freed, the exact cargo arrives once and the original lectern book returns to its owner. Checks also verify the captured lectern is not automatically rebuilt, the total money supply remains unchanged, and the administrator journal contains separate core, money and item records. Console logs, assertion files and exact JAR hashes are written by the runner.
