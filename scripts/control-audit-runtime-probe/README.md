# Native Control audit probe

Requires validated production JARs and an explicitly marked stopped disposable Folia template. The isolated fixture installs Towny, Control and Citizens (the offline actor framework dependency), binds only `127.0.0.1:25675`, and disables external telemetry.

```text
python scripts/control-audit-runtime-probe/prepare.py --template TEMPLATE --server FRESH_SERVER --towny TOWNY_JAR
python scripts/control-audit-runtime-probe/build.py --server-dir FRESH_SERVER --java-home JDK25
python scripts/control-audit-runtime-probe/run.py --server FRESH_SERVER --java-home JDK25 --phase first
python scripts/control-audit-runtime-probe/run.py --server FRESH_SERVER --java-home JDK25 --phase restart
```

Run these JVMs sequentially, without another native fixture occupying the shared test window. Each phase exits its server before returning; the restart phase verifies identical JAR hashes.

The probe uses actual Control menus, event handlers, command executors, derived SQLite cache, source checksums, AuditTrail/BankAuditQueue and Folia shutdown. It deliberately corrupts/restores **only its disposable fixture** TSV and SQLite cache to verify fail-closed queries and reindex. Sources are checked byte-for-byte before/after normal reads and reindex.

Administrative evidence is explicitly seeded, without actual payments. The Player, InventoryView, permissions and EntityScheduler are offline substitutes routed to a real Folia region. Public party names are captured before an actual Towny rename while the real audit journal monitor is blocked. The shutdown test holds an accepted bank audit receipt until Control becomes disabled, confirms SuiteState is available while the receipt remains pending, then releases the writer. The second JVM verifies exact IDs/timestamps and no replay. No network rendering, real bank transfer, or LuckPerms integration is claimed.

A bounded retry in the shutdown assertion allows the short normal onDisable guard to finish; it does not release the audit writer before testing that the shared guard is available. A blocking wait under SuiteState would still fail the test.
