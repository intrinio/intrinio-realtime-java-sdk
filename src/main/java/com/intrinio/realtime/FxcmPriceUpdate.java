package com.intrinio.realtime;

import org.json.JSONObject;

import java.math.BigDecimal;

public class FxcmPriceUpdate implements Quote {
    private String time;
    private String code;
    private Float bid_price;
    private Float ask_price;

    public FxcmPriceUpdate(JSONObject message) {
        this.time = message.getString("time");
        this.code = message.getString("code");
        this.bid_price = parseToFloat(message, "bid_price");
        this.ask_price = parseToFloat(message,"ask_price");

    }

    Float parseToFloat(JSONObject message, String value) {
        if (message.isNull(value)) {
            return null;
        }
        else {
            return BigDecimal.valueOf(message.getDouble(value)).floatValue();
        }
    }

    public String getTime() {
        return time;
    }

    public String getCode() {
        return code;
    }

    public Float getBidPrice() {
        return bid_price;
    }

    public Float getAskPrice() {
        return ask_price;
    }

    @Override
    public String toString() {
        return "FxcmPriceUpdate(time: " + this.time +
                ", code: " + this.code +
                ", bid_price: " + this.bid_price +
                ", ask_price: " + this.ask_price +
                ")";
    }
}
