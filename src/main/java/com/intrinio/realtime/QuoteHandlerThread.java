package com.intrinio.realtime;

public abstract class QuoteHandlerThread extends Thread {
    private RealTimeClient client;

    public void setClient(RealTimeClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        try {
            while(true) {
                Quote quote = this.client.getNextQuote();
                this.onQuote(quote);
            }
        }
        catch (InterruptedException e) { }
    }

    public abstract void onQuote(Quote quote);
}
