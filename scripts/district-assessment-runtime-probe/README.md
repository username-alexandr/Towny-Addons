# Native district-assessment regression

Only a fresh, stopped, explicitly marked loopback Folia 26.2 build 7 template. Compile validated Builds, Districts, Citizens and Control JARs before preparation. Requires Java 25 / Towny 0.103.2.0.

```
python scripts/district-assessment-runtime-probe/prepare.py --template /abs/template --server /abs/new-fixture --towny /abs/Towny-0.103.2.0.jar
python scripts/district-assessment-runtime-probe/build.py --server-dir /abs/new-fixture --java-home /abs/jdk25
python scripts/district-assessment-runtime-probe/run.py --server /abs/new-fixture --java-home /abs/jdk25
```

Uses actual Towny claims/home metadata, Builds footprint API, Districts service, command and native inventory cards, detached graph and persisted read-only file comparison. Completed V-level industry and I-level workshop sites are explicitly seeded metadata; no purchased construction or world-block scan is claimed. Inherits only the explicit offline Player/InventoryView/EntityScheduler harness from AdmissionsProbe. Changing the genuine home block proves input invalidation and disconnected-component handling. Two clean boots check restored data and derived report, unchanged bonus, neutral disable/settings failure and lifecycle errors. No GUI client, bank operation or production load claim.
