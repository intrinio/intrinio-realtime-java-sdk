package SampleApp;

import java.util.concurrent.atomic.AtomicInteger;

class OptionsUnusualActivityHandler implements intrinio.realtime.options.OnUnusualActivity {
    public AtomicInteger blockCount = new AtomicInteger(0);
    public AtomicInteger sweepCount = new AtomicInteger(0);
    public AtomicInteger largeTradeCount = new AtomicInteger(0);
    public AtomicInteger unusualSweepCount = new AtomicInteger(0);

    public void onUnusualActivity(intrinio.realtime.options.UnusualActivity ua) {
        switch (ua.type()) {
            case BLOCK:
                blockCount.incrementAndGet();
                break;
            case SWEEP:
                sweepCount.incrementAndGet();
                break;
            case LARGE:
                largeTradeCount.incrementAndGet();
                break;
            case UNUSUAL_SWEEP:
                unusualSweepCount.incrementAndGet();
                break;
            default:
                intrinio.realtime.options.Client.Log("Sample App - Invalid UA type detected: %s", ua.type().toString());
                break;
        }
    }
}
