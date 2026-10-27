package ru.neverland.townysieges;
import java.nio.file.*;import java.sql.*;import java.util.*;import java.util.concurrent.*;
public final class LootMigrationSmoke {
    public static void main(String[] args)throws Exception{
        Class.forName("org.sqlite.JDBC");var path=Files.createTempDirectory("legacy-raid-loot").resolve("raids.db");UUID id=UUID.randomUUID(),raid=UUID.randomUUID(),town=UUID.randomUUID(),other=UUID.randomUUID(),actor=UUID.randomUUID(),world=UUID.randomUUID();
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+path);var s=db.createStatement()){
            s.execute("CREATE TABLE raid_loot(id TEXT PRIMARY KEY,raid TEXT NOT NULL,attacker TEXT NOT NULL,defender TEXT NOT NULL,actor TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,book BLOB NOT NULL,money_bps INTEGER NOT NULL,resources_bps INTEGER NOT NULL,cents INTEGER NOT NULL,debit_before INTEGER NOT NULL,credit_before INTEGER NOT NULL,created INTEGER NOT NULL,state TEXT NOT NULL,note TEXT NOT NULL,UNIQUE(raid,defender))");
            try(var p=db.prepareStatement("INSERT INTO raid_loot VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){p.setString(1,id.toString());p.setString(2,raid.toString());p.setString(3,other.toString());p.setString(4,town.toString());p.setString(5,actor.toString());p.setString(6,world.toString());p.setInt(7,1);p.setInt(8,2);p.setInt(9,3);p.setBytes(10,new byte[]{7});p.setInt(11,1000);p.setInt(12,1000);p.setLong(13,10000);p.setLong(14,100000);p.setLong(15,50000);p.setLong(16,1);p.setString(17,"DONE");p.setString(18,"legacy");p.executeUpdate();}
        }
        var store=new RaidStore(path,e->{throw new AssertionError(e);});store.ready().toCompletableFuture().get(10,TimeUnit.SECONDS);var row=new RaidLootStore(store).get(id).toCompletableFuture().get(10,TimeUnit.SECONDS);
        if(!row.state().equals("DONE")||row.moneyBps()!=1000||row.cents()!=10000||!row.policy().isEmpty()||row.book()[0]!=7)throw new AssertionError("legacy receipt changed");
        store.close();store.stopped().toCompletableFuture().get(10,TimeUnit.SECONDS);System.out.println("LootMigrationSmoke PASS: legacy loot receipt, amount, state and book retained with additive defense policy");
    }
}
