package cc.crafto.ui;
/** Compact order cards, kept within the server's twelve-row page envelope. */
public record OrderGridLayout(int columns,int cellWidth,int cellHeight,int capacity) {
    public static final int HEIGHT=70,GAP=8;
    public int stride(){return cellHeight+GAP;}
    public static OrderGridLayout fit(int width,int height){
        int columns=width>=470?2:1;
        int cellHeight=Math.clamp(height,44,HEIGHT);
        return new OrderGridLayout(columns,Math.max(1,(width-(columns-1)*GAP)/columns),cellHeight,Math.clamp(Math.max(1,(height+GAP)/(cellHeight+GAP))*columns,1,12));
    }
}
