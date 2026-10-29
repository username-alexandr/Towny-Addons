# Native citizenship admissions regression

Use only a stopped, disposable Folia 26.2 build 7 template bound to `127.0.0.1` with `ALLOW_DISPOSABLE_RAID_PROBE`. Java 25 and Towny 0.103.2.0 are required. Compile Citizens and Control with `scripts/build_pinned_folia.py` first; preparation copies only the resulting validated JARs. Never put the probe on a live server.

```
python scripts/admissions-runtime-probe/prepare.py --template /abs/template --server /abs/new-fixture --towny /abs/Towny-0.103.2.0.jar
python scripts/admissions-runtime-probe/build.py --server-dir /abs/new-fixture --java-home /abs/jdk25
python scripts/admissions-runtime-probe/run.py --server /abs/new-fixture --java-home /abs/jdk25
```

The fixture imports an explicit schema-1 temporary status, seeds Towny residents and a town, and uses real Citizens repository, services, commands, native inventories and asynchronous persistence. Player, InventoryView and EntityScheduler are explicit offline proxies; scheduler operations are routed to the genuine owning Folia region. No network/graphical client, bank transaction or live-player acceptance is claimed.

The first run checks voluntary admission, mayor authorization, current sponsor membership, frozen terms, unavailable-action explanations, a separate confirmation, and a physical lock on the **owning Citizens classloader's actual file writer**. The region remains active while status and decision remain unconfirmed. The second run checks native restart, neutral pause/resume of temporary expiry, preserved historical dates, single status history, migration and the actual administrative journal. Both runs stop the server cleanly, preserve exact JAR hashes and reject stale pass markers or addon lifecycle errors. Failed fixtures must be retained or archived; use a fresh directory for the next attempt.
