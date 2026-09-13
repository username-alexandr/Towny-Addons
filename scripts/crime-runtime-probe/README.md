# Disposable native Crime probe

Requires a stopped prepared Paper/Purpur 26.2 server, Java 25, the exact 32 JARs from versions.yml, Towny, Vault/Essentials and the dependencies already installed. The server must bind 127.0.0.1 and contain ALLOW_DISPOSABLE_RELIABILITY_PROBE. Creates synthetic towns and changes their virtual resources and shop ledger; never run on player data.

Save a full disposable snapshot before `first`. Install the probe with `build.py --server-dir ... --java-home ...` (optional `--extra-jar ...` for annotations). Run `run.py --server-dir ... --java-home ... --phase first`, then the same command with `--phase restart`. Stage the exact JAR set before both phases. Repeating first requires explicit snapshot restoration; the runner refuses stale proof and duplicate/version-mismatched JARs.

The fixture verifies every advertised public service, actual Population/Builds/Jobs bridges, unavailable/paused inputs, crime income and temporary extortion, protected virtual reserves, actual theft, lost resource replies, restart reconciliation, actual net shop bank credits and persisted gross/net amounts, permissions, immutable snapshots and thread restrictions. Provider proxies inject specific failures; all mutations use real repositories and the disposable Towny bank. It does not emulate client resource packs or production load.

Archive the first/restart check lists, JAR hashes and logs, then remove NeverLandCrimeRuntimeProbe.jar. Probe classes are outside addon source trees and excluded from plugin JARs.
