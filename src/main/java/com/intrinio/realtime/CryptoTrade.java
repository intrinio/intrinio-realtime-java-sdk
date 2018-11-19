package com.intrinio.realtime;

import org.json.JSONObject;
import java.math.BigDecimal;

public class CryptoTrade implements Quote {
    private String type;

    private String pair_name;
    private String pair_code;
    private String exchange_name;
    private String exchange_code;

    private String last_updated;
    private Float bid;
    private Float bid_size;
    private Float ask;
    private Float ask_size;
    private Float change;
    private Float change_percent;
    private Float volume;
    private Float open;
    private Float high;
    private Float low;
    private String last_trade_time;
    private String last_trade_side;
    private Float last_trade_price;
    private Float last_trade_size;

    public CryptoTrade(JSONObject message) {
        this.type = message.getString("type");

        this.pair_name = message.getString("pair_name");
        this.pair_code = message.getString("pair_code");

        this.exchange_name = message.getString("exchange_name");
        this.exchange_code = message.getString("exchange_code");

        this.last_updated = message.optString("last_updated");
        this.bid = parseToFloat(message, "bid");
        this.bid_size = parseToFloat(message,"bid_size");
        this.ask = parseToFloat(message,"ask");
        this.ask_size = parseToFloat(message,"ask_size");
        this.change = parseToFloat(message,"change");
        this.change_percent = parseToFloat(message,"change_percent");
        this.volume = parseToFloat(message,"volume");
        this.open = parseToFloat(message,"open");
        this.high = parseToFloat(message,"high");
        this.low = parseToFloat(message,"low");
        this.last_trade_time = message.optString("last_trade_time", null);
        this.last_trade_side = message.optString("last_trade_side", null);
        this.last_trade_price = parseToFloat(message,"last_trade_price");
        this.last_trade_size = parseToFloat(message,"last_trade_size");

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

    public String getLastUpdated() {
        return last_updated;
    }

    public Float getBid() {
        return bid;
    }

    public Float getBidSize() {
        return bid_size;
    }

    public Float getAsk() {
        return ask;
    }

    public Float getAskSize() {
        return ask_size;
    }

    public Float getChange() {
        return change;
    }

    public Float getChangePercent() {
        return change_percent;
    }

    public Float getVolume() {
        return volume;
    }

    public Float getOpen() {
        return open;
    }

    public Float getHigh() {
        return high;
    }

    public Float getLow() {
        return low;
    }

    public String getLastTradeTime() {
        return last_trade_time;
    }

    public String getLastTradeSide() {
        return last_trade_side;
    }

    public Float getLastTradePrice() {
        return last_trade_price;
    }

    public Float getLastTradeSize() {
        return last_trade_size;
    }

    @Override
    public String toString() {
        return "CryptoTrade(type: " + this.type +
                ", pair_name: " + this.pair_name +
                ", pair_code: " + this.pair_code +
                ", exchange_name: " + this.exchange_name +
                ", exchange_code: " + this.exchange_code +
                ", last_updated: " + this.last_updated +
                ", bid: " + this.bid +
                ", bid_size: " + this.bid_size +
                ", ask: " + this.ask +
                ", ask_size: " + this.ask_size +
                ", change: " + this.change +
                ", change_percent: " + this.change_percent +
                ", volume: " + this.volume +
                ", open: " + this.open +
                ", high: " + this.high +
                ", low: " + this.low +
                ", last_trade_time: " + this.last_trade_time +
                ", last_trade_side: " + this.last_trade_side +
                ", last_trade_price: " + this.last_trade_price +
                ", last_trade_size: " + this.last_trade_size +
                ")";
    }
}
