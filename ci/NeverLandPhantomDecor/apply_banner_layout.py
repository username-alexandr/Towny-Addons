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
    s = s.replace("0.2.3-test", "0.2.4-test")
    p.write_text(s, encoding="utf-8")

p = root / "src/main/resources/config.yml"
s = p.read_text(encoding="utf-8")
old = """banner:
  display-transform: FIXED
"""
new = """banner:
  display-transform: FIXED
  standing-scale: 0.55
  standing-y-offset: 1.00
  standing-surface-offset: 0.00
  wall-scale: 0.48
  wall-y-offset: 0.58
  wall-surface-offset: 0.47
  yaw-offset-degrees: 0.0
"""
assert old in s, "banner config block not found"
s = s.replace(old, new)
p.write_text(s, encoding="utf-8")

p = root / "src/main/java/ru/neverland/phantomdecor/PhantomDecorListener.java"
s = p.read_text(encoding="utf-8")

old = '''        float yaw = yawFor(data);
        Vector offset = offsetFor(data, 0.47);
        ItemStack bannerItem = createBannerItem(banner, original);
        String group = UUID.randomUUID().toString();

        banner.getBlock().setType(Material.AIR, false);
        Location displayLoc = blockLoc.clone().add(0.5, 0.7, 0.5).add(offset);
        ItemDisplay display = blockLoc.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(bannerItem);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setRotation(yaw, 0.0f);
            String transform = plugin.getConfig().getString("banner.display-transform", "FIXED");
            try {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(transform.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            }
            markDecor(entity, TYPE_BANNER, group, original, blockLoc, data);
        });
'''
new = '''        boolean wallBanner = data instanceof Directional;
        float yawOffset = (float) plugin.getConfig().getDouble("banner.yaw-offset-degrees", 0.0);
        float yaw = yawFor(data) + yawOffset;

        double surface = plugin.getConfig().getDouble(
                wallBanner ? "banner.wall-surface-offset" : "banner.standing-surface-offset",
                wallBanner ? 0.47 : 0.0
        );
        double y = plugin.getConfig().getDouble(
                wallBanner ? "banner.wall-y-offset" : "banner.standing-y-offset",
                wallBanner ? 0.58 : 1.0
        );
        float scale = (float) plugin.getConfig().getDouble(
                wallBanner ? "banner.wall-scale" : "banner.standing-scale",
                wallBanner ? 0.48 : 0.55
        );
        scale = Math.max(0.10f, Math.min(1.0f, scale));

        Vector offset = offsetFor(data, surface);
        ItemStack bannerItem = createBannerItem(banner, original);
        String group = UUID.randomUUID().toString();

        banner.getBlock().setType(Material.AIR, false);
        Location displayLoc = blockLoc.clone().add(0.5, y, 0.5).add(offset);
        final float finalScale = scale;

        ItemDisplay display = blockLoc.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(bannerItem);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setRotation(yaw, 0.0f);

            String transform = plugin.getConfig().getString("banner.display-transform", "FIXED");
            try {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(transform.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            }

            var transformation = entity.getTransformation();
            transformation.getScale().set(finalScale, finalScale, finalScale);
            entity.setTransformation(transformation);

            markDecor(entity, TYPE_BANNER, group, original, blockLoc, data);
        });
'''
assert old in s, "convertBanner render block not found"
s = s.replace(old, new)

p.write_text(s, encoding="utf-8")
