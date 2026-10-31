# Disposable navigation regression probe

Run only on a throwaway Folia server bound to 127.0.0.1, with the marker
`ALLOW_DISPOSABLE_NAVIGATION_TEST` and JVM flag `-Dcraftoui.navigationProbe=true`.
Requires genuine Towny, Builds (Contracts dependency), Contracts and CraftoUI. It automatically shuts the test
server down and writes `plugins/NavigationProbe/checks.txt` plus passed/failed.

The Player, InventoryView and EntityScheduler are explicit test doubles. Their
callbacks run on Folia's global scheduler; this does not test regional ownership
of a live connected player. Contracts command execution, board creation, Bukkit
inventory events, CraftoUI packet decoding, sessions, permissions, directory,
native presentation hints and Back handling are real.

Tests home/economy/search paths to `/orders`, no civic interception, permission
denial, bad session and replay tokens, Back without command replay, and no payload
for an actor without the mod handshake. No payment or production data is used.
This is not a visual Fabric test. Never distribute the compiled probe as a
production plugin.
