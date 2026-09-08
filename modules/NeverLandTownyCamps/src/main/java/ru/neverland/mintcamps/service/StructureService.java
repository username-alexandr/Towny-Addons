package ru.neverland.mintcamps.service;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.model.BlockPos;
import ru.neverland.mintcamps.model.BlockSnapshot;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.model.Placement;
import ru.neverland.mintcamps.model.PlacementKind;
import ru.neverland.mintcamps.util.DirectionUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StructureService {
    public enum Validation { OK, WORLD_MISSING, OBSTRUCTED, WATER }

    private final JavaPlugin plugin;
    private final StructureGenerator generator;

    public StructureService(JavaPlugin plugin, StructureGenerator generator) {
        this.plugin = plugin;
        this.generator = generator;
    }

    public List<Placement> placements(Camp camp, StyleDefinitionProvider styles, int level) {
        return generator.generate(level, styles.style(camp.styleId()));
    }

    public Validation validate(Camp camp, List<Placement> placements, boolean allowCurrentBlocks) {
        World world = camp.world().orElse(null);
        if (world == null) return Validation.WORLD_MISSING;
        Set<BlockPos> current = allowCurrentBlocks ? new HashSet<>(camp.placedBlocks()) : Set.of();
        for (Placement placement : placements) {
            BlockPos worldPos = DirectionUtil.rotate(camp.anchor(), camp.facing(), placement.relative());
            if (current.contains(worldPos)) continue;
            Block block = world.getBlockAt(worldPos.x(), worldPos.y(), worldPos.z());
            if (block.isLiquid() && !plugin.getConfig().getBoolean("settings.placement.allow-water", false)) return Validation.WATER;
            if (placement.relative().y() == 0) {
                if (!canReplaceGround(block)) return Validation.OBSTRUCTED;
            } else if (!isReplaceable(block.getType())) {
                return Validation.OBSTRUCTED;
            }
        }
        return Validation.OK;
    }

    public void capture(Camp camp, List<Placement> placements) {
        World world = camp.world().orElseThrow();
        for (Placement placement : placements) {
            BlockPos position = DirectionUtil.rotate(camp.anchor(), camp.facing(), placement.relative());
            if (camp.snapshots().containsKey(position)) continue;
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            List<ItemStack> inventory = new ArrayList<>();
            if (block.getState() instanceof Container container) {
                for (ItemStack item : container.getInventory().getContents()) inventory.add(item == null ? null : item.clone());
            }
            camp.snapshots().put(position, new BlockSnapshot(block.getType(), block.getBlockData().getAsString(), inventory));
        }
    }

    public void apply(Camp camp, List<Placement> placements) {
        camp.placedBlocks().clear();
        for (Placement placement : placements) {
            applyPlacement(camp, placement);
        }
        camp.stashBlock(DirectionUtil.rotate(camp.anchor(), camp.facing(), generator.stashRelative(camp.level())));
    }

    public boolean migrateRaisedCommonBlocks(Camp camp, List<Placement> correctedCommon) {
        World world = camp.world().orElse(null);
        if (world == null || correctedCommon.isEmpty()) return false;
        BlockPos raisedFire = DirectionUtil.rotate(camp.anchor(), camp.facing(), new BlockPos(0, 1, 0));
        Material oldFire = world.getBlockAt(raisedFire.x(), raisedFire.y(), raisedFire.z()).getType();
        if (oldFire != Material.CAMPFIRE && oldFire != Material.SOUL_CAMPFIRE) return false;

        List<BlockPos> raisedPositions = correctedCommon.stream()
                .map(placement -> new BlockPos(placement.relative().x(), placement.relative().y() + 1, placement.relative().z()))
                .map(relative -> DirectionUtil.rotate(camp.anchor(), camp.facing(), relative))
                .toList();
        if (raisedPositions.stream().anyMatch(position -> !camp.snapshots().containsKey(position))) return false;
        if (validate(camp, correctedCommon, false) != Validation.OK) return false;

        capture(camp, correctedCommon);
        restorePositions(camp, raisedPositions);
        camp.placedBlocks().removeAll(raisedPositions);
        for (Placement placement : correctedCommon) applyPlacement(camp, placement);
        return true;
    }

    public void restoreCurrent(Camp camp) {
        restorePositions(camp, camp.placedBlocks());
        camp.placedBlocks().clear();
    }

    public List<ItemStack> drainPlacedContainers(Camp camp) {
        World world = camp.world().orElseThrow();
        List<ItemStack> items = new ArrayList<>();
        for (BlockPos position : camp.placedBlocks()) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (!(block.getState() instanceof Container container)) continue;
            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) items.add(item.clone());
            }
            container.getInventory().clear();
            container.update(true, false);
        }
        return items;
    }

    public void restoreAll(Camp camp) {
        restorePositions(camp, camp.snapshots().keySet());
        camp.placedBlocks().clear();
    }

    private void restorePositions(Camp camp, Iterable<BlockPos> positions) {
        World world = camp.world().orElseThrow();
        List<BlockPos> ordered = new ArrayList<>();
        positions.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(BlockPos::y).reversed());
        for (BlockPos position : ordered) {
            BlockSnapshot snapshot = camp.snapshots().get(position);
            if (snapshot == null) continue;
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (block.getState() instanceof Container current) current.getInventory().clear();
            BlockData data;
            try {
                data = Bukkit.createBlockData(ru.neverland.localization.MaterialLabels.canonicalBlockData(snapshot.blockData()));
            } catch (IllegalArgumentException exception) {
                data = snapshot.material().createBlockData();
            }
            block.setBlockData(data, false);
            BlockState state = block.getState();
            if (state instanceof Container container && !snapshot.inventory().isEmpty()) {
                ItemStack[] contents = snapshot.inventory().toArray(ItemStack[]::new);
                container.getInventory().setContents(contents);
                container.update(true, false);
            }
        }
    }

    private void configure(BlockData data, PlacementKind kind, Camp camp) {
        if (data instanceof Waterlogged waterlogged) waterlogged.setWaterlogged(false);
        if (data instanceof Orientable orientable) {
            orientable.setAxis(kind == PlacementKind.LOG_RIGHT_AXIS
                    ? (camp.facing().getModX() == 0 ? Axis.X : Axis.Z) : Axis.Y);
        }
        if (data instanceof Slab slab) {
            slab.setType(kind == PlacementKind.SLAB_TOP ? Slab.Type.TOP : Slab.Type.BOTTOM);
        }
        if (data instanceof Directional directional) {
            directional.setFacing(switch (kind) {
                case BACKWARD -> camp.facing().getOppositeFace();
                case LEFT -> DirectionUtil.relative(camp.facing(), 3);
                case RIGHT -> DirectionUtil.relative(camp.facing(), 1);
                default -> camp.facing();
            });
        }
        if (data instanceof Stairs stairs) {
            stairs.setHalf(kind == PlacementKind.SLAB_TOP ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
        }
        if (data instanceof Bed bed) {
            bed.setFacing(camp.facing());
            bed.setPart(kind == PlacementKind.BED_HEAD_FORWARD ? Bed.Part.HEAD : Bed.Part.FOOT);
            bed.setOccupied(false);
        }
        if (data instanceof Lantern lantern) lantern.setHanging(kind == PlacementKind.LANTERN_HANGING);
        if (data instanceof Rotatable rotatable && kind == PlacementKind.BANNER_FORWARD) rotatable.setRotation(camp.facing());
        if (data instanceof Campfire campfire) {
            campfire.setFacing(camp.facing());
            campfire.setLit(camp.burnUntil() > System.currentTimeMillis());
            campfire.setSignalFire(false);
        } else if (data instanceof Lightable lightable && kind == PlacementKind.CAMPFIRE) {
            lightable.setLit(camp.burnUntil() > System.currentTimeMillis());
        }
    }

    private void applyPlacement(Camp camp, Placement placement) {
        World world = camp.world().orElseThrow();
        BlockPos position = DirectionUtil.rotate(camp.anchor(), camp.facing(), placement.relative());
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        BlockData data = placement.material().createBlockData();
        configure(data, placement.kind(), camp);
        block.setBlockData(data, false);
        if (block.getState() instanceof Container container) {
            container.getInventory().clear();
            container.update(true, false);
        }
        camp.placedBlocks().add(position);
    }

    private boolean canReplaceGround(Block block) {
        if (block.getState() instanceof org.bukkit.block.TileState) return false;
        Material type = block.getType();
        if (type == Material.BEDROCK || type == Material.BARRIER || type == Material.END_PORTAL_FRAME) return false;
        return type.isSolid() || isReplaceable(type);
    }

    public boolean isReplaceable(Material type) {
        return type.isAir() || switch (type) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, VINE, GLOW_LICHEN,
                 SNOW, FIRE, SOUL_FIRE, SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT,
                 DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET, RED_TULIP, ORANGE_TULIP,
                 WHITE_TULIP, PINK_TULIP, OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY,
                 WITHER_ROSE, SUNFLOWER, LILAC, ROSE_BUSH, PEONY, TORCHFLOWER, PITCHER_PLANT -> true;
            default -> false;
        };
    }

    @FunctionalInterface
    public interface StyleDefinitionProvider {
        ru.neverland.mintcamps.model.StyleDefinition style(String id);
    }
}
