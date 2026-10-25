# Placement runtime verification

Run only on a disposable, loopback-only Folia 26.2 server with Towny 0.103.2.0 the newly built NeverLandTownyBuilds and CraftoUI 0.8.0. The plugin refuses to enable without `ALLOW_DISPOSABLE_PLACEMENT_PROBE` in the server directory and `server-ip=127.0.0.1`. Do not install the probe on a player server. It creates towns, changes blocks and stops the server.

Compile with `python3 build.py --server-dir /absolute/test-server --java-home /absolute/jdk25`. Start the same server twice with open stdin and a 180 second watchdog. Successful runs write `first-passed.txt` and `restart-passed.txt` under the probe data directory. `checks.txt` records assertions; `forge-0.png` through `forge-3.png` are generated thumbnails.

The test uses real Folia regions, BlockDisplay entities, inventories, Towny records and persisted Builds data. Player interfaces are simulated; their entity scheduler calls run on the chosen owning region. The preview timer is stopped and rendering is driven directly. It does not emulate client rendering or a populated server.

Checks: complete preview including roof without world modification; display visibility/persistence flags and cleanup; all bundled plans fit display budgets; resource-to-token issuance, duplicate and lost tokens, forged ID and town transfer; permission and height checks; failed confirmation with unavailable economy; successful free legacy placement through the confirmation button and duplicate click; restart persistence; injected write failure with item restoration and write lock; reject extra funding while a construction is active; real async thumbnail API and four bounded CraftoUI packets, capability and stale-menu guards.

A paid placement with a real third-party economy and visual inspection inside a Minecraft client remain deployment playtest checks. The existing receipt/refund transaction is retained.
