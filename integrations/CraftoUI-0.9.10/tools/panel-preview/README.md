# Remaining tabs: production renderer and workflow probe

Current 0.8.16 run: 1633 assertions and 343 PNGs, including finance and wheel checks. Shared controls and navigation use the compact minimalist style. Native/configuration MenuScreen and AddonScreen are tested separately by `tools/minimal-preview`.

```sh
python3 tools/panel-preview/run.py \
  --java-home /path/to/jdk25 \
  --minecraft /path/to/minecraft-26.2.jar \
  --gson /path/to/gson-2.14.0.jar \
  --output build/panel-preview
```

Compiles the real PanelScreen, PersonalScreen, CommandScreen, SettingsScreen, HudPositionScreen, CivicScreen, OrderCardButton and shared civic controls. Platform, input dispatch, network, client settings persistence and HUD geometry are explicit doubles in adapters.py; they are generated only under the output directory and never included in the production JAR. Original bitmap font and item textures are read from the separately supplied Minecraft JAR. No Mojang assets or dependencies are bundled with these tools.

Reuses the existing lifecycle and civic probes. Current output: 19 lifecycle assertions, 95 civic assertions, 161 additional assertions; 219 PNGs. New checks cover all stages of the command form, not real addon execution. Input tests operate on the production responder/state code via explicit EditBox/input doubles. Real keyboard/IME/GL rendering, resource packs, networking and GPU glint require a game playtest; item glint is checked as a data flag. HUD positioning buttons use the real screen with a geometry double.

The real client is separately built against genuine Minecraft and Fabric APIs. Tests demonstrate renderer/state behaviour under the stated doubles, not a launched Minecraft session.


0.8.9 adds WheelProbe: 110 checks for bounded dialogs, translucent shade, real multi-page catalogue replies, wheel direction/limits, request deduplication, local scrolling and local pagination. The full runner now emits 223 PNGs (219 existing scenarios and 4 wheel-regression views). These tests use explicit platform/network doubles; game-device and GL behaviour are not claimed.


0.8.10 adds RecentMenusProbe: 400 checks and 72 actual-screen renders for the compact city dashboard and recent civic pages. The full runner emits 295 PNGs, including the previous scenarios. The dashboard fixture supplies real-shaped metrics/shortcuts/progress rows and tests server pagination with explicit transport doubles.
