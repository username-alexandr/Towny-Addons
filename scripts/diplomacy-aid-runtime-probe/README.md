# Native mutual-aid probe

Use a fresh explicitly marked disposable loopback-only Folia 26.2 fixture, never a player server. Three complete JVM boots run serially: first actions, restart with a staged OPEN crash snapshot, optional journal corruption.

```bash
python scripts/diplomacy-aid-runtime-probe/prepare.py --template /abs/stopped-template --server /abs/fresh-fixture --towny /abs/Towny.jar
python scripts/diplomacy-aid-runtime-probe/build.py --server-dir /abs/fresh-fixture --java-home /abs/jdk25 --annotations /abs/annotations.jar
python scripts/diplomacy-aid-runtime-probe/run.py --server /abs/fresh-fixture --java-home /abs/jdk25
```

Port 25679. No other native JVM should be running simultaneously. Uses actual Towny territories, production Diplomacy services, real Folia region ownership and registered Bukkit/Towny damage listeners. Constructed native offline CraftPlayers receive explicitly submitted damage events; no network client or organic combat is claimed. Menu commands use named offline Player/InventoryView substitutes. The real mutual-aid file lock is delayed to verify mayor revalidation and visibility after ACK. No bank or item transfer exists in this feature. Optional corruption is expected to log one explicit aid-only load failure; other addons and treaty protections remain healthy.
