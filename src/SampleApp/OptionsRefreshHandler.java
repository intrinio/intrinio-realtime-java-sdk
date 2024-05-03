package SampleApp;

import java.util.concurrent.atomic.AtomicInteger;

class OptionsRefreshHandler implements intrinio.realtime.options.OnRefresh {
    public AtomicInteger rCount = new AtomicInteger(0);

    public void onRefresh(intrinio.realtime.options.Refresh r) {
        rCount.incrementAndGet();
    }
}
