# Native building procurement probe

Opt-in disposable loopback Folia fixture only. Never install this probe on a real server.

Prepare with `prepare.py --template <stopped-marked-fixture> --server <new-directory> --towny <jar> --artifacts-from <directory-containing-one-exact-jar-per-module>`; compile with `build.py --server-dir ... --java-home ...`. Run `run.py --server ... --java-home ... --phase first`, then `broken`, then `restart`, sequentially and fully foreground. Use only an exclusive native window coordinated with the parent task.

The fixture uses genuine Folia, Towny, Contracts, Builds, Treasury and Control APIs and persistence. Player/EntityScheduler are explicit offline substitutes; the Towny EconomyAdapter is deterministic and saved between boots. This does not test a real Vault economy or network inventory client. A blocked optional Procurement database is deliberately injected only between orderly JVM exits, retaining original database bytes separately. SHA256 and WAL absence are checked before restoration. Never repair a failed run by deleting live sidecars or changing artifacts.

Covers actual missing resources after fund/warehouse subtraction; municipal funding before publication; idempotent publication; a changed shortage after agreed payment; no fund/warehouse consumption on procurement; exact prepublication refund once; stale debit backing invalidation; former mayor rejection; explicit new mayor resume across full restart; unreadable optional database isolation; new-work pause retaining old contracts and receipts.

Native results are pending until the Treasury async gateway artifact is verified. Compilation or cold tests do not establish Folia readiness.
