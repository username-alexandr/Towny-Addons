# Native Population persistence probe

Requires stopped loopback-only Folia template with ALLOW_DISPOSABLE_RAID_PROBE. Uses real Towny/Builds/Districts/Population and genuine filesystem failure/delay. No bank or client claim. Never run against production.

Run prepare.py --template PATH --server NEW_PATH --towny JAR, then build.py --server-dir NEW_PATH --java-home JDK25, then run.py --server NEW_PATH --java-home JDK25. Each phase stops automatically. Source and JAR smoke validation must finish before copying artifacts into the fixture.
