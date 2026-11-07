# CraftoUI 0.9.22 (Folia) + 0.9.31 (Fabric source)

This source snapshot is derived from the latest provided 0.9.31 CraftoUI source package. The Fabric client source is unchanged. The server map opens the NeverLandTownyStick 0.2.6 expansion plan from inside the player's city and no longer routes remote free plots through Towny's local `/t claim` confirmation.

`claim-map.patch` is the readable diff against the 0.9.31 source package; the tarball is a buildable source snapshot for CI. Run `tar -xJf craftoui-source-0.9.22.tar.xz` and `gradle -PdirectMinecraft=/tmp/no-minecraft :server:test :server:jar :api:jar` from its `craftoui-source` directory with Java 25.

The action is revalidated before dispatch. Stick subsequently checks the mayor or deputy's Council permission, Towny rights, adjacency, town balance, biomes, and the final price. The ordinary direct Towny claim command remains available outside CraftoUI.
