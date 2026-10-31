package cc.crafto.ui.server;
/** Optional versioned integrations use public methods only; no private-field access. */
final class PublicMethods {
    static Object call(Object object,String name)throws ReflectiveOperationException{return object.getClass().getMethod(name).invoke(object);}
    static Object call(Object object,String name,Class<?> type,Object argument)throws ReflectiveOperationException{return object.getClass().getMethod(name,type).invoke(object,argument);}
    static Object staticCall(ClassLoader loader,String type,String name)throws ReflectiveOperationException{return Class.forName(type,true,loader).getMethod(name).invoke(null);}
}
