package cc.crafto.ui;

/** Logical GUI pixels, independent of window resolution and Minecraft GUI Scale. */
public record CityDashboardLayout(int width, int viewport, int metricColumns, int shortcutColumns,
                                  int cardColumns, int metricsY, int shortcutsY, int tabsY,
                                  int cardsY, int contentHeight, int capacity,
                                  int financeY,int financeColumns,int financeCount,int developmentCount) {
    public static final int GAP=8, METRIC_HEIGHT=50, SHORTCUT_HEIGHT=30, CARD_HEIGHT=68;
    public record Box(int x,int y,int width,int height) {
        public int right(){return x+width;} public int bottom(){return y+height;}
    }
    public static CityDashboardLayout fit(int width,int viewport,int shortcutCount,int rows){
        return fit(width,viewport,shortcutCount,rows,0);
    }
    public static CityDashboardLayout fit(int width,int viewport,int shortcutCount,int rows,int financeCount){
        return fit(width,viewport,shortcutCount,rows,financeCount,3);
    }
    public static CityDashboardLayout fit(int width,int viewport,int shortcutCount,int rows,int financeCount,int developmentCount){
        width=Math.max(120,width);viewport=Math.max(1,viewport);
        financeCount=Math.clamp(financeCount,0,3);
        developmentCount=Math.clamp(developmentCount,0,shortcutCount);
        int metrics=width>=500?4:2,shortcuts=developmentCount==4?(width>=500?4:2):(width>=440?3:2),cards=width>=620?3:width>=360?2:1;
        int my=28,sy=my+16+(4+metrics-1)/metrics*(METRIC_HEIGHT+GAP);
        int financeColumns=width>=440?3:1,fy=sy+4;
        if(financeCount>0)sy=fy+(financeCount+financeColumns-1)/financeColumns*(METRIC_HEIGHT+GAP)+16;
        int secondColumns=width>=(developmentCount==4?500:620)?4:2;
        int ty=sy+(developmentCount+shortcuts-1)/shortcuts*(SHORTCUT_HEIGHT+GAP)+20+(Math.max(0,shortcutCount-developmentCount)+secondColumns-1)/secondColumns*(SHORTCUT_HEIGHT+GAP)+8,cy=ty+34;
        int capacity=Math.clamp(Math.max(1,(viewport-cy+GAP)/(CARD_HEIGHT+GAP))*cards,1,12);
        int content=cy+Math.max(1,(rows+cards-1)/cards)*(CARD_HEIGHT+GAP);
        return new CityDashboardLayout(width,viewport,metrics,shortcuts,cards,my,sy,ty,cy,content,capacity,fy,financeColumns,financeCount,developmentCount);
    }
    private Box cell(int index,int columns,int top,int height){int w=(width-(columns-1)*GAP)/columns;return new Box(index%columns*(w+GAP),top+index/columns*(height+GAP),w,height);}
    public Box metric(int i){return cell(i,metricColumns,metricsY,METRIC_HEIGHT);}
    public Box finance(int i){return cell(i,financeColumns,financeY,METRIC_HEIGHT);}
    public int managementY(){return shortcutsY+(developmentCount+shortcutColumns-1)/shortcutColumns*(SHORTCUT_HEIGHT+GAP)+20;}
    public Box shortcut(int i){return i<developmentCount?cell(i,shortcutColumns,shortcutsY,SHORTCUT_HEIGHT):cell(i-developmentCount,width>=(developmentCount==4?500:620)?4:2,managementY(),SHORTCUT_HEIGHT);}
    public Box tab(int i){return cell(i,3,tabsY,24);}
    public Box card(int i){return cell(i,cardColumns,cardsY,CARD_HEIGHT);}
    public int maxScroll(){return Math.max(0,contentHeight-viewport);}
    public int scrollToReveal(int scroll,Box box){
        int value=scroll;if(box.y()<value)value=box.y();else if(box.bottom()>value+viewport)value=box.bottom()-viewport;
        return Math.clamp(value,0,maxScroll());
    }
}
