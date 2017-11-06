package com.intrinio.realtime;

import org.json.JSONObject;
import java.math.BigDecimal;

public class IexQuote implements Quote {
    private String type;
    private String ticker;
    private Double price;
    private long size;
    private Double timestamp;

    public IexQuote(JSONObject message) {
        this.type = message.getString("type");
        this.ticker = message.getString("ticker");
        this.price = message.getDouble("price");
        this.size = message.getLong("size");
        this.timestamp = message.getDouble("timestamp");
    }

    public String getType() {
        return type;
    }

    public String getTicker() {
        return ticker;
    }

    public Double getPrice() {
        return price;
    }

    public long getSize() {
        return size;
    }

    public Double getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "IexQuote(type: " + this.type +
                ", ticker: " + this.ticker +
                ", price: " + this.price +
                ", size: " + this.size +
                ", timestamp: " + this.timestamp +
                ")";
    }
}
