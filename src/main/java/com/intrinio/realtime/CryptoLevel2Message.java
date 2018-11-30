package com.intrinio.realtime;

import org.json.JSONObject;
import java.math.BigDecimal;

public class CryptoLevel2Message implements Quote {
    private String type;

    private String pair_name;
    private String pair_code;
    private String exchange_name;
    private String exchange_code;

    private String side;
    private Float price;
    private Float size;

    public CryptoLevel2Message(JSONObject message) {
        this.type = message.getString("type");

        this.pair_name = message.getString("pair_name");
        this.pair_code = message.getString("pair_code");

        this.exchange_name = message.getString("exchange_name");
        this.exchange_code = message.getString("exchange_code");

        this.side = message.optString("side", null);
        this.price = parseToFloat(message,"price");
        this.size = parseToFloat(message,"size");
    }

    Float parseToFloat(JSONObject message, String value) {
        if (message.isNull(value)) {
            return null;
        }
        else {
            return BigDecimal.valueOf(message.getDouble(value)).floatValue();
        }
    }

    public String getType() {
        return type;
    }

    public String getPairName() {
        return pair_name;
    }

    public String getPairCode() {
        return pair_code;
    }

    public String getExchangeCode() {
        return exchange_code;
    }

    public String getExchangeName() {
        return exchange_name;
    }

    // Book updates
    public String getSide() {
        return side;
    }

    public Float getPrice() {
        return price;
    }

    public Float getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "CryptoLevel2Message(type: " + this.type +
                ", pair_name: " + this.pair_name +
                ", pair_code: " + this.pair_code +
                ", exchange_name: " + this.exchange_name +
                ", exchange_code: " + this.exchange_code +
                ", side: " + this.side +
                ", price: " + this.price +
                ", size: " + this.size +
                ")";
    }
}
