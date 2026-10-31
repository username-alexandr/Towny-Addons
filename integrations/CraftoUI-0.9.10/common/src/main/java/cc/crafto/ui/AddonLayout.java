package cc.crafto.ui;
/** Compact geometry; original native slot indexes are independent of layout. */
public record AddonLayout(int width,int height,float scale,int gridX,int gridY,int gridW,int columns,int rows,
                          int detailX,int detailY,int detailW,int detailH,int controlW,int controlStep){
    public static final int CARD_HEIGHT=42,STRIDE=48;
    public static AddonLayout fit(int vw,int vh,int pw,int ph,float setting,int columns,boolean market){
        return fit(vw,vh,pw,ph,setting,columns,market,false);
    }
    public static AddonLayout fit(int vw,int vh,int pw,int ph,float setting,int columns,boolean market,boolean details){
        var frame=CivicPageLayout.fit(vw,vh);float desired=Math.clamp(setting,.5f,1.5f);
        int w=Math.max(240,Math.min(Math.min(pw,640),(int)(frame.width()/desired)));
        int h=Math.max(details?188:156,Math.min(Math.min(ph,400),(int)(frame.height()/desired)));
        float scale=Math.max(.05f,Math.min(desired,Math.min((float)frame.width()/w,(float)frame.height()/h)));
        int bottom=h-50,gx=16,gy=58,gw=w-32,dx=0,dw=0,dh=0;
        if(details&&w>=480&&h>=250){dw=Math.clamp(w/3,160,200);dx=w-dw-16;gw=dx-gx-12;dh=bottom-gy;}
        int cols=Math.clamp((gw+8)/118,1,Math.clamp(columns,1,4));
        return new AddonLayout(w,h,scale,gx,gy,gw,cols,Math.max(1,(bottom-gy+6)/STRIDE),dx,gy,dw,dh,0,0);
    }
    public int pageSize(){return columns*rows;}
    public AddonLayout reserveToolbar(){
        int h=Math.max(188,height);float s=scale*Math.min(1f,(float)height/h);
        return new AddonLayout(width,h,s,gridX,gridY,gridW,columns,Math.max(1,(h-88-gridY+6)/STRIDE),detailX,detailY,detailW,Math.max(0,detailH-38),controlW,controlStep);
    }
    public AddonLayout reserveActionBar(){
        return new AddonLayout(width,height,scale,gridX,gridY,gridW,columns,Math.max(1,(height-82-gridY+6)/STRIDE),
            detailX,detailY,detailW,Math.max(0,detailH-32),controlW,controlStep);
    }
    /** Reserve a separated lower band for native administrative controls. */
    public AddonLayout reserveAdminFooter(int footerRows){
        if(footerRows<=0)return this;
        int h=Math.max(height,165+footerRows*56);
        float s=scale*Math.min(1f,(float)height/h);
        int footerTop=h-49-footerRows*56;
        return new AddonLayout(width,h,s,gridX,gridY,gridW,columns,
            Math.max(1,(footerTop-12-gridY+6)/STRIDE),detailX,detailY,detailW,
            Math.max(0,Math.min(detailH,footerTop-12-detailY)),controlW,controlStep);
    }
}
