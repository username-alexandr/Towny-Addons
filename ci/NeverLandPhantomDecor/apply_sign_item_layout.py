from pathlib import Path
import sys

root = Path(sys.argv[1])

for rel in [
    "build.gradle.kts",
    "src/main/resources/plugin.yml",
    "src/main/java/ru/neverland/phantomdecor/NeverLandPhantomDecorPlugin.java",
    "src/main/java/ru/neverland/phantomdecor/PhantomDecorCommand.java",
]:
    p = root / rel
    s = p.read_text(encoding="utf-8")
    s = s.replace("0.2.1-test", "0.2.2-test")
    p.write_text(s, encoding="utf-8")

p = root / "src/main/resources/config.yml"
s = p.read_text(encoding="utf-8")
old = """sign-items:
  surface-offset: 0.515
  y-offset: 0.55
  display-transform: FIXED
"""
new = """sign-items:
  # NONE ignores oversized item-model display.fixed transforms from resource packs
  # and lets the plugin keep vanilla and ItemsAdder items within sign bounds.
  display-transform: NONE
  scale: 0.42
  yaw-offset-degrees: 180.0

  # Standing signs are centered inside their block.
  standing-surface-offset: 0.045
  standing-y-offset: 0.56

  # Wall signs sit near the outer face of their block.
  wall-surface-offset: 0.47
  wall-y-offset: 0.50
"""
assert old in s, "sign-items config block not found"
s = s.replace(old, new)
p.write_text(s, encoding="utf-8")

p = root / "src/main/java/ru/neverland/phantomdecor/PhantomDecorListener.java"
s = p.read_text(encoding="utf-8")

old = '        float yaw = yawFor(data) + (side == Side.BACK ? 180.0f : 0.0f);'
new = '''        float yawOffset = (float) plugin.getConfig().getDouble("sign-items.yaw-offset-degrees", 180.0);
        float yaw = yawFor(data) + yawOffset + (side == Side.BACK ? 180.0f : 0.0f);'''
assert old in s, "yaw line not found"
s = s.replace(old, new)

old = '''            } catch (IllegalArgumentException ignored) {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            }

            PersistentDataContainer itemPdc = entity.getPersistentDataContainer();'''
new = '''            } catch (IllegalArgumentException ignored) {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            }

            float scale = (float) plugin.getConfig().getDouble("sign-items.scale", 0.42);
            scale = Math.max(0.05f, Math.min(1.0f, scale));
            var transformation = entity.getTransformation();
            transformation.getScale().set(scale, scale, scale);
            entity.setTransformation(transformation);

            PersistentDataContainer itemPdc = entity.getPersistentDataContainer();'''
assert old in s, "display transform block not found"
s = s.replace(old, new)

old = '''    private Location signItemLocation(Location blockLoc, BlockData data, Side side) {
        double surface = plugin.getConfig().getDouble("sign-items.surface-offset", 0.515);
        double y = plugin.getConfig().getDouble("sign-items.y-offset", 0.55);
        Vector forward = facingVector(data);
        if (side == Side.BACK) forward.multiply(-1.0);
        return blockLoc.clone().add(0.5, y, 0.5).add(forward.multiply(surface));
    }
'''
new = '''    private Location signItemLocation(Location blockLoc, BlockData data, Side side) {
        boolean wallSign = data instanceof Directional;

        double surface = plugin.getConfig().getDouble(
                wallSign ? "sign-items.wall-surface-offset" : "sign-items.standing-surface-offset",
                wallSign ? 0.47 : 0.045
        );
        double y = plugin.getConfig().getDouble(
                wallSign ? "sign-items.wall-y-offset" : "sign-items.standing-y-offset",
                wallSign ? 0.50 : 0.56
        );

        Vector forward = facingVector(data);
        if (side == Side.BACK) forward.multiply(-1.0);

        return blockLoc.clone()
                .add(0.5, y, 0.5)
                .add(forward.multiply(surface));
    }
'''
assert old in s, "signItemLocation method not found"
s = s.replace(old, new)

p.write_text(s, encoding="utf-8")
