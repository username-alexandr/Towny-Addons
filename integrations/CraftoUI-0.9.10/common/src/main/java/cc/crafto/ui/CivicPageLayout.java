package cc.crafto.ui;

/** Shared civic-page chrome in logical GUI pixels. The body scrolls; navigation stays visible. */
public record CivicPageLayout(int x,int y,int width,int height,int padding,boolean compact) {
    public record Box(int x,int y,int width,int height) {
        public int right(){return x+width;} public int bottom(){return y+height;}
    }
    public static CivicPageLayout fit(int width,int height){
        // Keep world visible around the dialog. Small GUI viewports retain useful body space.
        int marginX=width<480?8:Math.max(16,width/16),marginY=height<300?8:Math.max(16,height/12);
        int w=Math.max(1,Math.min(640,width-2*marginX)),h=Math.max(1,Math.min(400,height-2*marginY));
        return new CivicPageLayout((width-w)/2,(height-h)/2,w,h,w<400?10:18,h<300);
    }
    public int left(){return x+padding;}
    public int span(){return Math.max(1,width-2*padding);}
    public int navY(){return y+(compact?34:58);}
    public int navHeight(){return compact?22:28;}
    public int bodyTop(){return navY()+navHeight()+10;}
    public int bottom(){return y+height-60;}
    public int footerY(){return y+height-27;}
    public int viewport(){return Math.max(1,bottom()-bodyTop());}
    public boolean iconTabs(){return span()<480;}
    public int columns(){return span()>=600?2:1;}
    // At least two rows per page; a small viewport scrolls instead of dropping records.
    public int capacity(){return Math.clamp(Math.max(2,viewport()/68)*columns(),1,12);}
    public Box tab(int index,int count){
        int start=left()+index*span()/count,end=left()+(index+1)*span()/count;
        return new Box(start,navY(),Math.max(1,end-start-2),navHeight());
    }
    public Box card(int index,int count,int top){
        int columns=count==1?1:columns(),w=(span()-8-(columns-1)*10)/columns;
        return new Box(left()+index%columns*(w+10),top+index/columns*68,w,60);
    }
    public int scrollToReveal(int scroll,int content,int top,int bottom){
        int next=scroll;if(top<next)next=top;else if(bottom>next+viewport())next=bottom-viewport();
        return Math.clamp(next,0,Math.max(0,content-viewport()));
    }
}
