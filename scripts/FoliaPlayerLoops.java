import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
/** Migrates audited independent per-player loops. Does not transform loops that return shared results. */
public final class FoliaPlayerLoops {
    record Edit(int from,int to,String text){}
    public static void main(String[] args)throws Exception {
        var files=new ArrayList<Path>();
        var selected=Set.of("ParticleService.java","EffectService.java","BonusService.java","AchievementService.java","ConstructionService.java","CivicAutomationService.java","ContractService.java","SupplyService.java","TradeService.java","QuestService.java","SiegeService.java","CompanyMenus.java","EnvironmentMenu.java","BuildingStorageService.java","ArmyService.java","CivicService.java");
        try(var paths=Files.walk(Path.of(args[0]).resolve("modules"))){paths.filter(p->p.toString().contains("/src/main/java/")&&selected.contains(p.getFileName().toString())).forEach(files::add);}
        var compiler=ToolProvider.getSystemJavaCompiler();try(var fm=compiler.getStandardFileManager(null,null,null)){
            var task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjectsFromPaths(files));var units=task.parse();var pos=Trees.instance(task).getSourcePositions();
            for(var unit:units){Path path=Path.of(unit.getSourceFile().toUri());String source=Files.readString(path);var edits=new ArrayList<Edit>();
                new TreeScanner<Void,Void>(){
                    public Void visitEnhancedForLoop(EnhancedForLoopTree loop,Void unused){
                        if(!loop.getExpression().toString().equals("Bukkit.getOnlinePlayers()"))return super.visitEnhancedForLoop(loop,unused);
                        int from=(int)pos.getStartPosition(unit,loop),body=(int)pos.getStartPosition(unit,loop.getStatement()),end=(int)pos.getEndPosition(unit,loop);
                        edits.add(new Edit(from,body,"ru.neverland.core.SuiteScheduler.players(plugin, "+loop.getVariable().getName()+" -> {"));edits.add(new Edit(end,end,"});"));
                        new TreeScanner<Void,Integer>(){
                            public Void visitContinue(ContinueTree node,Integer depth){if(depth==0){int a=(int)pos.getStartPosition(unit,node),b=(int)pos.getEndPosition(unit,node);edits.add(new Edit(a,b,"return;"));}return null;}
                            public Void visitEnhancedForLoop(EnhancedForLoopTree node,Integer depth){return super.visitEnhancedForLoop(node,depth+1);}
                            public Void visitForLoop(ForLoopTree node,Integer depth){return super.visitForLoop(node,depth+1);}
                            public Void visitWhileLoop(WhileLoopTree node,Integer depth){return super.visitWhileLoop(node,depth+1);}
                            public Void visitDoWhileLoop(DoWhileLoopTree node,Integer depth){return super.visitDoWhileLoop(node,depth+1);}
                        }.scan(loop.getStatement(),0);
                        return null;
                    }
                }.scan(unit,null);
                edits.sort(Comparator.comparingInt(Edit::from).reversed());StringBuilder out=new StringBuilder(source);for(var edit:edits)out.replace(edit.from,edit.to,edit.text);if(!edits.isEmpty())Files.writeString(path,out.toString());
            }
        }
    }
}
