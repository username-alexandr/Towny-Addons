# Native building repair and protected loot probe

Destructive opt-in fixture for a fresh disposable loopback Folia 26.2 server. Require `ALLOW_DISPOSABLE_RAID_PROBE` and `server-ip=127.0.0.1`. Install genuine Towny 0.103.2.0, Builds 0.8.42, SiegesPlus 0.4.0 and Control 0.4.18. Never install the probe on a live world.

```sh
python scripts/repair-runtime-probe/build.py --server /absolute/fresh-fixture --java-home /absolute/jdk25 --classpath-file /absolute/dependencies.classpath
python scripts/repair-runtime-probe/run.py --server /absolute/fresh-fixture --java-home /absolute/jdk25
```

The inherited TNT, camp, escrow and admin-menu checks run first. An unexecuted repair bank intent is committed, independently read through a second SQLite connection, checked again after shutdown, and recovered as review on the second native boot. The registered town-hall geometry is then populated and explicitly seeded with half HP damage. The actual production quote and confirmation checks reject an empty town treasury, show the real repair inventory, withdraw exactly half the current-level price through genuine Towny Account observers, restore aggregate HP and avoid a second charge. A fully destroyed registered cell is separately restored. Operational wall levels reduce both captured shares and the frozen policy is retained in the loot receipt. Native audit and admin inventory categories are inspected.

Combat authorization and the external economy adapter/readiness use explicit doubles. The half-damage aggregate is seeded through the real RaidStore; the inherited capture uses actual TNT events. No logged-in players, live Vault provider or positive Army/SiegeWar session is certified. The runner saves console logs only after each process ends, records exact JAR hashes, and checks the closed SQLite repair intent before restarting.
