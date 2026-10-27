package ru.neverland.townysieges;

/** Pure distance rules. Contact is measured to the nearest point of the block's full voxel. */
public final class TntRules {
    private TntRules(){}
    public static double distance(double x,double y,double z,int bx,int by,int bz) {
        double dx=Math.max(bx-x,Math.max(0,x-bx-1)),dy=Math.max(by-y,Math.max(0,y-by-1)),dz=Math.max(bz-z,Math.max(0,z-bz-1));
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    public static double damage(double distance,TntSettings settings) {
        if(!Double.isFinite(distance)||distance<0||distance>=settings.radius())return 0;
        if(distance<=settings.contact())return settings.damage();
        return settings.damage()*Math.pow((settings.radius()-distance)/(settings.radius()-settings.contact()),settings.falloff());
    }
    public static boolean protectedMaterial(String name) {
        return name.equals("BEDROCK")||name.equals("BARRIER")||name.equals("END_PORTAL_FRAME")||name.equals("END_PORTAL")
                ||name.equals("NETHER_PORTAL")||name.equals("REINFORCED_DEEPSLATE")||name.contains("COMMAND_BLOCK")
                ||name.equals("STRUCTURE_BLOCK")||name.equals("JIGSAW")||name.equals("STRUCTURE_VOID");
    }
}
