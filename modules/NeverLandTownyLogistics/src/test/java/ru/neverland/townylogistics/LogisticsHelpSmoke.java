package ru.neverland.townylogistics;

import ru.neverland.townylogistics.command.LogisticsCommand;

public final class LogisticsHelpSmoke {
    public static void main(String[] args) {
        var lines=LogisticsCommand.helpLines();
        for(String action:java.util.List.of("filter", "limit", "keep", "pause", "resume")) {
            long count=lines.stream().filter(s->s.contains("/t logistics "+action+" ")).count();
            if(count!=1)throw new AssertionError("separate complete command: "+action);
        }
        if(lines.stream().filter(s->s.contains("/t logistics ")).anyMatch(s->s.contains(";")))throw new AssertionError("combined command line");
        System.out.println("LogisticsHelpSmoke PASS");
    }
}
