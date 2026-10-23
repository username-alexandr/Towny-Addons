# Mob farm native probe

Run only on a disposable Folia 26.2 server with Towny 0.103.2.0, Builds 0.8.29 and the new Districts JAR. This creates test residents, claims and towns, changes world mob settings and spawns/removes zombies. Do not use production worlds or databases.

Bootstrap the local server first, bind `server-ip=127.0.0.1`, disable metrics, allow monster spawning (`spawn-monsters=true`), choose a non-peaceful difficulty and an unused port, and create `ALLOW_DISPOSABLE_MOB_FARM_PROBE` in its root. Accept the server EULA only if authorized. Use a fresh Towny database for the first phase. Keep failed evidence; retries require an explicitly restored disposable snapshot.

```sh
python scripts/mob-farm-runtime-probe/build.py --server-dir /path/to/disposable --java-home /path/to/jdk25
python scripts/mob-farm-runtime-probe/run.py --server-dir /path/to/disposable --java-home /path/to/jdk25 --phase 1
python scripts/mob-farm-runtime-probe/run.py --server-dir /path/to/disposable --java-home /path/to/jdk25 --phase 2
```

Both runs stop the server and retain installed-JAR hashes, console log and assertions. The second run reads persisted Towny flags. The fixture tickets its chunks and waits for Folia's entity registration before testing living entities; removal is verified with isValid, not the historical isInWorld flag. The player identity is a strict proxy; Towny objects, API, events, entities, inventories and persistence are real. No visual Minecraft client or AFK yield/load measurement is involved.
