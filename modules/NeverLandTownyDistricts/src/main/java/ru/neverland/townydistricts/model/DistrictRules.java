package ru.neverland.townydistricts.model;
import java.util.*;
public final class DistrictRules {
    private DistrictRules(){}
    public static void validate(District proposed,Collection<District> existing,Set<Cell> owned,int maxCells){
        if(proposed.cells().size()>maxCells)throw new IllegalArgumentException("Превышен предел участков района: "+maxCells);
        if(!owned.containsAll(proposed.cells()))throw new IllegalArgumentException("Все участки должны принадлежать вашему городу");
        if(!Cell.connected(proposed.cells()))throw new IllegalArgumentException("Участки района должны соединяться сторонами");
        for(District other:existing) if(!(other.town().equals(proposed.town())&&other.id().equals(proposed.id()))
                && !Collections.disjoint(other.cells(),proposed.cells()))throw new IllegalArgumentException("Участки уже заняты другим районом");
    }
    public record Building(String id,UUID world,int minX,int minZ,int maxX,int maxZ,int level,int requiredLevel) {
        public Optional<Set<Cell>> cells(int cellSize,int limit){
            if(cellSize<1||minX>maxX||minZ>maxZ)return Optional.empty();
            try{return Optional.of(Cell.rectangle(new Cell(world,Math.floorDiv(minX,cellSize),Math.floorDiv(minZ,cellSize)),
                    new Cell(world,Math.floorDiv(maxX,cellSize),Math.floorDiv(maxZ,cellSize)),limit));}
            catch(IllegalArgumentException ex){return Optional.empty();}
        }
        public boolean completed(){return requiredLevel>0&&level>=requiredLevel;}
    }
    public record Evaluation(Map<String,Double> multipliers,Map<String,String> districts,Set<String> industrialCombinations) {
        public Evaluation {multipliers=Map.copyOf(multipliers);districts=Map.copyOf(districts);industrialCombinations=Set.copyOf(industrialCombinations);}
    }
    public static Evaluation evaluate(Collection<District> districts,Collection<Building> buildings,
            Map<String,DistrictType> expected,Set<Cell> owned,int cellSize,double matching,double combination,
            double maximum,Set<String> requires){
        Map<String,Double> multipliers=new HashMap<>();Map<String,String> assigned=new HashMap<>();Set<String> combos=new HashSet<>();
        for(District district:districts){
            if(!owned.containsAll(district.cells())||!Cell.connected(district.cells()))continue;
            List<Building> matched=new ArrayList<>();
            for(Building building:buildings){
                var area=building.cells(cellSize,district.cells().size());
                if(area.isEmpty()||!district.cells().containsAll(area.get()))continue;
                assigned.put(building.id(),district.id());
                if(building.completed()&&expected.get(building.id())==district.type())matched.add(building);
            }
            Set<String> ids=new HashSet<>();matched.forEach(b->ids.add(b.id()));
            boolean combined=district.type()==DistrictType.INDUSTRIAL&&!requires.isEmpty()&&ids.containsAll(requires);
            if(combined)combos.add(district.id());
            for(Building building:matched)multipliers.put(building.id(),Math.min(maximum,1+matching+(combined?combination:0)));
        }
        return new Evaluation(multipliers,assigned,combos);
    }
    /** Fractional bonus is a probability; never exceed available output space. */
    public static int output(int base,double multiplier,double roll,int space){
        if(base<=0||!Double.isFinite(multiplier)||multiplier<=1)return base;
        double extra=base*(Math.min(3,multiplier)-1);int add=(int)extra;
        if(roll<extra-add)add++;
        return base+Math.max(0,Math.min(add,space-base));
    }
}
