package cc.crafto.ui.server;

import java.util.*;

/** Per-connection navigation, never a replay of clicks, commands or item snapshots. */
final class MenuHistory<T> {
    private record Entry<T>(String key,T value) {}
    private final List<Entry<T>> entries=new ArrayList<>();
    void visit(String key,T value){
        for(int i=entries.size()-1;i>=0;i--)if(entries.get(i).key().equals(key)){
            entries.subList(i,entries.size()).clear();break;
        }
        entries.add(new Entry<>(key,value));
        if(entries.size()>32)entries.removeFirst();
    }
    T current(){return entries.isEmpty()?null:entries.getLast().value();}
    T previous(){return entries.size()<2?null:entries.get(entries.size()-2).value();}
    T back(){if(!entries.isEmpty())entries.removeLast();return current();}
    void clear(){entries.clear();}
}
