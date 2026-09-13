package ru.neverland.townyelections;
import java.util.*;

public final class ElectionSmoke {
    static UUID id(int n){return new UUID(0,n);}
    static Election campaign(){
        var e=new Election(id(100),10000);e.phase=Election.Phase.NOMINATION;e.start=100;e.nominationEnd=200;e.votingDuration=100;e.interval=1000;e.originalMayor=id(9);e.quorum=.5;
        e.seats.put("mayor",1);e.seats.put("councillor",2);e.electorate.addAll(Set.of(id(1),id(2),id(3),id(4)));return e;
    }
    static void reject(Runnable action){try{action.run();throw new AssertionError("accepted invalid operation");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var e=campaign();e.nominate(id(1),"mayor",101);e.nominate(id(2),"mayor",101);
        reject(()->e.nominate(id(1),"councillor",102));reject(()->e.nominate(id(3),"mayor",200));
        e.nominate(id(3),"councillor",101);e.nominate(id(4),"councillor",101);e.nominate(id(5),"councillor",101);
        e.phase=Election.Phase.VOTING;e.votingEnd=300;
        reject(()->e.vote(id(9),"mayor",List.of(id(1)),220));reject(()->e.vote(id(1),"mayor",List.of(id(1)),300));
        reject(()->e.vote(id(1),"mayor",List.of(id(1),id(2)),220));reject(()->e.vote(id(1),"councillor",List.of(id(3),id(3)),220));
        reject(()->e.vote(id(1),"mayor",List.of(id(3)),220));
        e.vote(id(1),"mayor",List.of(id(1)),220);e.vote(id(1),"mayor",List.of(id(2)),221);
        assert e.ballots.get("mayor").size()==1;assert e.ballots.get("mayor").get(id(1)).equals(List.of(id(2)));
        e.vote(id(2),"mayor",List.of(id(1)),220);e.tally(e.electorate,e.candidates.keySet());
        assert !e.winners.containsKey("mayor");assert e.results.get("mayor").startsWith("Ничья");
        e.vote(id(3),"mayor",List.of(id(1)),220);e.tally(e.electorate,e.candidates.keySet());assert e.winners.get("mayor").equals(List.of(id(1)));
        e.tally(Set.of(id(1),id(2)),e.candidates.keySet());assert !e.winners.containsKey("mayor"); // revoked vote cannot break tie
        e.tally(e.electorate,Set.of(id(2),id(3),id(4),id(5)));assert !e.winners.containsKey("mayor"); // invalid candidate votes do not create turnout
        e.vote(id(1),"councillor",List.of(id(3),id(4)),220);e.vote(id(2),"councillor",List.of(id(3),id(5)),220);
        e.tally(e.electorate,e.candidates.keySet());assert !e.winners.containsKey("councillor");
        e.vote(id(3),"councillor",List.of(id(4)),220);e.tally(e.electorate,e.candidates.keySet());assert new HashSet<>(e.winners.get("councillor")).equals(Set.of(id(3),id(4)));
        e.phase=Election.Phase.APPLYING;e.validate();var copy=e.copy();copy.ballots.get("mayor").clear();assert e.ballots.get("mayor").size()==3;
        var malformed=e.copy();malformed.winners.put("mayor",List.of(id(4)));reject(malformed::validate);
        var empty=campaign();empty.phase=Election.Phase.VOTING;empty.votingEnd=300;empty.tally(Set.of(),Set.of());assert empty.winners.isEmpty();
        System.out.println("ElectionSmoke PASS: deadlines, duplicate/replaced ballots, eligibility, quorum, ties, multiple seats, isolation");
    }
}
