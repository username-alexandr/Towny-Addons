# Player experience integration probe

Run only on a disposable Folia 26.2 server bound to `127.0.0.1`, with Towny 0.103.2.0, Builds 0.8.34, Control 0.4.13 and CraftoUI 0.8.4. Create `ALLOW_DISPOSABLE_PLAYER_EXPERIENCE_PROBE` in its root. Never install on a player server: the probe creates residents/town, modifies inventories and funds, writes audit fixtures, and shuts down the server.

Install the previous CraftoUI 0.8.3 addons.json before its first startup to test migration. Compile with `python3 build.py --server-dir /absolute/fixture --java-home /absolute/jdk25`. Run `python3 run.py --server-dir /absolute/fixture` using Java 25 on PATH. Save `logs/latest.log` and `plugins/PlayerExperienceProbe/{checks,passed}.txt`, then run again to verify persistence. Use a fresh fixture after a failed first run, since partially saved Towny data may select the restart path.

The test uses real Bukkit inventories/events, actual Towny and addon services, Folia region scheduling, saved audit records and real encoded CraftoUI packets. Player identity, player inventory access and client connection are simulated. It does not render a Minecraft client or simulate a power cut.
