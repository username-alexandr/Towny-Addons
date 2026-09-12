import java.lang.reflect.*;
import java.util.*;

/** Validate the compiled contract, including inherited methods and concrete API classes. */
public final class ApiContractProbe {
    public static void main(String[] args) throws Exception {
        for (String name : args) {
            Class<?> type = Class.forName(name);
            Object api;
            if (type.isInterface()) api = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, values) -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, values) : null);
            else {
                Constructor<?> ctor = type.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
                api = ctor.newInstance(new Object[ctor.getParameterCount()]);
            }
            if (!Integer.valueOf(1).equals(type.getMethod("apiVersion").invoke(api))) throw new AssertionError(name + ": major version");
            Set<String> methods = new TreeSet<>();
            for (Method m : type.getMethods()) if (!Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class
                && !m.getDeclaringClass().getName().equals("ru.neverland.core.ApiContract")
                && !Set.of("apiVersion", "capabilities").contains(m.getName())) methods.add(m.getName());
            Object capabilities = type.getMethod("capabilities").invoke(api);
            if (!methods.equals(capabilities)) throw new AssertionError(name + ": declared " + capabilities + ", actual " + methods);
            System.out.println("API CONTRACT PASS: " + name);
        }
    }
}
