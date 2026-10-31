package cc.crafto.ui;

/** Vertical spacing for the searchable command catalog, in logical GUI pixels. */
public final class CommandCatalogLayout {
    public static final int SEARCH_Y = 6;
    public static final int SEARCH_HEIGHT = 22;
    public static final int CARD_Y = 42;
    public static final int CARD_HEIGHT = 54;
    public static final int ROW_STRIDE = 60;
    public static final int PAGER_HEIGHT = 18;

    private CommandCatalogLayout() {}

    public static int rows(int viewport) {
        return Math.max(1, (viewport - CARD_Y - CARD_HEIGHT) / ROW_STRIDE + 1);
    }

    public static int capacity(int viewport, int columns) {
        return Math.clamp(rows(viewport) * columns, 1, 24);
    }
}
