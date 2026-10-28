package ru.neverland.townybuilds;
import java.util.*;
import ru.neverland.townybuilds.construction.*;
public final class BuildingStylesSmoke {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){var generator=new BuildingBlueprintGenerator();long blocks=0;int plans=0;
        for(String project:generator.supportedProjects())for(int level=1;level<=generator.maximumStage(project);level++){
            var base=generator.generate(project,level);for(var style:BuildingStyle.values()){
                int version=style.architecture(ConstructionSite.CURRENT_ARCHITECTURE_VERSION);var themed=generator.generateForArchitecture(project,level,version);check(themed.blocks().keySet().equals(base.blocks().keySet()),"same geometry "+project+style);check(BuildingStyle.ofArchitecture(version)==style,"saved identity");check(BuildingStyle.baseArchitecture(version)==ConstructionSite.CURRENT_ARCHITECTURE_VERSION,"saved base");
                for(var e:base.blocks().entrySet()){var a=e.getValue();var b=themed.blocks().get(e.getKey());check(a.role()==b.role()&&a.stage()==b.stage()&&a.facing()==b.facing()&&a.axis()==b.axis()&&a.half()==b.half()&&a.hinge()==b.hinge(),"unchanged interaction/shape "+project);check(b.material()==style.material(a.material()),"consistent visual "+project);blocks++;}plans++;
            }
        }
        for(var style:BuildingStyle.values())for(int version=1;version<=6;version++){var p=generator.generateForArchitecture("town_hall",3,style.architecture(version));check(p!=null&&!p.blocks().isEmpty(),"historical version themed");}
        var town=new ru.neverland.townybuilds.data.TownData(UUID.randomUUID(),54);check(town.buildingStyle().equals("medieval")&&!town.styleChosen(),"legacy city unchanged");town.buildingStyle("fantasy",true);check(town.styleChosen()&&town.buildingStyle().equals("fantasy"),"city selection");
        check(!generator.generateForStyle("town_hall",3,BuildingStyle.MODERN).blocks().equals(generator.generateForStyle("town_hall",3,BuildingStyle.FANTASY).blocks()),"actual visual variants");System.out.println("BuildingStylesSmoke PASS: "+plans+" plans, "+blocks+" block identities, historical styles and city defaults");
    }
}
