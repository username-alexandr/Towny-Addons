package ru.neverland.townyseasons;

public final class SeasonClockSmoke {
    static void check(boolean ok,String label) { if(!ok)throw new AssertionError(label); }
    public static void main(String[] args) {
        check(SeasonClock.at(0,1000,7,Season.SPRING).season()==Season.SPRING,"initial spring");
        var last=SeasonClock.at(6999,1000,7,Season.SPRING);check(last.day()==7&&last.remaining()==1,"exact last millisecond");
        var next=SeasonClock.at(7000,1000,7,Season.SPRING);check(next.season()==Season.SUMMER&&next.day()==1&&next.remaining()==7000,"boundary is half-open");
        check(SeasonClock.at(28000,1000,7,Season.SPRING).year()==2,"next year");
        check(SeasonClock.at(0,24000,7,Season.WINTER).season()==Season.WINTER,"configured starting season");
        check(SeasonClock.at(Long.MAX_VALUE,24000,365,Season.AUTUMN).day()>0,"long-running world no overflow");
        check(Season.parse("FALL")==Season.AUTUMN,"provider FALL mapping");
        for(long day=0;day<4000;day++){var d=SeasonClock.at(day*24000,24000,7,Season.SPRING);check(d.day()==day%7+1&&d.year()==day/28+1,"monotonic year/day");}
        System.out.println("SeasonClockSmoke PASS: boundaries, offline elapsed time, years, provider mapping, 4000 days");
    }
}
