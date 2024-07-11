package SampleApp;

import java.util.concurrent.atomic.AtomicInteger;

class OptionsQuoteHandler implements intrinio.realtime.options.OnQuote {
    public AtomicInteger quoteCount = new AtomicInteger(0);

    public void onQuote(intrinio.realtime.options.Quote quote) {
        quoteCount.incrementAndGet();
    }
}
