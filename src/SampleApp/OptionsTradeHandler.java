package SampleApp;

import java.util.concurrent.atomic.AtomicInteger;

class OptionsTradeHandler implements intrinio.realtime.options.OnTrade {

    public AtomicInteger tradeCount = new AtomicInteger(0);

    public void onTrade(intrinio.realtime.options.Trade trade) {
        tradeCount.incrementAndGet();
    }
}
