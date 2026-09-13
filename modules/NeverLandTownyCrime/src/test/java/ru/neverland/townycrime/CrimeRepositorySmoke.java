package ru.neverland.townycrime;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
public final class CrimeRepositorySmoke {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("crime-repository-");Path file=dir.resolve("data.yml");var repo=new CrimeRepository(file);repo.load();var state=TheftRecoverySmoke.plan();repo.put(state);var again=new CrimeRepository(file);again.load();check(again.all().get(state.town()).equals(state),"incident intent and amounts survive reload");
        Files.writeString(file,"schema: 1\ntowns:\n  broken: true\n");boolean denied=false;try{again.load();}catch(IOException e){denied=true;}check(denied&&!again.writable()&&again.all().get(state.town()).equals(state),"corruption keeps last snapshot but freezes mutation");
        Path blocked=dir.resolve("blocked.yml");var fault=new CrimeRepository(blocked);fault.load();Files.createDirectory(blocked);Files.writeString(blocked.resolve("occupied"),"x");denied=false;try{fault.put(state);}catch(IOException e){denied=true;}check(denied&&!fault.writable()&&fault.all().isEmpty(),"failed replacement cannot publish state");
        denied=false;try{fault.put(state);}catch(IOException e){denied=true;}check(denied,"subsequent mutation refused");
        System.out.println("CrimeRepositorySmoke OK");
    }
}
