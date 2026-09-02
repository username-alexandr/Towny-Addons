package ru.neverland.mintespionage;

import ru.neverland.mintespionage.util.EspionageMath;

public final class EspionageMathSmoke {
    public static void main(String[] args) {
        assertNear(EspionageMath.chance(.70,2,.04,3,.05,.08,.05,.95),.55);
        assertNear(EspionageMath.detection(.20,2,.07,.11,.02,.95),.45);
        assertNear(EspionageMath.chance(.99,5,.10,0,0,0,.05,.95),.95);
        if(EspionageMath.duration(100_000,3,.05)!=85_000) throw new AssertionError("duration");
        System.out.println("EspionageMathSmoke OK");
    }
    private static void assertNear(double actual,double expected){if(Math.abs(actual-expected)>0.00001)throw new AssertionError(actual+" != "+expected);}
}
