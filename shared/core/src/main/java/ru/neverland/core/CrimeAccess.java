package ru.neverland.core;
import java.util.UUID;
/** Optional Crime integration; installed but unavailable must not silently erase shop losses. */
public final class CrimeAccess {
    private CrimeAccess(){}
    public static int incomeBasisPoints(UUID town)throws ReflectiveOperationException{
        ApiServices.primaryThread();var connection=ApiServices.connect("NeverLandTownyCrime","ru.neverland.townycrime.api.TownyCrimeApi",1,"shopIncomeBasisPoints");
        if(connection.state()==ApiServices.State.NOT_INSTALLED)return 10000;
        if(!connection.ready())throw new IllegalStateException("Расчёт выручки временно недоступен: "+connection.state());
        Object value=connection.invoke("shopIncomeBasisPoints",new Class<?>[]{UUID.class},town);if(!(value instanceof Integer n)||n<5000||n>10000)throw new IllegalStateException("Неверная доля выручки Crime API");return n;
    }
    /** Round losses down, retaining at least one cent; no floating point or overflow. */
    public static long net(long gross,int income){if(gross<1||gross>100_000_000_000L||income<5000||income>10000)throw new IllegalArgumentException("Неверная выручка");int loss=10000-income;return gross-(gross/10000*loss+gross%10000*loss/10000);}
}
