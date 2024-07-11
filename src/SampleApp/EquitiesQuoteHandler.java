package SampleApp;

import java.util.concurrent.ConcurrentHashMap;

class EquitiesQuoteHandler implements intrinio.realtime.equities.OnQuote {
    private final ConcurrentHashMap<String, Integer> symbols = new ConcurrentHashMap<String, Integer>();
    private int maxQuoteCount = 0;
    private intrinio.realtime.equities.Quote maxQuote;

    public int getMaxQuoteCount() {
        return maxQuoteCount;
    }

    public intrinio.realtime.equities.Quote getMaxQuote() {
        return maxQuote;
    }

    public void onQuote(intrinio.realtime.equities.Quote quote) {
        symbols.compute(quote.symbol() + ":" + quote.type(), (String key, Integer value) -> {
            if (value == null) {
                if (maxQuoteCount == 0) {
                    maxQuoteCount = 1;
                    maxQuote = quote;
                }
                return 1;
            } else {
                if (value + 1 > maxQuoteCount) {
                    maxQuoteCount = value + 1;
                    maxQuote = quote;
                }
                return value + 1;
            }
        });
    }

    public void tryLog() {
        if (maxQuoteCount > 0) {
            intrinio.realtime.equities.Client.Log("Most active quote symbol: %s:%s (%d updates)", maxQuote.symbol(), maxQuote.type(), maxQuoteCount);
            intrinio.realtime.equities.Client.Log("%s - Quote (type = %s, price = %f, size = %d)",
                    maxQuote.symbol(),
                    maxQuote.type(),
                    maxQuote.price(),
                    maxQuote.size());
        }
    }
}
