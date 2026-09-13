# Native API audit

Use only an isolated, disposable server on loopback. The fixture creates an ApiAudit town, seeds 16 iron ingots, changes its market stock, temporarily replaces a service and disables Population before shutdown. It must never be installed on a production server.

Prepare all 31 addon JARs at the versions in `versions.yml`, Towny 0.103.2.0, Vault/economy, Paper/Purpur 26.2 and Java 25. Preserve a snapshot of plugin data before the first run. Create `ALLOW_DISPOSABLE_RELIABILITY_PROBE` in that server directory and set `server-ip=127.0.0.1`.

```sh
python scripts/api-runtime-probe/build.py --server-dir /absolute/test-server --java-home /absolute/jdk-25
python scripts/api-runtime-probe/run.py --server-dir /absolute/test-server --java-home /absolute/jdk-25 --phase first
python scripts/api-runtime-probe/run.py --server-dir /absolute/test-server --java-home /absolute/jdk-25 --phase restart
```

Build requires Python/PyYAML and the installed server libraries; use `--extra-jar` for a compile-only annotation dependency if needed. It generates contract and consumer inventories from the exact source tree. The runner rejects missing, old or duplicate addon versions and records SHA-256 of all tested JARs. Both runs stop the server automatically.

A failed run must be investigated, archived and reset from the explicit disposable snapshot. Do not erase failure evidence and rerun against the partially changed fixture. Success files and individual assertions are written under `plugins/NeverLandApiRuntimeProbe`. Remove the probe JAR after the audit.

Coverage: 33 real service registrations and read calls; metadata; every statically resolved capability requirement; dynamic Companies authorization; MarketGateway availability and actual reserve/refund/close with restart; missing, disabled, unregistered, replaced, incompatible and throwing providers; five rejected worker-thread mutation paths. Other gameplay and load scenarios remain the responsibility of their dedicated probes.
