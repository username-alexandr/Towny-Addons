# Personal Market native verification

This opt-in fixture uses Folia 26.2, Towny 0.103.2.0, the actual Market plugin, actual constructed `ServerPlayer` / `CraftPlayer` inventory and persistent data, native `saveData()` / `loadData()`, and the real `.dat` files. It deliberately has no network Minecraft client. Online status, menu view and entity scheduling are proxies; the personal bank is a controlled balance fixture. No real Vault provider payment is claimed.

Prepare a **new**, isolated, loopback-only server from the stopped `folia-bootstrap` template after the Market build and tests pass:

```sh
python scripts/personal-market-runtime-probe/prepare.py --template ../folia-bootstrap --server ../folia-personal-market-N --towny ../toolchain/Towny-0.103.2.0.jar
python scripts/personal-market-runtime-probe/build.py --server-dir ../folia-personal-market-N --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64
python scripts/personal-market-runtime-probe/run.py --server ../folia-personal-market-N --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64
```

The fixture reserves port 25672 and runs first boot plus a full second JVM restart. bStats, faststats and Paper update checks are disabled. It refuses existing proof markers and non-opted-in/non-loopback servers. The runner records exact plugin JAR hashes, independent phase results and logs under `validation/`.

Checks include confirmation commands/menu, ordinary versus custom items, backing and maker-price partial fills, unmatched cancellation, retrieval while new orders are disabled, actual `SpigotConfig.disablePlayerDataSaving` silently skipping a sale save and an item claim save, verified receipt recovery, restart with the original unsaved inventory, unknown bank result without replay, and manual outcome reconciliation once. A seeded historical schema-1 ledger has 1,025 completed one-cent claims: the first live action must archive them, and the restart must verify the checkpoint while preserving the remaining 975 cents. Seeded history is explicit fixture data, not claimed live bank activity.

The probe writes every successful assertion, a failure marker on any exception, and separate first/restart completion markers. Runtime success does not substitute for testing with the production economy provider or a real connected client.
