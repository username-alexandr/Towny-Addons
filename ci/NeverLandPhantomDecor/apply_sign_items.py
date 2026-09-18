from pathlib import Path
import sys

root = Path(sys.argv[1])

# build.gradle.kts
p = root / 'build.gradle.kts'
s = p.read_text(encoding='utf-8')
s = s.replace('version = "0.1.2-test"', 'version = "0.2.0-test"')
p.write_text(s, encoding='utf-8')

# plugin.yml
p = root / 'src/main/resources/plugin.yml'
s = p.read_text(encoding='utf-8')
s = s.replace('version: 0.1.2-test', 'version: 0.2.0-test')
s = s.replace('  neverlandphantomdecor.admin:\n    default: op\n',
              '  neverlandphantomdecor.signitems:\n    default: true\n  neverlandphantomdecor.admin:\n    default: op\n')
p.write_text(s, encoding='utf-8')

# config.yml
p = root / 'src/main/resources/config.yml'
s = p.read_text(encoding='utf-8')
s = s.replace(
    '  restore-failed: "<red>Не удалось восстановить декор. Сообщите администратору.</red>"\n',
    '  restore-failed: "<red>Не удалось восстановить декор. Сообщите администратору.</red>"\n'
    '  sign-item-placed: "<green>Предмет закреплён на табличке.</green>"\n'
    '  sign-item-removed: "<yellow>Предмет снят с таблички.</yellow>"\n'
    '  sign-item-empty: "<gray>На этой стороне таблички нет предмета.</gray>"\n'
    '  sign-item-no-permission: "<red>У вас нет разрешения размещать предметы на табличках.</red>"\n'
)
s = s.replace(
    'sign:\n  line-width: 120\n  shadowed: false\n  see-through: true\n',
    'sign:\n  line-width: 120\n  shadowed: false\n  see-through: true\n\n'
    'sign-items:\n  surface-offset: 0.515\n  y-offset: 0.55\n  display-transform: FIXED\n'
)
p.write_text(s, encoding='utf-8')

# Main plugin keys/version
p = root / 'src/main/java/ru/neverland/phantomdecor/NeverLandPhantomDecorPlugin.java'
s = p.read_text(encoding='utf-8')
s = s.replace('    private NamespacedKey signDisplaySideKey;\n',
              '    private NamespacedKey signDisplaySideKey;\n    private NamespacedKey signItemKey;\n    private NamespacedKey signItemSideKey;\n')
s = s.replace('        signDisplaySideKey = new NamespacedKey(this, "sign_display_side");\n',
              '        signDisplaySideKey = new NamespacedKey(this, "sign_display_side");\n        signItemKey = new NamespacedKey(this, "sign_item");\n        signItemSideKey = new NamespacedKey(this, "sign_item_side");\n')
s = s.replace('        getLogger().info("NeverLandPhantomDecor 0.1.2-test enabled.");',
              '        getLogger().info("NeverLandPhantomDecor 0.2.0-test enabled.");')
s = s.replace('    public NamespacedKey signDisplaySideKey() { return signDisplaySideKey; }\n',
              '    public NamespacedKey signDisplaySideKey() { return signDisplaySideKey; }\n    public NamespacedKey signItemKey() { return signItemKey; }\n    public NamespacedKey signItemSideKey() { return signItemSideKey; }\n')
p.write_text(s, encoding='utf-8')

# Command info
p = root / 'src/main/java/ru/neverland/phantomdecor/PhantomDecorCommand.java'
s = p.read_text(encoding='utf-8')
s = s.replace('0.1.2-test', '0.2.0-test')
s = s.replace(
    'Мембрана фантома: рамки, таблички и знамёна. Ножницы снимают мембрану.',
    'Мембрана фантома: рамки, таблички и знамёна. Shift+ПКМ позволяет крепить предметы к табличкам.'
)
p.write_text(s, encoding='utf-8')

# Listener
p = root / 'src/main/java/ru/neverland/phantomdecor/PhantomDecorListener.java'
s = p.read_text(encoding='utf-8')
s = s.replace('import org.bukkit.DyeColor;\n', 'import org.bukkit.DyeColor;\nimport org.bukkit.GameMode;\n')
s = s.replace('import org.bukkit.event.block.Action;\n',
              'import org.bukkit.event.block.Action;\nimport org.bukkit.event.block.BlockBreakEvent;\nimport org.bukkit.event.block.BlockExplodeEvent;\n')
s = s.replace('import org.bukkit.event.entity.EntityDamageByEntityEvent;\n',
              'import org.bukkit.event.entity.EntityDamageByEntityEvent;\nimport org.bukkit.event.entity.EntityExplodeEvent;\n')

old = '''            if (TYPE_SIGN.equals(type)) {
                if (!checkPermission(player)) return;
                openSignEditor(player, interaction, player.isSneaking() ? Side.BACK : Side.FRONT);
                return;
            }
'''
new = '''            if (TYPE_SIGN.equals(type)) {
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
assert old in s
s = s.replace(old, new)

old = '''    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.PHANTOM_MEMBRANE) return;
        if (!checkPermission(player)) {
            event.setCancelled(true);
            return;
        }

        var state = event.getClickedBlock().getState();
        if (state instanceof Sign sign) {
            event.setCancelled(true);
            convertSign(player, sign, hand);
        } else if (state instanceof Banner banner) {
            event.setCancelled(true);
            convertBanner(player, banner, hand);
        }
    }
'''
new = '''    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Block block = event.getClickedBlock();
        var state = block.getState();

        if (state instanceof Sign sign) {
            if (player.isSneaking()) {
                event.setCancelled(true);
                if (!checkSignItemPermission(player)) return;

                Side side = sideForPlayer(sign.getBlockData(), sign.getLocation(), player);
                if (hand.getType().isAir()) removeSignItem(player, sign.getLocation(), sign.getBlockData(), side);
                else placeSignItem(player, sign.getLocation(), sign.getBlockData(), side, hand);
                return;
            }

            if (hand.getType() == Material.PHANTOM_MEMBRANE) {
                event.setCancelled(true);
                if (!checkPermission(player)) return;
                convertSign(player, sign, hand);
            }
            return;
        }

        if (state instanceof Banner banner && hand.getType() == Material.PHANTOM_MEMBRANE) {
            event.setCancelled(true);
            if (!checkPermission(player)) return;
            convertBanner(player, banner, hand);
        }
    }
'''
assert old in s
s = s.replace(old, new)

marker = '''    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        signEditSessions.remove(event.getPlayer().getUniqueId());
    }
'''
insert = '''    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        signEditSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) return;
        dropAndRemoveSignItems(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getState() instanceof Sign) dropAndRemoveSignItems(block, true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getState() instanceof Sign) dropAndRemoveSignItems(block, true);
        }
    }
'''
assert marker in s
s = s.replace(marker, insert)

helpers = r'''    private void placeSignItem(Player player, Interaction interaction, Side side, ItemStack hand) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        String dataString = pdc.get(plugin.originalBlockDataKey(), PersistentDataType.STRING);
        if (dataString == null || dataString.isBlank()) {
            message(player, "messages.restore-failed");
            return;
        }

        BlockData data;
        try {
            data = Bukkit.createBlockData(dataString);
        } catch (IllegalArgumentException ex) {
            message(player, "messages.restore-failed");
            return;
        }

        placeSignItem(player, originalBlock(interaction).getLocation(), data, side, hand);
    }

    private void removeSignItem(Player player, Interaction interaction, Side side) {
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

    private void placeSignItem(Player player, Location blockLoc, BlockData data, Side side, ItemStack hand) {
        if (hand == null || hand.getType().isAir()) return;

        ItemDisplay existing = findSignItemDisplay(blockLoc, side);
        if (existing != null) {
            refundItem(player, existing.getItemStack().clone(), blockLoc.clone().add(0.5, 0.7, 0.5));
            existing.remove();
        }

        ItemStack shown = hand.clone();
        shown.setAmount(1);

        Location displayLoc = signItemLocation(blockLoc, data, side);
        float yaw = yawFor(data) + (side == Side.BACK ? 180.0f : 0.0f);

        ItemDisplay display = blockLoc.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(shown);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setRotation(yaw, 0.0f);

            String transform = plugin.getConfig().getString("sign-items.display-transform", "FIXED");
            try {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(transform.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            }

            PersistentDataContainer itemPdc = entity.getPersistentDataContainer();
            itemPdc.set(plugin.signItemKey(), PersistentDataType.BYTE, (byte) 1);
            itemPdc.set(plugin.signItemSideKey(), PersistentDataType.STRING, side == Side.BACK ? "back" : "front");
            itemPdc.set(plugin.originalXKey(), PersistentDataType.INTEGER, blockLoc.getBlockX());
            itemPdc.set(plugin.originalYKey(), PersistentDataType.INTEGER, blockLoc.getBlockY());
            itemPdc.set(plugin.originalZKey(), PersistentDataType.INTEGER, blockLoc.getBlockZ());
            itemPdc.set(plugin.originalBlockDataKey(), PersistentDataType.STRING, data.getAsString());
        });
        display.setPersistent(true);

        if (player.getGameMode() != GameMode.CREATIVE) consumeOne(hand);
        message(player, "messages.sign-item-placed");
    }

    private void removeSignItem(Player player, Location blockLoc, BlockData data, Side side) {
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

    private ItemDisplay findSignItemDisplay(Location blockLoc, Side side) {
        String wantedSide = side == Side.BACK ? "back" : "front";
        Location center = blockLoc.clone().add(0.5, 0.5, 0.5);

        for (Entity entity : blockLoc.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
            if (!(entity instanceof ItemDisplay display)) continue;

            PersistentDataContainer pdc = display.getPersistentDataContainer();
            Byte marker = pdc.get(plugin.signItemKey(), PersistentDataType.BYTE);
            if (marker == null || marker != (byte) 1) continue;

            Integer x = pdc.get(plugin.originalXKey(), PersistentDataType.INTEGER);
            Integer y = pdc.get(plugin.originalYKey(), PersistentDataType.INTEGER);
            Integer z = pdc.get(plugin.originalZKey(), PersistentDataType.INTEGER);
            String displaySide = pdc.get(plugin.signItemSideKey(), PersistentDataType.STRING);

            if (x != null && y != null && z != null
                    && x == blockLoc.getBlockX()
                    && y == blockLoc.getBlockY()
                    && z == blockLoc.getBlockZ()
                    && wantedSide.equals(displaySide)) {
                return display;
            }
        }
        return null;
    }

    private void dropAndRemoveSignItems(Block block, boolean drop) {
        Location blockLoc = block.getLocation();
        Location center = blockLoc.clone().add(0.5, 0.5, 0.5);

        for (Entity entity : block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
            if (!(entity instanceof ItemDisplay display)) continue;

            PersistentDataContainer pdc = display.getPersistentDataContainer();
            Byte marker = pdc.get(plugin.signItemKey(), PersistentDataType.BYTE);
            if (marker == null || marker != (byte) 1) continue;

            Integer x = pdc.get(plugin.originalXKey(), PersistentDataType.INTEGER);
            Integer y = pdc.get(plugin.originalYKey(), PersistentDataType.INTEGER);
            Integer z = pdc.get(plugin.originalZKey(), PersistentDataType.INTEGER);

            if (x == null || y == null || z == null
                    || x != block.getX() || y != block.getY() || z != block.getZ()) continue;

            ItemStack item = display.getItemStack().clone();
            display.remove();
            if (drop && item != null && !item.getType().isAir()) {
                block.getWorld().dropItemNaturally(center, item);
            }
        }
    }

    private void refundItem(Player player, ItemStack item, Location dropLocation) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            dropLocation.getWorld().dropItemNaturally(dropLocation, leftover);
        }
    }

    private Location signItemLocation(Location blockLoc, BlockData data, Side side) {
        double surface = plugin.getConfig().getDouble("sign-items.surface-offset", 0.515);
        double y = plugin.getConfig().getDouble("sign-items.y-offset", 0.55);
        Vector forward = facingVector(data);
        if (side == Side.BACK) forward.multiply(-1.0);
        return blockLoc.clone().add(0.5, y, 0.5).add(forward.multiply(surface));
    }

    private Side sideForPlayer(Interaction interaction, Player player) {
        String dataString = interaction.getPersistentDataContainer().get(
                plugin.originalBlockDataKey(), PersistentDataType.STRING);
        if (dataString == null || dataString.isBlank()) return Side.FRONT;
        try {
            return sideForPlayer(Bukkit.createBlockData(dataString), originalBlock(interaction).getLocation(), player);
        } catch (IllegalArgumentException ex) {
            return Side.FRONT;
        }
    }

    private Side sideForPlayer(BlockData data, Location blockLoc, Player player) {
        Vector facing = facingVector(data);
        Vector center = blockLoc.clone().add(0.5, 0.5, 0.5).toVector();
        Vector toPlayer = player.getEyeLocation().toVector().subtract(center);
        toPlayer.setY(0.0);
        if (toPlayer.lengthSquared() < 0.0001) return Side.FRONT;
        return facing.dot(toPlayer.normalize()) >= 0.0 ? Side.FRONT : Side.BACK;
    }

    private Vector facingVector(BlockData data) {
        Vector vector;
        if (data instanceof Rotatable rotatable) vector = rotatable.getRotation().getDirection();
        else if (data instanceof Directional directional) vector = directional.getFacing().getDirection();
        else vector = new Vector(0, 0, 1);

        vector = vector.clone().setY(0.0);
        if (vector.lengthSquared() < 0.0001) return new Vector(0, 0, 1);
        return vector.normalize();
    }

    private boolean checkSignItemPermission(Player player) {
        if (player.hasPermission("neverlandphantomdecor.signitems")) return true;
        message(player, "messages.sign-item-no-permission");
        return false;
    }

'''
marker = '    private void removeMembraneFromFrame(Player player, ItemFrame frame) {\n'
assert marker in s
s = s.replace(marker, helpers + marker)
p.write_text(s, encoding='utf-8')
