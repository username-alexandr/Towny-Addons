0.8.20: `--addons-update` проверяет девять новых окон Suite 0.51.0: 2726 проверок и 72 PNG; нормальный режим — 1200 проверок, `--density` — 914. Данные примеров и платформа явно синтетические.

# Native and configuration menu regression

0.8.17 adds `--density`: 914 assertions and 90 PNGs reproducing legacy oversized grids, fixed order controls, styled lore, all configured menus, local pagination, footer actions and header alignment. PyYAML is used only to export the actual YAML help screen into the renderer fixture. The normal runner now performs 1196 checks; platform, inventory and transport remain explicit doubles.

```sh
python3 tools/minimal-preview/run.py \
  --java-home /path/to/jdk25 \
  --minecraft /path/to/minecraft-26.2.jar \
  --gson /path/to/gson-2.14.0.jar \
  --output build/native-minimal-preview
```

Compiles the production MenuScreen and AddonScreen, their shared layout/schema code and shared controls. The parent panel-preview tool only prepares explicit platform/input/inventory/transport doubles; these do not enter the release JAR. Fonts and available item textures are read from the separately supplied original Minecraft JAR. Entity/blueprint preview, player/inventory, settings and networking are synthetic test implementations.

The 0.8.16 probe performs 1132 checks and writes 128 PNGs. Both themes are tested at logical GUI sizes 829×563, 520×340, 320×240 and 320×180. Scenarios cover the building catalogue, market goods, order rarity, nine configuration menus and confirmation with long conditions. Checks include original raw slot/right button/Shift, grouping without merging goods, exact server Back tokens, stale dropdown clicks, a complete visible action at small sizes, scrollable conditions and confirmation placement.

These images show production drawing code with example data through an AWT adapter, not a game session. Native GPU item models, GL rendering, resource packs, mouse devices, IME and a real server connection require a separate game playtest. Full-loader bootstrap and the release build use actual Minecraft/Fabric dependencies independently.
