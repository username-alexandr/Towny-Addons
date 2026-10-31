# AddonScreen building-menu regression

Run `python3 tools/build-menu-preview/run.py --java-home /path/to/jdk25 --minecraft /path/to/minecraft-26.2.jar --gson /path/to/gson-2.14.0.jar --output validation/build-menu-0.8.14/renderer`.

Compiles the production AddonScreen, AddonLayout, Protocol, MenuViewMemory and OrderRarityStyle. Explicit platform, inventory, network, input, item and preview-texture doubles are generated under the output directory only. Uses the existing AWT font/drawing adapter and bitmap font from the separately supplied Minecraft JAR. Original items and preview image are represented by test rectangles; this tests visibility and layout, not Minecraft GL or actual image rendering. Neither game assets nor test doubles enter the release JAR.

60 behavioural assertions and 18 PNGs cover two themes at 900×600, 520×340 and 320×180: exact hall hover trigger, hiding on other cards/exit, wheel rotation, no Actions/F7 in Builds, explicit donation caption, pagination at small size, presentation-only refresh and rejection of stale view metadata. Not a launched game/client-network test.
