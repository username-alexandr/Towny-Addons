package ru.neverland.core;

import java.util.UUID;

/** Only an absent optional provider is neutral. Installed failures suspend the consuming calculation. */
public final class SeasonsAccess {
    private SeasonsAccess() { }
    public static double production(UUID town,String building) throws ReflectiveOperationException {
        return value(ApiServices.connect("NeverLandTownySeasons","ru.neverland.townyseasons.api.TownySeasonsApi",1,"productionMultiplier"),"productionMultiplier",town,building,.1,2);
    }
    public static double eventWeight(UUID town,String mode) throws ReflectiveOperationException {
        return value(ApiServices.connect("NeverLandTownySeasons","ru.neverland.townyseasons.api.TownySeasonsApi",1,"eventWeight"),"eventWeight",town,mode,0,10);
    }
    public static double eventProduction(UUID town,String building) throws ReflectiveOperationException {
        return value(ApiServices.connect("NeverLandTownyEvents","ru.neverland.mintevents.api.MintTownyEventsApi",1,"productionMultiplier"),"productionMultiplier",town,building,.1,1);
    }
    private static double value(ApiServices.Connection connection,String method,UUID town,String key,double min,double max) throws ReflectiveOperationException {
        ApiServices.primaryThread();
        if(connection.state()==ApiServices.State.NOT_INSTALLED)return 1;
        Object result=connection.invoke(method,new Class<?>[]{UUID.class,String.class},town,key);
        if(!(result instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)
            throw new IllegalStateException("Неверный коэффициент "+method);
        return n.doubleValue();
    }
    public static long output(long amount,double factor,long maximum) {
        if(amount<0||maximum<0||!Double.isFinite(factor)||factor<.01||factor>2)throw new IllegalArgumentException("Неверный сезонный выпуск");
        return java.math.BigDecimal.valueOf(amount).multiply(java.math.BigDecimal.valueOf(factor))
                .min(java.math.BigDecimal.valueOf(maximum)).setScale(0,java.math.RoundingMode.DOWN).longValueExact();
    }
}
