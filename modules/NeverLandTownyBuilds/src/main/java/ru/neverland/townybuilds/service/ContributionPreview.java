package ru.neverland.townybuilds.service;

import java.util.*;
import org.bukkit.inventory.ItemStack;

/** Immutable quote: confirming a different source, town, level or inventory needs a fresh preview. */
public record ContributionPreview(UUID player,UUID town,String project,int level,boolean warehouse,
                                  String signature,String reason,List<Line> lines) {
    public record Line(ItemStack sample,String name,int needed,int personal,int stored,int taking) {
        public Line { sample=sample.clone(); }
        @Override public ItemStack sample(){return sample.clone();}
    }
    public ContributionPreview { lines=List.copyOf(lines); }
    public int total(){return lines.stream().mapToInt(Line::taking).sum();}
    public boolean ready(){return reason.isEmpty()&&total()>0;}
}
