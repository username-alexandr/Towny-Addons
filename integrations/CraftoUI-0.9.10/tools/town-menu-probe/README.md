# Disposable Towny menu test

This probe creates a test town and residents and automatically shuts down Folia.
Never install it on a production server. It requires the file
`ALLOW_DISPOSABLE_TOWN_UI_TEST` and a server bound to `127.0.0.1`.

Dependencies: genuine Towny, Builds, Council and the CraftoUI server being tested.
Build with `python3 build.py --server-dir /path/to/disposable/server --java-home /path/to/jdk25`.
Start that server, then inspect `plugins/TownMenuProbe/checks.txt` and passed/failed.
Use a fresh fixture or archive old proof files before repeating the test.

Towny resident creation, membership changes, CraftoUI menu generation, packet
decoding, permissions, search and CIVIC handling are real. Player, InventoryView,
inventory and EntityScheduler are explicit doubles. Scheduler callbacks run on
Folia's global scheduler: this is not a live player's regional ownership test,
nor a visual client or network login test. Test JARs must not be shipped in the
production `release/` directory.
