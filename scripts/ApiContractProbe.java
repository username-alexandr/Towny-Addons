import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Compiled metadata and additive compatibility against the previous published API. */
public final class ApiContractProbe {
    static void referenced(Type type, Set<String> signatures, Set<Class<?>> visited) {
        if(type instanceof ParameterizedType p) { referenced(p.getRawType(),signatures,visited); for(Type t:p.getActualTypeArguments())referenced(t,signatures,visited); }
        if(!(type instanceof Class<?> c) || !c.getName().startsWith("ru.neverland.") || !visited.add(c))return;
        if(c.isEnum())for(Object value:c.getEnumConstants())signatures.add("enum "+c.getName()+" "+((Enum<?>)value).name());
        if(c.isRecord()) {
            signatures.add("record "+c.getName()+" "+Arrays.toString(Arrays.stream(c.getRecordComponents()).map(x->x.getName()+":"+x.getGenericType().getTypeName()).toArray()));
            for(RecordComponent x:c.getRecordComponents())referenced(x.getGenericType(),signatures,visited);
        }
    }
    static Set<String> signatures(Class<?> type) {
        Set<String> out=new TreeSet<>(); Set<Class<?>> visited=new HashSet<>();
        for(Method m:type.getMethods())if(m.getDeclaringClass()!=Object.class) {
            out.add((Modifier.isStatic(m.getModifiers())?"static ":"")+m.getName()+"("+String.join(",",Arrays.stream(m.getGenericParameterTypes()).map(Type::getTypeName).toList())+"):"+m.getGenericReturnType().getTypeName()+" throws ["+String.join(",",Arrays.stream(m.getGenericExceptionTypes()).map(Type::getTypeName).sorted().toList())+"]");
            if(m.isDefault())out.add("default "+m.getName()+"("+String.join(",",Arrays.stream(m.getParameterTypes()).map(Class::getName).toList())+")");
            referenced(m.getGenericReturnType(),out,visited); for(Type p:m.getGenericParameterTypes())referenced(p,out,visited);
        }
        return out;
    }
    public static void main(String[] args) throws Exception {
        boolean write=args.length>0 && args[0].equals("--write-baseline");
        Path baseline=args.length>1 && args[0].equals("--baseline")?Path.of(args[1]):null;
        int offset=write?1:baseline!=null?2:0;
        Map<String,Set<String>> prior=new HashMap<>();
        if(baseline!=null)for(String line:Files.readAllLines(baseline))if(!line.isBlank()&&!line.startsWith("#")) {
            String[] fields=line.split("\t",2);prior.computeIfAbsent(fields[0],k->new TreeSet<>()).add(fields[1]);
        }
        for(String name:Arrays.copyOfRange(args,offset,args.length)) {
            Class<?> type=Class.forName(name); Object api;
            if(type.isInterface())api=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},
                (proxy,method,values)->method.isDefault()?InvocationHandler.invokeDefault(proxy,method,values):null);
            else {Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);api=ctor.newInstance(new Object[ctor.getParameterCount()]);}
            if(!Integer.valueOf(1).equals(type.getMethod("apiVersion").invoke(api)))throw new AssertionError(name+": major version (v1 baseline requires deliberate migration)");
            Set<String> methods=new TreeSet<>();
            for(Method m:type.getMethods())if(!Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass()!=Object.class
                && !m.getDeclaringClass().getName().equals("ru.neverland.core.ApiContract") && !Set.of("apiVersion","capabilities").contains(m.getName()))methods.add(m.getName());
            Object capabilities=type.getMethod("capabilities").invoke(api);
            if(!methods.equals(capabilities))throw new AssertionError(name+": declared "+capabilities+", actual "+methods);
            Set<String> current=signatures(type);
            for(String cap:methods)current.add("capability "+cap);
            if(write)for(String signature:current)System.out.println(name+"\t"+signature);
            else {
                if(baseline!=null) {
                    if(!prior.containsKey(name))throw new AssertionError("Missing published baseline for "+name);
                    Set<String> missing=new TreeSet<>(prior.get(name));missing.removeAll(current);
                    if(!missing.isEmpty())throw new AssertionError(name+": incompatible v1 API: "+missing);
                }
                System.out.println("API CONTRACT PASS: "+name+" ("+current.size()+" signatures/capabilities)");
            }
        }
    }
}
