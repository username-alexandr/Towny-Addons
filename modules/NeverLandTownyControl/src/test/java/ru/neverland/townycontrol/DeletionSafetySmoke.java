package ru.neverland.townycontrol;

import java.nio.file.*;
import java.util.*;
import static ru.neverland.townycontrol.DeletionFiles.*;

/** Regression cases for destructive scope, damaged snapshots, vertical trenches and restart replay. */
public final class DeletionSafetySmoke {
    private static int checks;
    private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    interface Attempt {void run()throws Exception;}
    private static void rejected(Attempt action,String message)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("deletion-safety");Plot plot=new Plot(UUID.randomUUID(),-1,2);UUID town=UUID.randomUUID();
        check(plot.contains(-1,32)&&plot.contains(-16,47)&&!plot.contains(0,32)&&!plot.contains(-17,32),"negative coordinates stay within the original plot");
        var blocks=new ArrayList<>(Collections.nCopies(256*383,"minecraft:stone"));blocks.set(0,"minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        Baseline baseline=new Baseline(plot,town,123,-64,320,blocks);Path snapshot=root.resolve("baseline.gz");saveBaseline(snapshot,baseline);
        check(loadBaseline(snapshot).equals(baseline),"complete baseline round trip preserves owner and claim epoch");
        check(baseline.at(-16,319,32).startsWith("minecraft:oak_stairs"),"snapshot top row maps to correct negative world coordinate");
        check(baseline.at(-1,-63,47).equals("minecraft:stone"),"last block maps to minimum plus one without bedrock shift");
        rejected(()->new Baseline(plot,town,123,-64,320,blocks.subList(0,blocks.size()-1)),"truncated native snapshot is rejected");
        rejected(()->new Baseline(plot,town,123,320,-64,blocks),"invalid height range is rejected");
        byte[] good=Files.readAllBytes(snapshot);Files.write(snapshot,Arrays.copyOf(good,good.length-8));rejected(()->loadBaseline(snapshot),"gzip trailer truncation cannot produce partial restoration");Files.write(snapshot,good);
        var change=new Change(-10,80,40,"minecraft:oak_planks","minecraft:air");var plan=new ChunkPlan(plot,-64,320,List.of(change));Path file=root.resolve("plan.gz");savePlan(file,plan);String hash=sha(file);
        check(loadPlan(file).equals(plan)&&sha(file).equals(hash),"immutable plan survives restart");
        rejected(()->new ChunkPlan(plot,-64,320,List.of(new Change(0,80,40,"minecraft:oak_planks","minecraft:air"))),"neighbor plot cannot enter plan");
        rejected(()->new ChunkPlan(plot,-64,320,List.of(change,change)),"duplicate block actions are rejected");
        rejected(()->new ChunkPlan(plot,-64,320,List.of(new Change(-10,-64,40,"minecraft:stone","minecraft:air"))),"minimum bedrock layer cannot enter plan");
        check(decide(change,"minecraft:oak_planks",false,false)==Decision.APPLY,"authorized exact original block can change");
        check(decide(change,"minecraft:air",false,false)==Decision.ALREADY_APPLIED,"restart does not reapply completed block");
        check(decide(change,"minecraft:diamond_block",false,false)==Decision.SKIP,"new player block is preserved");
        check(decide(change,"minecraft:oak_planks",true,false)==Decision.SKIP,"tile inventory is preserved");
        check(decide(change,"minecraft:oak_planks",false,true)==Decision.SKIP,"new claimant is preserved");
        check(decide(new Change(-10,80,40,"minecraft:bedrock","minecraft:air"),"minecraft:bedrock",false,false)==Decision.SKIP,"bedrock cannot be erased");
        check(decide(new Change(-10,80,40,"minecraft:stone","minecraft:command_block"),"minecraft:stone",false,false)==Decision.SKIP,"system blocks cannot be restored from foreign snapshots");
        var trench=new ArrayList<Change>();for(int x=-16;x<-11;x++)for(int y=-63;y<80;y++)trench.add(new Change(x,y,32,"minecraft:stone","minecraft:air"));
        rejected(()->TownDeletionService.validateColumns(new ChunkPlan(plot,-64,320,trench),32),"five-block-wide trench down to bedrock is rejected before town deletion");
        TownDeletionService.validateColumns(plan,32);check(true,"small bounded building plan allowed");
        byte[] saved=Files.readAllBytes(file);rejected(()->DeletionFiles.atomic(file,out->{out.writeInt(1);throw new java.io.IOException("disk failure");}),"write failure reports failure");check(Arrays.equals(saved,Files.readAllBytes(file)),"write failure retains last durable plan");
        System.out.println("DELETION_SAFETY_PASS: "+checks+" checks");
    }
}
