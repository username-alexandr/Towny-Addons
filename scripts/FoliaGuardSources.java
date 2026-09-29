import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.Modifier;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Source migration tool: serializes suite entry points without changing API signatures.
 * The monitor protects the suite ledger, not Minecraft state. Ownership handoffs are explicit.
 */
public final class FoliaGuardSources {
    record Edit(int at,String text) {}
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        var paths=new ArrayList<Path>();
        for(String directory:List.of("modules","shared"))try(var stream=Files.walk(root.resolve(directory))){
            stream.filter(p->p.toString().contains("/src/main/java/")&&p.toString().endsWith(".java"))
                .filter(p->!Set.of("SuiteState.java","SuiteScheduler.java","SuiteRunnable.java","WorldTasks.java","AuditQuery.java","AuditCategory.java","AuditRecord.java","AuditJournal.java","AuditTrail.java","PaymentEvidence.java","Money.java","EconomyListener.java").contains(p.getFileName().toString())).forEach(paths::add);
        }
        var compiler=ToolProvider.getSystemJavaCompiler();
        try(var manager=compiler.getStandardFileManager(null,null,null)) {
            var task=(JavacTask)compiler.getTask(null,manager,null,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(paths));
            var units=task.parse();var positions=Trees.instance(task).getSourcePositions();int[] count={0};
            for(var unit:units){
                Path path=Path.of(unit.getSourceFile().toUri());String source=Files.readString(path);var edits=new ArrayList<Edit>();
                new TreeScanner<Void,Void>() {
                    @Override public Void visitMethod(MethodTree method,Void unused) {
                        var body=method.getBody();
                        boolean entry=method.getModifiers().getFlags().contains(Modifier.PUBLIC)||method.getModifiers().getFlags().contains(Modifier.PROTECTED)
                            ||method.getModifiers().getAnnotations().stream().anyMatch(a->a.getAnnotationType().toString().endsWith("EventHandler"));
                        if(body!=null&&method.getReturnType()!=null&&entry&&!Set.of("withdrew","deposited","observeTransaction").contains(method.getName().toString())&&!(path.getFileName().toString().equals("BankAudit.java")&&Set.of("transfer","bankCommand").contains(method.getName().toString()))) {
                            int start=(int)positions.getStartPosition(unit,body),end=(int)positions.getEndPosition(unit,body);
                            if(!source.substring(start,Math.min(end,start+90)).contains("synchronized (ru.neverland.core.SuiteState.lock())")) {
                                edits.add(new Edit(start+1," synchronized (ru.neverland.core.SuiteState.lock()) {"));
                                edits.add(new Edit(end-1,"} "));count[0]++;
                            }
                        }
                        return super.visitMethod(method,unused);
                    }
                }.scan(unit,null);
                edits.sort(Comparator.comparingInt(Edit::at).reversed());var out=new StringBuilder(source);
                for(var edit:edits)out.insert(edit.at(),edit.text());
                // All externally visible monitors use the same ordering. Private helpers stay under their entry-point lock.
                String result=out.toString().replaceAll("((?:public|protected)\\s+(?:static\\s+)?)synchronized\\s+", "$1");
                if(!source.equals(result))Files.writeString(path,result);
            }
            System.out.println("Guarded entry points: "+count[0]);
        }
    }
}
