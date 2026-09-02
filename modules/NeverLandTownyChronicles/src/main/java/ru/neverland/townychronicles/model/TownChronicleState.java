package ru.neverland.townychronicles.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TownChronicleState {
    private final UUID townId;private String name="",mayorName="",nationName="";private UUID mayorId,nationId;private int residents,blocks;private double balance;private long foundedAt,lastSeen;private boolean active=true;
    private final Set<String> achievements=new HashSet<>();private final Map<String,Integer>wonders=new HashMap<>();
    public TownChronicleState(UUID townId){this.townId=townId;}public UUID townId(){return townId;}public String name(){return name;}public void name(String v){name=v==null?"":v;}public String mayorName(){return mayorName;}public void mayor(String n,UUID id){mayorName=n==null?"":n;mayorId=id;}public UUID mayorId(){return mayorId;}public String nationName(){return nationName;}public void nation(String n,UUID id){nationName=n==null?"":n;nationId=id;}public UUID nationId(){return nationId;}public int residents(){return residents;}public void residents(int v){residents=v;}public int blocks(){return blocks;}public void blocks(int v){blocks=v;}public double balance(){return balance;}public void balance(double v){balance=v;}public long foundedAt(){return foundedAt;}public void foundedAt(long v){foundedAt=v;}public long lastSeen(){return lastSeen;}public void lastSeen(long v){lastSeen=v;}public boolean active(){return active;}public void active(boolean v){active=v;}public Set<String> achievements(){return achievements;}public Map<String,Integer>wonders(){return wonders;}
}
