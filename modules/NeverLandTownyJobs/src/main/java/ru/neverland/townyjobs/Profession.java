package ru.neverland.townyjobs;
import java.util.Locale;
public enum Profession { BLACKSMITH,FARMER,ENGINEER,GUARD,MERCHANT,RESEARCHER,ALCHEMIST;
    public String id(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return name().toLowerCase(Locale.ROOT);} }
    public static Profession parse(String s){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {try{return valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception ex){throw new IllegalArgumentException("Неизвестная профессия");}} }
}
