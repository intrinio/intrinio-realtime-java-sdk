package SampleApp;

import java.util.concurrent.ConcurrentHashMap;

class EquitiesTradeHandler implements intrinio.realtime.equities.OnTrade {
    private final ConcurrentHashMap<String, Integer> symbols = new ConcurrentHashMap<String, Integer>();
    private int maxTradeCount = 0;
    private intrinio.realtime.equities.Trade maxTrade;

    public int getMaxTradeCount() {
        return maxTradeCount;
    }

    public intrinio.realtime.equities.Trade getMaxTrade() {
        return maxTrade;
    }

    public void onTrade(intrinio.realtime.equities.Trade trade) {
        symbols.compute(trade.symbol(), (String key, Integer value) -> {
            if (value == null) {
                if (maxTradeCount == 0) {
                    maxTradeCount = 1;
                    maxTrade = trade;
                }
                return 1;
            } else {
                if (value + 1 > maxTradeCount) {
                    maxTradeCount = value + 1;
                    maxTrade = trade;
                }
                return value + 1;
            }
        });
    }

    public void tryLog() {
        if (maxTradeCount > 0) {
            intrinio.realtime.equities.Client.Log("Most active trade symbol: %s (%d updates)", maxTrade.symbol(), maxTradeCount);
            intrinio.realtime.equities.Client.Log("%s - Trade (price = %f, size = %d, time = %s)",
                    maxTrade.symbol(),
                    maxTrade.price(),
                    maxTrade.size(),
                    maxTrade.timestamp());
        }
    }
}
