package ru.neverland.townycontrol;
import java.util.*;
/** Safety graph is separate from plugin load order: mutually dependent features pause together. */
public final class ModuleGraph {
    private final Map<String,Set<String>> dependencies;
    public ModuleGraph(Map<String,Set<String>> dependencies) {
        var copy=new TreeMap<String,Set<String>>();dependencies.forEach((name,values)->copy.put(name,Set.copyOf(values)));
        for(var e:copy.entrySet())if(!copy.keySet().containsAll(e.getValue())||e.getValue().contains(e.getKey()))throw new IllegalArgumentException("Некорректная зависимость "+e.getKey());
        this.dependencies=Map.copyOf(copy);
    }
    public Set<String> modules() { return dependencies.keySet(); }
    public Set<String> closure(Set<String> roots) {
        if(!dependencies.keySet().containsAll(roots))throw new IllegalArgumentException("Неизвестный модуль");
        Set<String> result=new TreeSet<>(roots);boolean changed;
        do { changed=false;for(var e:dependencies.entrySet())if(!Collections.disjoint(e.getValue(),result))changed|=result.add(e.getKey()); }while(changed);
        return Set.copyOf(result);
    }
    public String resolve(String raw) {
        return modules().stream().filter(n->n.equalsIgnoreCase(raw)||n.substring("NeverLandTowny".length()).equalsIgnoreCase(raw)).findFirst().orElseThrow(()->new IllegalArgumentException("Неизвестный аддон: "+raw));
    }
}
