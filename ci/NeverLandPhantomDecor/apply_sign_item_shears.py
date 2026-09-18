from pathlib import Path
import sys

root = Path(sys.argv[1])

# Bump 0.2.0-test -> 0.2.1-test everywhere relevant.
for rel in [
    "build.gradle.kts",
    "src/main/resources/plugin.yml",
    "src/main/java/ru/neverland/phantomdecor/NeverLandPhantomDecorPlugin.java",
    "src/main/java/ru/neverland/phantomdecor/PhantomDecorCommand.java",
]:
    p = root / rel
    s = p.read_text(encoding="utf-8")
    s = s.replace("0.2.0-test", "0.2.1-test")
    p.write_text(s, encoding="utf-8")

# Messages/help.
p = root / "src/main/resources/config.yml"
s = p.read_text(encoding="utf-8")
s = s.replace(
    '  sign-item-removed: "<yellow>Предмет снят с таблички.</yellow>"',
    '  sign-item-removed: "<yellow>Предмет снят ножницами и возвращён в инвентарь. Если места нет — он выпал рядом.</yellow>"'
)
s = s.replace(
    '  sign-applied: "<gray>Мембрана растворилась. Основа таблички исчезла. ПКМ — редактировать, Shift+ПКМ — обратная сторона.</gray>"',
    '  sign-applied: "<gray>Мембрана растворилась. Основа таблички исчезла. ПКМ — редактировать. Shift+ПКМ предметом — закрепить предмет, ножницы — снять предмет/мембрану.</gray>"'
)
p.write_text(s, encoding="utf-8")

p = root / "src/main/java/ru/neverland/phantomdecor/PhantomDecorCommand.java"
s = p.read_text(encoding="utf-8")
s = s.replace(
    "Shift+ПКМ позволяет крепить предметы к табличкам.",
    "Shift+ПКМ крепит предметы к табличкам, ножницы снимают предмет с выбранной стороны."
)
p.write_text(s, encoding="utf-8")

# Listener changes.
p = root / "src/main/java/ru/neverland/phantomdecor/PhantomDecorListener.java"
s = p.read_text(encoding="utf-8")

old = '''            if (hand.getType() == Material.SHEARS) {
                if (!checkPermission(player)) return;
                signEditSessions.remove(player.getUniqueId());
                restoreDecor(player, interaction);
                return;
            }

            if (TYPE_SIGN.equals(type)) {
                if (!checkPermission(player)) return;

                Side side = sideForPlayer(interaction, player);
                if (player.isSneaking()) {
                    if (!checkSignItemPermission(player)) return;
                    if (hand.getType().isAir()) removeSignItem(player, interaction, side);
                    else placeSignItem(player, interaction, side, hand);
                } else {
                    openSignEditor(player, interaction, side);
                }
                return;
            }
'''
new = '''            if (TYPE_SIGN.equals(type)) {
                if (!checkPermission(player)) return;

                Side side = sideForPlayer(interaction, player);

                if (hand.getType() == Material.SHEARS) {
                    if (!checkSignItemPermission(player)) return;
                    Location signLocation = originalBlock(interaction).getLocation();
                    if (findSignItemDisplay(signLocation, side) != null) {
                        if (removeSignItem(player, interaction, side)) damageShears(player);
                    } else {
                        signEditSessions.remove(player.getUniqueId());
                        restoreDecor(player, interaction);
                    }
                    return;
                }

                if (player.isSneaking() && !hand.getType().isAir()) {
                    if (!checkSignItemPermission(player)) return;
                    placeSignItem(player, interaction, side, hand);
                } else {
                    openSignEditor(player, interaction, side);
                }
                return;
            }

            if (hand.getType() == Material.SHEARS) {
                if (!checkPermission(player)) return;
                signEditSessions.remove(player.getUniqueId());
                restoreDecor(player, interaction);
                return;
            }
'''
assert old in s, "phantom sign interaction block not found"
s = s.replace(old, new)

old = '''        if (state instanceof Sign sign) {
            if (player.isSneaking()) {
                event.setCancelled(true);
                if (!checkSignItemPermission(player)) return;

                Side side = sideForPlayer(sign.getBlockData(), sign.getLocation(), player);
                if (hand.getType().isAir()) removeSignItem(player, sign.getLocation(), sign.getBlockData(), side);
                else placeSignItem(player, sign.getLocation(), sign.getBlockData(), side, hand);
                return;
            }

            if (hand.getType() == Material.PHANTOM_MEMBRANE) {
'''
new = '''        if (state instanceof Sign sign) {
            Side side = sideForPlayer(sign.getBlockData(), sign.getLocation(), player);

            if (hand.getType() == Material.SHEARS) {
                event.setCancelled(true);
                if (!checkSignItemPermission(player)) return;
                if (removeSignItem(player, sign.getLocation(), sign.getBlockData(), side)) {
                    damageShears(player);
                }
                return;
            }

            if (player.isSneaking() && !hand.getType().isAir()) {
                event.setCancelled(true);
                if (!checkSignItemPermission(player)) return;
                placeSignItem(player, sign.getLocation(), sign.getBlockData(), side, hand);
                return;
            }

            if (hand.getType() == Material.PHANTOM_MEMBRANE) {
'''
assert old in s, "physical sign interaction block not found"
s = s.replace(old, new)

old = '''    private void removeSignItem(Player player, Interaction interaction, Side side) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        String dataString = pdc.get(plugin.originalBlockDataKey(), PersistentDataType.STRING);
        if (dataString == null || dataString.isBlank()) {
            message(player, "messages.sign-item-empty");
            return;
        }

        BlockData data;
        try {
            data = Bukkit.createBlockData(dataString);
        } catch (IllegalArgumentException ex) {
            message(player, "messages.sign-item-empty");
            return;
        }

        removeSignItem(player, originalBlock(interaction).getLocation(), data, side);
    }
'''
new = '''    private boolean removeSignItem(Player player, Interaction interaction, Side side) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        String dataString = pdc.get(plugin.originalBlockDataKey(), PersistentDataType.STRING);
        if (dataString == null || dataString.isBlank()) {
            message(player, "messages.sign-item-empty");
            return false;
        }

        BlockData data;
        try {
            data = Bukkit.createBlockData(dataString);
        } catch (IllegalArgumentException ex) {
            message(player, "messages.sign-item-empty");
            return false;
        }

        return removeSignItem(player, originalBlock(interaction).getLocation(), data, side);
    }
'''
assert old in s, "interaction removeSignItem method not found"
s = s.replace(old, new)

old = '''    private void removeSignItem(Player player, Location blockLoc, BlockData data, Side side) {
        ItemDisplay display = findSignItemDisplay(blockLoc, side);
        if (display == null) {
            message(player, "messages.sign-item-empty");
            return;
        }

        ItemStack item = display.getItemStack().clone();
        display.remove();
        refundItem(player, item, blockLoc.clone().add(0.5, 0.7, 0.5));
        message(player, "messages.sign-item-removed");
    }
'''
new = '''    private boolean removeSignItem(Player player, Location blockLoc, BlockData data, Side side) {
        ItemDisplay display = findSignItemDisplay(blockLoc, side);
        if (display == null) {
            message(player, "messages.sign-item-empty");
            return false;
        }

        ItemStack item = display.getItemStack().clone();
        display.remove();
        refundItem(player, item, blockLoc.clone().add(0.5, 0.7, 0.5));
        message(player, "messages.sign-item-removed");
        return true;
    }
'''
assert old in s, "location removeSignItem method not found"
s = s.replace(old, new)

old = '''        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.SHEARS) {
            if (!checkPermission(player)) return;
            restoreDecor(player, interaction);
        } else {
            message(player, "messages.use-shears");
        }
'''
new = '''        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.SHEARS) {
            if (!checkPermission(player)) return;

            if (TYPE_SIGN.equals(type)) {
                Side side = sideForPlayer(interaction, player);
                Location signLocation = originalBlock(interaction).getLocation();
                if (findSignItemDisplay(signLocation, side) != null) {
                    if (!checkSignItemPermission(player)) return;
                    if (removeSignItem(player, interaction, side)) damageShears(player);
                    return;
                }
            }

            restoreDecor(player, interaction);
        } else {
            message(player, "messages.use-shears");
        }
'''
assert old in s, "interaction damage block not found"
s = s.replace(old, new)

p.write_text(s, encoding="utf-8")
