package ru.neverland.townyideologies.model;

import java.util.*;

/** The exact benefits accepted with a reform; live catalogue edits cannot rewrite the agreement. */
public record PolicyTerms(String name, String scope, String government, Map<String, Double> numbers, List<String> effects) {
    public PolicyTerms {
        if(name==null||name.isBlank()||name.length()>160||!Set.of("GLOBAL","TOWN").contains(scope)||!Set.of("DEMOCRACY","MONARCHY","AUTOCRACY").contains(government))throw new IllegalArgumentException("Неверные условия реформы");
        numbers=Map.copyOf(numbers);effects=List.copyOf(effects);
        if(!numbers.keySet().equals(Set.of("growth-multiplier","smelt-multiplier","health-hearts","minecart-multiplier","placement-cooldown-ticks","trade-output-multiplier"))||numbers.values().stream().anyMatch(v->!Double.isFinite(v)||v<0||v>1_000_000)||effects.size()>32||effects.stream().anyMatch(s->s==null||s.length()>80||!s.matches("[a-zA-Z0-9_]+(?::[0-9]{1,3})?")))throw new IllegalArgumentException("Неверные бонусы реформы");
    }
}
