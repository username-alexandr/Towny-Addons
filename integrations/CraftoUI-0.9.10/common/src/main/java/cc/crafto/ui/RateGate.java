package cc.crafto.ui;

/** Thread-safe bounded admission before submitting work to an entity scheduler. */
public final class RateGate {
    private final double capacity, perSecond;
    private double tokens;
    private long last;
    public RateGate(double capacity,double perSecond,long now) { this.capacity=capacity;this.perSecond=perSecond;tokens=capacity;last=now; }
    public synchronized boolean allow(long now) {
        tokens=Math.min(capacity,tokens+Math.max(0,now-last)/1_000_000_000d*perSecond);last=now;
        if(tokens<1)return false; tokens--;return true;
    }
}
