package ru.neverland.townybuilds.construction;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

public final class ObstructionValidationSmoke {
    public static void main(String[] args) {
        BlockOffset offset = new BlockOffset(-3, -2, 7);
        var previous = new BlueprintPlan("test", 1, "test", Map.of(offset,
                new BlueprintBlock(Material.SPRUCE_PLANKS, BlockRole.RESIDENT, 1)));
        check(ConstructionService.isCompletedBlock(previous, offset, Material.SPRUCE_PLANKS), "Old structure in excavation is preserved");
        check(!ConstructionService.isCompletedBlock(previous, offset, Material.STONE), "Unrelated block is not treated as completed");
        check(!ConstructionService.isCompletedBlock(null, offset, Material.SPRUCE_PLANKS), "No old structure");
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            var site = new ConstructionSite("test", UUID.randomUUID(), 47, -58, -30, face, 1, 2, 2, true);
            Location world = site.location(null, offset);
            check(site.offset(world).equals(offset), "Negative coordinates round trip: " + face);
        }
        System.out.println("ObstructionValidationSmoke OK");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
