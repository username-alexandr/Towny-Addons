# Isolated Folia deletion probe

Never run this fixture against production files. It creates and deletes synthetic Towny towns and changes blocks in a disposable world. Do not distribute `DeletionProbe.jar` with release plugins.

Prepare a disposable Folia 26.2 directory, accept the server EULA for this test instance, bind to loopback, install Towny 0.103.2.0, Control 0.4.7 and Builds 0.8.28, disable Towny economy and ruins for the fixture, and disable metrics. Bootstrap once to obtain native libraries. Set `JAVA` and `JAVA_HOME` to Java 25.

```sh
python3 scripts/town-deletion-runtime-probe/build.py /absolute/disposable/server
python3 scripts/town-deletion-runtime-probe/run.py /absolute/disposable/server
```

The runner owns its Java subprocess, supplies console stdin, stops it cleanly, then starts it again to verify recovery from a durable checkpoint. Inspect `deletion-result-1.txt`, `deletion-result-2.txt` and both console logs. Repeat from a fresh directory; retained fixture towns are intentionally not reset automatically.

The test covers command interception and confirmation, explicit permissions without OP, actor binding and replay, exact registered-block removal, preservation of ordinary bases/neighbor towns/containers, first-claim snapshots, complete rollback, truncated and invalid snapshots, a five-column vertical trench, and replay after a real restart. It uses native world/chunk/block implementations on Folia region schedulers. Command senders are synthetic and no human client joins; interactive GUI rendering and the ruins delay are not tested by this fixture.
