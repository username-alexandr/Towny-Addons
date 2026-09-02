package ru.neverland.townybuilds;

import org.bukkit.Material;
import ru.neverland.townybuilds.construction.BlockOffset;
import ru.neverland.townybuilds.construction.BlockRole;
import ru.neverland.townybuilds.construction.BlueprintBlock;
import ru.neverland.townybuilds.construction.BlueprintPlan;
import ru.neverland.townybuilds.construction.BuildingBlueprintGenerator;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Точный изометрический рендер строится непосредственно из тех же BlueprintPlan, что использует сервер. */
public final class WonderPreviewRenderer {
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 1200;
    private static final Map<String, String> NAMES = Map.of(
            "sun_pyramid", "Пирамида Солнца",
            "great_colosseum", "Великий Колизей",
            "alexandria_lighthouse", "Александрийский маяк",
            "hanging_gardens", "Висячие сады",
            "archmage_spire", "Шпиль Архимагов"
    );

    private WonderPreviewRenderer() { }

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : "build/wonder-previews");
        Files.createDirectories(output);
        BuildingBlueprintGenerator generator = new BuildingBlueprintGenerator();
        Map<String, BufferedImage> rendered = new LinkedHashMap<>();
        for (String id : List.of("sun_pyramid", "great_colosseum", "alexandria_lighthouse",
                "hanging_gardens", "archmage_spire")) {
            BlueprintPlan plan = generator.generate(id, 1);
            BufferedImage image = render(id, plan);
            rendered.put(id, image);
            ImageIO.write(image, "png", output.resolve(id + ".png").toFile());
        }
        ImageIO.write(contactSheet(rendered), "png", output.resolve("wonders-overview.png").toFile());
        System.out.println("Точные превью сохранены в " + output.toAbsolutePath());
    }

    private static BufferedImage render(String id, BlueprintPlan plan) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setPaint(new GradientPaint(0, 0, new Color(174, 224, 255), 0, HEIGHT,
                new Color(245, 249, 252)));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);

        Bounds bounds = bounds(plan);
        double scale = Math.min(26.0, Math.min(1020.0 / Math.max(1, bounds.projectedWidth()),
                820.0 / Math.max(1, bounds.projectedHeight())));
        Projector projector = new Projector(scale, WIDTH / 2.0,
                175.0 - bounds.minimumProjectedY() * scale);
        drawGround(graphics, projector, bounds);

        List<Map.Entry<BlockOffset, BlueprintBlock>> entries = new ArrayList<>(plan.blocks().entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<BlockOffset, BlueprintBlock> entry) -> entry.getKey().x() - entry.getKey().z())
                .thenComparingInt(entry -> entry.getKey().y())
                .thenComparingInt(entry -> entry.getKey().x()));

        for (Map.Entry<BlockOffset, BlueprintBlock> entry : entries) {
            BlockOffset offset = entry.getKey();
            BlueprintBlock block = entry.getValue();
            Color base = materialColor(block.material());
            boolean top = !plan.blocks().containsKey(new BlockOffset(offset.x(), offset.y() + 1, offset.z()));
            boolean east = !plan.blocks().containsKey(new BlockOffset(offset.x() + 1, offset.y(), offset.z()));
            boolean north = !plan.blocks().containsKey(new BlockOffset(offset.x(), offset.y(), offset.z() - 1));
            if (block.material().name().endsWith("_DOOR")) {
                drawDoor(graphics, projector, offset, base);
            } else if (block.material().name().endsWith("_STAIRS")) {
                drawStair(graphics, projector, offset, block, base);
            } else {
                drawCuboid(graphics, projector, offset.x(), offset.y(), offset.z(),
                        offset.x() + 1, offset.y() + 1, offset.z() + 1, base, top, east, north);
            }
        }

        graphics.setColor(new Color(21, 31, 44));
        graphics.setFont(new Font("DejaVu Sans", Font.BOLD, 44));
        graphics.drawString(NAMES.get(id), 70, 68);
        graphics.setFont(new Font("DejaVu Sans", Font.PLAIN, 24));
        graphics.setColor(new Color(55, 70, 86));
        String dimensions = bounds.width() + " × " + bounds.depth() + " × " + bounds.height();
        graphics.drawString("Точная схема плагина • " + dimensions + " блоков", 72, 106);

        long residents = plan.blocks().values().stream().filter(block -> block.role() == BlockRole.RESIDENT).count();
        long decoration = plan.blocks().size() - residents;
        graphics.setColor(new Color(31, 43, 56, 225));
        graphics.fillRoundRect(64, HEIGHT - 92, 720, 54, 18, 18);
        graphics.setFont(new Font("DejaVu Sans", Font.PLAIN, 22));
        graphics.setColor(new Color(255, 255, 255));
        graphics.drawString("Жители: " + residents + " блоков   •   Автоотделка: " + decoration
                + "   •   Всего: " + plan.blocks().size(), 88, HEIGHT - 57);
        graphics.dispose();
        return image;
    }

    private static Bounds bounds(BlueprintPlan plan) {
        int minX = plan.blocks().keySet().stream().mapToInt(BlockOffset::x).min().orElse(0);
        int maxX = plan.blocks().keySet().stream().mapToInt(BlockOffset::x).max().orElse(0);
        int minZ = plan.blocks().keySet().stream().mapToInt(BlockOffset::z).min().orElse(0);
        int maxZ = plan.blocks().keySet().stream().mapToInt(BlockOffset::z).max().orElse(0);
        int minY = plan.blocks().keySet().stream().mapToInt(BlockOffset::y).min().orElse(0);
        int maxY = plan.blocks().keySet().stream().mapToInt(BlockOffset::y).max().orElse(0);
        double minimumProjectedX = Double.POSITIVE_INFINITY;
        double maximumProjectedX = Double.NEGATIVE_INFINITY;
        double minimumProjectedY = Double.POSITIVE_INFINITY;
        double maximumProjectedY = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{minX, maxX + 1.0}) for (double z : new double[]{minZ, maxZ + 1.0}) {
            for (double y : new double[]{minY, maxY + 1.0}) {
                double projectedX = x + z;
                double projectedY = (x - z) * 0.5 - y;
                minimumProjectedX = Math.min(minimumProjectedX, projectedX);
                maximumProjectedX = Math.max(maximumProjectedX, projectedX);
                minimumProjectedY = Math.min(minimumProjectedY, projectedY);
                maximumProjectedY = Math.max(maximumProjectedY, projectedY);
            }
        }
        return new Bounds(minX, maxX, minY, maxY, minZ, maxZ, minimumProjectedY,
                maximumProjectedX - minimumProjectedX, maximumProjectedY - minimumProjectedY);
    }

    private static void drawGround(Graphics2D graphics, Projector projector, Bounds bounds) {
        int margin = 3;
        Polygon ground = polygon(
                projector.point(bounds.minX() - margin, -0.04, bounds.minZ() - margin),
                projector.point(bounds.maxX() + margin + 1, -0.04, bounds.minZ() - margin),
                projector.point(bounds.maxX() + margin + 1, -0.04, bounds.maxZ() + margin + 1),
                projector.point(bounds.minX() - margin, -0.04, bounds.maxZ() + margin + 1));
        graphics.setColor(new Color(86, 151, 73));
        graphics.fillPolygon(ground);
        graphics.setColor(new Color(48, 101, 47));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawPolygon(ground);
    }

    private static void drawDoor(Graphics2D graphics, Projector projector, BlockOffset offset, Color base) {
        drawCuboid(graphics, projector, offset.x() + 0.08, offset.y(), offset.z() + 0.05,
                offset.x() + 0.92, offset.y() + 1, offset.z() + 0.17, base, true, true, true);
    }

    private static void drawStair(Graphics2D graphics, Projector projector, BlockOffset offset,
                                  BlueprintBlock block, Color base) {
        drawCuboid(graphics, projector, offset.x(), offset.y(), offset.z(), offset.x() + 1,
                offset.y() + 0.5, offset.z() + 1, base, true, true, true);
        double x1 = offset.x(), x2 = offset.x() + 1, z1 = offset.z(), z2 = offset.z() + 1;
        switch (block.facing()) {
            case NORTH -> z2 = offset.z() + 0.5;
            case SOUTH -> z1 = offset.z() + 0.5;
            case EAST -> x1 = offset.x() + 0.5;
            case WEST -> x2 = offset.x() + 0.5;
            default -> { }
        }
        drawCuboid(graphics, projector, x1, offset.y() + 0.5, z1, x2,
                offset.y() + 1, z2, base, true, true, true);
    }

    private static void drawCuboid(Graphics2D graphics, Projector projector,
                                   double x1, double y1, double z1, double x2, double y2, double z2,
                                   Color base, boolean top, boolean east, boolean north) {
        if (east) fillFace(graphics, shade(base, 0.72), polygon(
                projector.point(x2, y1, z1), projector.point(x2, y1, z2),
                projector.point(x2, y2, z2), projector.point(x2, y2, z1)));
        if (north) fillFace(graphics, shade(base, 0.86), polygon(
                projector.point(x1, y1, z1), projector.point(x2, y1, z1),
                projector.point(x2, y2, z1), projector.point(x1, y2, z1)));
        if (top) fillFace(graphics, shade(base, 1.12), polygon(
                projector.point(x1, y2, z1), projector.point(x2, y2, z1),
                projector.point(x2, y2, z2), projector.point(x1, y2, z2)));
    }

    private static void fillFace(Graphics2D graphics, Color color, Polygon polygon) {
        graphics.setColor(color);
        graphics.fillPolygon(polygon);
        graphics.setColor(new Color(24, 32, 42, 90));
        graphics.setStroke(new BasicStroke(0.75f));
        graphics.drawPolygon(polygon);
    }

    private static Polygon polygon(java.awt.Point... points) {
        Polygon polygon = new Polygon();
        for (java.awt.Point point : points) polygon.addPoint(point.x, point.y);
        return polygon;
    }

    private static Color shade(Color color, double factor) {
        return new Color(Math.min(255, (int) (color.getRed() * factor)),
                Math.min(255, (int) (color.getGreen() * factor)),
                Math.min(255, (int) (color.getBlue() * factor)), color.getAlpha());
    }

    private static Color materialColor(Material material) {
        String name = material.name();
        if (name.endsWith("_DOOR")) return new Color(132, 91, 51);
        if (name.contains("GLASS")) return name.contains("BLUE") ? new Color(75, 183, 224, 185)
                : name.contains("PURPLE") ? new Color(150, 80, 207, 190) : new Color(202, 236, 239, 170);
        if (name.contains("GOLD")) return new Color(235, 184, 47);
        if (name.contains("SANDSTONE")) return new Color(216, 195, 139);
        if (name.contains("QUARTZ")) return new Color(225, 219, 205);
        if (name.contains("PRISMARINE")) return new Color(92, 155, 146);
        if (name.contains("AMETHYST")) return new Color(149, 82, 190);
        if (name.contains("PURPUR") || name.contains("PURPLE")) return new Color(145, 84, 157);
        if (name.contains("CRYING_OBSIDIAN")) return new Color(69, 38, 94);
        if (name.contains("BLACKSTONE") || name.contains("DEEPSLATE")) return new Color(52, 53, 61);
        if (name.contains("MOSS") || name.contains("AZALEA")) return name.contains("FLOWERING")
                ? new Color(158, 104, 142) : new Color(86, 139, 65);
        if (name.contains("STONE") || name.contains("ANDESITE")) return new Color(122, 126, 126);
        if (name.contains("IRON")) return new Color(165, 169, 169);
        if (name.contains("WOOD") || name.contains("OAK")) return new Color(112, 79, 48);
        if (name.contains("RED_WOOL")) return new Color(164, 48, 48);
        if (name.contains("SEA_LANTERN") || name.contains("BEACON") || name.contains("END_ROD"))
            return new Color(151, 236, 226);
        if (name.contains("LANTERN") || name.contains("CAMPFIRE")) return new Color(232, 142, 52);
        return new Color(142, 138, 128);
    }

    private static BufferedImage contactSheet(Map<String, BufferedImage> rendered) {
        int cellWidth = 800;
        int cellHeight = 600;
        BufferedImage sheet = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(234, 242, 248));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        int index = 0;
        for (BufferedImage source : rendered.values()) {
            int x = (index % 3) * cellWidth;
            int y = (index / 3) * cellHeight;
            graphics.drawImage(source, x, y, cellWidth, cellHeight, null);
            index++;
        }
        graphics.dispose();
        return sheet;
    }

    private record Projector(double scale, double originX, double originY) {
        java.awt.Point point(double x, double y, double z) {
            int screenX = (int) Math.round(originX + (x + z) * scale);
            int screenY = (int) Math.round(originY + (x - z) * scale * 0.5 - y * scale);
            return new java.awt.Point(screenX, screenY);
        }
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                          double minimumProjectedY, double projectedWidth, double projectedHeight) {
        int width() { return maxX - minX + 1; }
        int depth() { return maxZ - minZ + 1; }
        int height() { return maxY - minY + 1; }
    }
}
