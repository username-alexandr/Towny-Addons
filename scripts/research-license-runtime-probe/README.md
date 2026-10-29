# Research license native regression

Run only with the coordinator's exclusive Folia JVM window. Preparation refuses an existing destination and requires `ALLOW_DISPOSABLE_RAID_PROBE` on the stopped loopback template.

```bash
python scripts/research-license-runtime-probe/prepare.py --template ../folia-bootstrap --server ../folia-research-license-2 --towny ../toolchain/Towny-0.103.2.0.jar --control-jar modules/NeverLandTownyControl/build/libs/NeverLandTownyControl-0.6.2.jar
python scripts/research-license-runtime-probe/build.py --server-dir ../folia-research-license-2 --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64
python scripts/research-license-runtime-probe/run.py --server ../folia-research-license-2 --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64
```

Four foreground, sequential JVMs cover:

1. Real Research/Treasury pipeline, held acceptance writer, revoked permission between submission and ACK, exact existing-money transfer, no permanent learning/resale, neutral pause, disabled technology, finite term, PAYING config pause, backed refund, ambiguous seller response, actual outer-Suite shutdown inside bank callback.
2. No replay after restart, explicit exact paid reconciliation, blocked prepaid-clock writer, no bonus before clock ACK, nonblocking shutdown and preservation of unused prepaid seconds.
3. Another complete restart with exact remaining time, eventual expiry, three payment records and one refund record across transitions, persisted actor/town audit snapshots, byte-identical legacy research.
4. Deliberately corrupt optional `licenses.yml` and invalid license configuration: old Research stays enabled, learned levels and paused study remain, corrupt bytes are preserved at shutdown, bank remains untouched.

The actual artifacts and their hashes are recorded per phase. EconomyAdapter, player identities, and proxy entity scheduling are explicit fixture substitutions. The proxy's entity scheduler delegates to real Folia global tasks; this does not claim network-player regional interaction testing. Public offer terms are explicitly shortened to 60 seconds in the disposable journal, then the real service processes them. No live Vault backend, client menu clicks or production data are used.

The separate cold artifact-boundary check is reproducible without a server:

```bash
python scripts/research-license-runtime-probe/cold_boundaries.py --java-home ../toolchain/zulu25.38.107-ca-jdk25.0.5-linux_x64 --libraries ../folia-bootstrap/libraries --research-jar modules/NeverLandTownyResearch/build/libs/NeverLandTownyResearch-0.2.0.jar
```

Validated artifact: `validated-0.2.0.json`; native evidence: `docs/validation/city-systems-052/research-licenses/`. The accepted fresh tmpfs run passed 21 + 10 + 6 + 5 assertions, four JVM exits 0. The earlier overlay run stopped on a probe-only mistake (raw TSV columns instead of `AuditJournal.decode`); production code was unchanged for the successful run.
