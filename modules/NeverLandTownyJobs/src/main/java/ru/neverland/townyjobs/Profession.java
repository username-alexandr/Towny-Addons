package ru.neverland.townyjobs;
import java.util.Locale;
public enum Profession { BLACKSMITH,FARMER,ENGINEER,GUARD,MERCHANT,RESEARCHER,ALCHEMIST;
    public String id(){return name().toLowerCase(Locale.ROOT);}
    public static Profession parse(String s){try{return valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception ex){throw new IllegalArgumentException("Неизвестная профессия");}}
}
