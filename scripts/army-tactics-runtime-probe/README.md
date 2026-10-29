# Native Army tactics probe

Opt-in disposable Folia fixture. Requires `ALLOW_DISPOSABLE_RAID_PROBE` and loopback binding. Never point at a live server. Run only one Folia JVM at a time.

Prepare with `prepare.py --template <stopped marked server> --server <new path> --towny <Towny JAR> --candidate-root <source root containing built Army and Control>`. Other dependency JARs come from this repository's `versions.yml`.

Compile using `build.py --server-dir <path> --java-home <JDK25> --annotations <annotations.jar>`, then `run.py --server <path> --java-home <JDK25>`.

Three sequential JVMs exercise first use, restart recovery and intentional corruption of the optional tactics file. The runner records exact installed JAR hashes and rejects lifecycle errors and missing/newly stale assertions. The corruption phase preserves a copy of the intact optional file in validation output. Do not use this fixture as a gameplay server.

The probe uses actual Towny memberships, Builds levels and native item inventories/PDC/player.dat. Network presence, inventory views, location and entity scheduling are controlled proxies around offline ServerPlayer handles. Accepted roster/base and pre/post-crash states are explicit fixtures. No live client, combat or economic-provider integration is claimed.
