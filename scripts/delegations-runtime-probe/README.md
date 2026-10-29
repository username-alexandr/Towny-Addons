# Native temporary-rights regression

Use the same loopback-only, explicitly disposable Folia 26.2 build 7 / Java 25 / Towny 0.103.2.0 template as `../admissions-runtime-probe`. Build Council, Control, Citizens and Governance with `build_pinned_folia.py` before preparing a **fresh** server directory.

```
python scripts/delegations-runtime-probe/prepare.py --template /abs/template --server /abs/new-fixture --towny /abs/Towny-0.103.2.0.jar
python scripts/delegations-runtime-probe/build.py --server-dir /abs/new-fixture --java-home /abs/jdk25
python scripts/delegations-runtime-probe/run.py --server /abs/new-fixture --java-home /abs/jdk25
```

Reuses the explicit offline Player, InventoryView and owning-region EntityScheduler harness from AdmissionsProbe, overriding all gameplay scenarios. Uses actual Towny/Council/Citizens/Governance services, native inventory items, action bridge, acknowledged files, deliberately blocked owning-classloader writer and full restart. Expired grant is an explicit persisted fixture. It does not claim network-client testing, bank debits or monetary caps. See `docs/COUNCIL-TEMPORARY-RIGHTS.md` for coverage and current limits.
