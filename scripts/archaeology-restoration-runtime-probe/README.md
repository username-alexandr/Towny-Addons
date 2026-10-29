# Archaeology restoration native fixture

Run only in an explicitly marked, disposable loopback fixture, with the shared native JVM slot reserved. `prepare.py` refuses to overwrite existing directories. Compile using `build.py`, then `run.py` performs first/restart/corrupt boots, rejects missing or stale proof markers and lifecycle errors, and records exact JAR hashes.

Validated fixture: `folia-archaeology-restoration-2`, Folia26.2 build7 / Towny0.103.2.0 / Control0.6.1 / Archaeology0.2.0. All boots exit0; 22+10+5 assertions. The first discarded fixture contained only a probe mistake: writing to read-only `sites()` instead of calling `addSite()`.

This exercises actual native items, HMAC, brush event, region callbacks, menus and file writers. Player, inventory, entity-scheduler callback and minimal UUID/marker NBT save are explicitly substituted; no live-client or full vanilla inventory persistence claim is made. Missing recipe parts are seeded with fixture-only source receipts. Never run this probe on a production server.

Proof: `docs/validation/city-systems-052/archaeology-restoration/`. Environmental DNS/auth-key errors and container OS metrics warnings are not addon faults. Corrupt phase deliberately logs one optional-feature warning; other addon modules remain enabled.
