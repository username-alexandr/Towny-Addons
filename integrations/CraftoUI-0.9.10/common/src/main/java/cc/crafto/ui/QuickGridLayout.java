package cc.crafto.ui;

/** Compact directory geometry in GUI units, after Minecraft applies GUI Scale. */
public record QuickGridLayout(int columns, int cellWidth, int rows) {
    public static final int GAP = 8, ROW_STRIDE = 24, STAR_WIDTH = 20, STAR_GAP = 4;
    public static QuickGridLayout fit(int width, int availableHeight) {
        int columns = Math.clamp((width + GAP) / (168 + GAP), 1, 4);
        return new QuickGridLayout(columns, (width - (columns - 1) * GAP) / columns,
                Math.max(1, (availableHeight + ROW_STRIDE - 20) / ROW_STRIDE));
    }
    public int capacity() { return rows * columns; }
    public int buttonWidth() { return cellWidth - STAR_WIDTH - STAR_GAP; }
}
