package com.intrinio.realtime;

import org.json.JSONObject;

public class QuoddBookQuote implements Quote {
    private Double askSize = null;
    private Double quoteTime = null;
    private Double rtl = null;
    private String ticker = null;
    private String askExchange = null;
    private Double askPrice4d = null;
    private String bidExchange = null;
    private Double bidPrice4d = null;
    private Double bidSize = null;
    private Integer protocolId = null;
    private String rootTicker = null;

    public QuoddBookQuote(JSONObject message) {
        if (message.has("ask_size")) {
            this.askSize = message.getDouble("ask_size");
        }
        if (message.has("quote_time")) {
            this.quoteTime = message.getDouble("quote_time");
        }
        if (message.has("rtl")) {
            this.rtl = message.getDouble("rtl");
        }
        if (message.has("ticker")) {
            this.ticker = message.getString("ticker");
        }
        if (message.has("ask_exchange")) {
            this.askExchange = message.getString("ask_exchange");
        }
        if (message.has("ask_price_4d")) {
            this.askPrice4d = message.getDouble("ask_price_4d");
        }
        if (message.has("bid_exchange")) {
            this.bidExchange = message.getString("bid_exchange");
        }
        if (message.has("bid_price_4d")) {
            this.bidPrice4d = message.getDouble("bid_price_4d");
        }
        if (message.has("bid_size")) {
            this.bidSize = message.getDouble("bid_size");
        }
        if (message.has("protocol_id")) {
            this.protocolId = message.getInt("protocol_id");
        }
        if (message.has("root_ticker")) {
            this.rootTicker = message.getString("root_ticker");
        }
    }

    public Double getAskSize() {
        return askSize;
    }

    public Double getQuoteTime() {
        return quoteTime;
    }

    public Double getRtl() {
        return rtl;
    }

    public String getTicker() {
        return ticker;
    }

    public String getAskExchange() {
        return askExchange;
    }

    public Double getAskPrice4d() {
        return askPrice4d;
    }

    public String getBidExchange() {
        return bidExchange;
    }

    public Double getBidPrice4d() {
        return bidPrice4d;
    }

    public Double getBidSize() {
        return bidSize;
    }

    public Integer getProtocolId() {
        return protocolId;
    }

    public String getRootTicker() {
        return rootTicker;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append("QuoddBookQuote(");
        if (this.ticker != null) {
            result.append("ticker: ").append(this.ticker);
        }
        if (this.quoteTime != null) {
            result.append(", quoteTime: ").append(this.quoteTime);
        }
        if (this.rtl != null) {
            result.append(", rtl: ").append(this.rtl);
        }
        if (this.ticker != null) {
            result.append(", ticker: ").append(this.ticker);
        }
        if (this.askExchange != null) {
            result.append(", askExchange: ").append(this.askExchange);
        }
        if (this.askPrice4d != null) {
            result.append(", askPrice4d: ").append(this.askPrice4d);
        }
        if (this.bidExchange != null) {
            result.append(", bidExchange: ").append(this.bidExchange);
        }
        if (this.bidPrice4d != null) {
            result.append(", bidPrice4d: ").append(this.bidPrice4d);
        }
        if (this.bidSize != null) {
            result.append(", bidSize: ").append(this.bidSize);
        }
        if (this.protocolId != null) {
            result.append(", protocolId: ").append(this.protocolId);
        }
        if (this.rootTicker != null) {
            result.append(", rootTicker: ").append(this.rootTicker);
        }
        result.append(")");

        return result.toString();
    }
}
