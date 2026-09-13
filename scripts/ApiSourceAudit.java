import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import javax.lang.model.element.Modifier;
import java.nio.file.*;
import java.util.*;

/** Parse Java syntax (including comments, constants and local forwarding helpers), without dependencies. */
public final class ApiSourceAudit {
    record Method(ClassInfo owner, MethodTree tree) {}
    record Call(Method owner, MethodInvocationTree tree, long line) {}
    static final class ClassInfo {
        String name, path; ClassTree tree;
        Map<String,ExpressionTree> fields = new HashMap<>();
        List<Method> methods = new ArrayList<>();
        List<Call> calls = new ArrayList<>();
    }
    static final List<ClassInfo> classes = new ArrayList<>();
    static final Set<String> requests = new TreeSet<>(), unresolved = new TreeSet<>();
    static String q(Object s) { return "\""+String.valueOf(s).replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\""; }
    static String list(Collection<String> values) { return "["+String.join(",",values.stream().map(ApiSourceAudit::q).toList())+"]"; }
    static String name(MethodInvocationTree call) { String n=call.getMethodSelect().toString(); return n.substring(n.lastIndexOf('.')+1); }
    static boolean service(MethodInvocationTree call) { return call.getMethodSelect().toString().matches("(?:ru\\.neverland\\.core\\.)?ApiServices\\.(connect|require|call)"); }
    static String value(ExpressionTree e, Method m, Map<String,String> env, Set<String> seen) {
        if(e==null)return null;
        if(e instanceof LiteralTree l)return l.getValue()==null?null:String.valueOf(l.getValue());
        if(e instanceof ParenthesizedTree p)return value(p.getExpression(),m,env,seen);
        if(e instanceof IdentifierTree id) {
            String n=id.getName().toString(); if(env.containsKey(n))return env.get(n);
            if(!seen.add(n))return null;
            ExpressionTree init=m.owner.fields.get(n);
            if(init==null && m.tree.getBody()!=null) {
                final ExpressionTree[] local={null};
                new TreeScanner<Void,Void>() { public Void visitVariable(VariableTree v,Void p) { if(v.getName().contentEquals(n))local[0]=v.getInitializer(); return super.visitVariable(v,p); } }.scan(m.tree.getBody(),null);
                init=local[0];
            }
            return value(init,m,env,seen);
        }
        // A configured provider is checked at its shipped default; runtime covers overrides.
        if(e instanceof MethodInvocationTree c && name(c).equals("getString") && c.getArguments().size()==2)
            return value(c.getArguments().get(1),m,env,seen);
        if(e instanceof ConditionalExpressionTree c) {
            String a=value(c.getTrueExpression(),m,env,new HashSet<>(seen));
            String b=value(c.getFalseExpression(),m,env,new HashSet<>(seen));
            if(Objects.equals(a,b))return a;
            if(a!=null && a.startsWith("NeverLandTowny"))return a; // legacy plugin alias
        }
        return null;
    }
    static String value(ExpressionTree e,Method m,Map<String,String> env) {return value(e,m,env,new HashSet<>());}
    static boolean arity(Method m, List<? extends ExpressionTree> args) { var p=m.tree.getParameters(); return p.size()==args.size() || !p.isEmpty() && p.get(p.size()-1).toString().contains("...") && args.size()>=p.size()-1; }
    static void walk(Method m, Map<String,String> env, Set<Method> stack) {
        if(!stack.add(m))return;
        for(Call call:m.owner.calls)if(call.owner==m) {
            List<? extends ExpressionTree> a=call.tree.getArguments();
            if(service(call.tree)) {
                String op=name(call.tree); int start=op.equals("connect")?3:2;
                String provider=value(a.get(0),m,env), contract=value(a.get(1),m,env), major=op.equals("connect")?value(a.get(2),m,env):"1";
                List<String> caps=new ArrayList<>(); boolean dynamic=false;
                int end=op.equals("call")?3:a.size();
                for(int i=start;i<end;i++) {String cap=value(a.get(i),m,env); if(cap==null)dynamic=true;else caps.add(cap);}
                String location=m.owner.path+":"+call.line;
                if(provider!=null && contract!=null && major!=null && !dynamic)
                    requests.add("{\"source\":"+q(location)+",\"plugin\":"+q(provider)+",\"contract\":"+q(contract)+",\"major\":"+major+",\"capabilities\":"+list(caps)+"}");
                else unresolved.add(location);
            } else {
                String select=call.tree.getMethodSelect().toString();
                if(select.contains(".") && !select.startsWith("this.") && !select.startsWith(m.owner.tree.getSimpleName()+"."))continue;
                for(Method target:m.owner.methods)if(target.tree.getName().contentEquals(name(call.tree)) && arity(target,a)) {
                    Map<String,String> child=new HashMap<>();
                    for(int i=0;i<Math.min(a.size(),target.tree.getParameters().size());i++)child.put(target.tree.getParameters().get(i).getName().toString(),value(a.get(i),m,env));
                    walk(target,child,new HashSet<>(stack));
                }
            }
        }
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();
        List<Path> paths=new ArrayList<>();
        for(String folder:List.of("modules","shared"))try(var walk=Files.walk(root.resolve(folder))) {
            walk.filter(p->p.toString().endsWith(".java") && (p.toString().contains("/src/main/java/") || p.toString().contains("/src/test/java/"))).sorted().forEach(paths::add);
        }
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(null,null,null)) {
            DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
            JavacTask task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none"),null,fm.getJavaFileObjectsFromPaths(paths));
            Iterable<? extends CompilationUnitTree> units=task.parse(); Trees trees=Trees.instance(task);
            for(CompilationUnitTree unit:units) {
                String relative=root.relativize(Path.of(unit.getSourceFile().toUri())).toString();
                new TreeScanner<Void,Void>() {
                    ClassInfo current; Method method;
                    public Void visitClass(ClassTree node,Void p) {
                        ClassInfo previous=current;Method prevMethod=method;
                        current=new ClassInfo();current.tree=node;current.path=relative;
                        current.name=(previous==null?(unit.getPackageName()==null?"":unit.getPackageName()+"."):previous.name+"$")+node.getSimpleName();
                        for(Tree member:node.getMembers())if(member instanceof VariableTree v)current.fields.put(v.getName().toString(),v.getInitializer());
                        classes.add(current);method=null;super.visitClass(node,p);current=previous;method=prevMethod;return null;
                    }
                    public Void visitMethod(MethodTree node,Void p) {Method previous=method;method=new Method(current,node);current.methods.add(method);super.visitMethod(node,p);method=previous;return null;}
                    public Void visitMethodInvocation(MethodInvocationTree node,Void p) {
                        if(method!=null)current.calls.add(new Call(method,node,unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit,node))));
                        return super.visitMethodInvocation(node,p);
                    }
                }.scan(unit,null);
            }
            if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))throw new IllegalStateException(diagnostics.getDiagnostics().toString());
        }
        List<String> contracts=new ArrayList<>(), tests=new ArrayList<>();
        for(ClassInfo c:classes) {
            if(c.path.contains("/src/test/java/")) {
                for(Method m:c.methods)if(m.tree.getName().contentEquals("main") && m.tree.getModifiers().getFlags().containsAll(Set.of(Modifier.PUBLIC,Modifier.STATIC))
                    && m.tree.getReturnType().toString().equals("void") && m.tree.getParameters().size()==1
                    && Set.of("String[]","java.lang.String[]").contains(m.tree.getParameters().get(0).getType().toString()))
                    tests.add("{\"source\":"+q(c.path)+",\"class\":"+q(c.name)+"}");
                continue;
            }
            boolean api=c.tree.getImplementsClause().stream().anyMatch(t->t.toString().matches("(?:ru\\.neverland\\.core\\.)?ApiContract"));
            if(api) {
                Set<String> caps=new TreeSet<>();
                for(Method m:c.methods)if(m.tree.getName().contentEquals("capabilities"))
                    for(Call call:c.calls)if(call.owner==m && name(call.tree).equals("of"))for(ExpressionTree e:call.tree.getArguments()) {String v=value(e,m,Map.of());if(v!=null)caps.add(v);}
                contracts.add("{\"source\":"+q(c.path)+",\"contract\":"+q(c.name)+",\"major\":1,\"capabilities\":"+list(caps)+"}");
            }
            for(Method m:c.methods)if(c.calls.stream().noneMatch(call->call.owner!=m && arity(m,call.tree.getArguments()) && !service(call.tree) && (call.tree.getMethodSelect().toString().equals(m.tree.getName().toString()) || call.tree.getMethodSelect().toString().equals("this."+m.tree.getName()))))walk(m,new HashMap<>(),new HashSet<>());
        }
        Set<String> sites=new TreeSet<>();
        for(ClassInfo c:classes)if(!c.path.contains("/src/test/java/"))for(Call call:c.calls)if(service(call.tree))sites.add(c.path+":"+call.line);
        System.out.println("{\"sites\":"+list(sites)+",\"contracts\":["+String.join(",",contracts)+"],\"consumers\":["+String.join(",",requests)+"],\"dynamic_sites\":"+list(unresolved)+",\"tests\":["+String.join(",",tests)+"]}");
    }
}
