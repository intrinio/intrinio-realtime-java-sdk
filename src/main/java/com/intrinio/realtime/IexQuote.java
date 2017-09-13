package com.intrinio.realtime;

import org.json.JSONObject;
import java.math.BigDecimal;

public class IexQuote implements Quote {
    private String type;
    private String ticker;
    private BigDecimal price;
    private long size;
    private BigDecimal timestamp;

    public IexQuote(JSONObject message) {
        this.type = message.getString("type");
        this.ticker = message.getString("ticker");
        this.price = message.getBigDecimal("price");
        this.size = message.getLong("size");
        this.timestamp = message.getBigDecimal("timestamp");
    }

    public String getType() {
        return type;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getSize() {
        return size;
    }

    public BigDecimal getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "IexQuote(type: " + this.type +
                ", ticker: " + this.ticker +
                ", price: " + this.price +
                ", size: " + this.size +
                ", timestamp: " + this.timestamp;
    }
}
