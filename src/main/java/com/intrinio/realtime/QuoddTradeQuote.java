package com.intrinio.realtime;

import org.json.JSONObject;

public class QuoddTradeQuote implements Quote {
    private Double changePrice4d = null;
    private Double dayHigh4d = null;
    private Double dayHighTime = null;
    private Double dayLow4d = null;
    private Double dayLowTime = null;
    private Double extChangePrice4d = null;
    private Double extLastPrice4d = null;
    private Double extPercentChange4d = null;
    private String extTradeExchange = null;
    private Double extTradeTime = null;
    private Double extTradeVolume = null;
    private String extUpDown = null;
    private Boolean isHalted = null;
    private Boolean isShortRestricted = null;
    private Double lastPrice4d = null;
    private Double openPrice4d = null;
    private Double openTime = null;
    private Double openVolume = null;
    private Double percentChange4d = null;
    private Double prevClose4d = null;
    private Double protocolId = null;
    private String rootTicker = null;
    private Double rtl = null;
    private String ticker = null;
    private Double totalVolume = null;
    private String tradeExchange = null;
    private Double tradeTime = null;
    private Double tradeVolume = null;
    private String upDown = null;
    private Double volumePlus = null;
    private Double vwap4d = null;

    public QuoddTradeQuote(JSONObject message) {
        if (message.has("change_price_4d")) {
            this.changePrice4d = message.getDouble("change_price_4d");
        }
        if (message.has("day_high_4d")) {
            this.dayHigh4d = message.getDouble("day_high_4d");
        }
        if (message.has("day_high_time")) {
            this.dayHighTime = message.getDouble("day_high_time");
        }
        if (message.has("day_low_4d")) {
            this.dayLow4d = message.getDouble("day_low_4d");
        }
        if (message.has("day_low_time")) {
            this.dayLowTime = message.getDouble("day_low_time");
        }
        if (message.has("ext_change_price_4d")) {
            this.extChangePrice4d = message.getDouble("ext_change_price_4d");
        }
        if (message.has("ext_last_price_4d")) {
            this.extLastPrice4d = message.getDouble("ext_last_price_4d");
        }
        if (message.has("ext_percent_change_4d")) {
            this.extPercentChange4d = message.getDouble("ext_percent_change_4d");
        }
        if (message.has("ext_trade_exchange")) {
            this.extTradeExchange = message.getString("ext_trade_exchange");
        }
        if (message.has("ext_trade_time")) {
            this.extTradeTime = message.getDouble("ext_trade_time");
        }
        if (message.has("ext_trade_volume")) {
            this.extTradeVolume = message.getDouble("ext_trade_volume");
        }
        if (message.has("ext_up_down")) {
            this.extUpDown = message.getString("ext_up_down");
        }
        if (message.has("is_halted")) {
            this.isHalted = message.getBoolean("is_halted");
        }
        if (message.has("is_short_restricted")) {
            this.isShortRestricted = message.getBoolean("is_short_restricted");
        }
        if (message.has("last_price_4d")) {
            this.lastPrice4d = message.getDouble("last_price_4d");
        }
        if (message.has("open_price_4d")) {
            this.openPrice4d = message.getDouble("open_price_4d");
        }
        if (message.has("open_time")) {
            this.openTime = message.getDouble("open_time");
        }
        if (message.has("open_volume")) {
            this.openVolume = message.getDouble("open_volume");
        }
        if (message.has("percent_change_4d")) {
            this.percentChange4d = message.getDouble("percent_change_4d");
        }
        if (message.has("prev_close_4d")) {
            this.prevClose4d = message.getDouble("prev_close_4d");
        }
        if (message.has("protocol_id")) {
            this.protocolId = message.getDouble("protocol_id");
        }
        if (message.has("root_ticker")) {
            this.rootTicker = message.getString("root_ticker");
        }
        if (message.has("rtl")) {
            this.rtl = message.getDouble("rtl");
        }
        if (message.has("ticker")) {
            this.ticker = message.getString("ticker");
        }
        if (message.has("total_volume")) {
            this.totalVolume = message.getDouble("total_volume");
        }
        if (message.has("trade_exchange")) {
            this.tradeExchange = message.getString("trade_exchange");
        }
        if (message.has("trade_time")) {
            this.tradeTime = message.getDouble("trade_time");
        }
        if (message.has("trade_volume")) {
            this.tradeVolume = message.getDouble("trade_volume");
        }
        if (message.has("up_down")) {
            this.upDown = message.getString("up_down");
        }
        if (message.has("volume_plus")) {
            this.volumePlus = message.getDouble("volume_plus");
        }
        if (message.has("vwap_4d")) {
            this.vwap4d = message.getDouble("vwap_4d");
        }
    }

    public Double getChangePrice4d() {
        return changePrice4d;
    }

    public Double getDayHigh4d() {
        return dayHigh4d;
    }

    public Double getDayHighTime() {
        return dayHighTime;
    }

    public Double getDayLow4d() {
        return dayLow4d;
    }

    public Double getDayLowTime() {
        return dayLowTime;
    }

    public Double getExtChangePrice4d() {
        return extChangePrice4d;
    }

    public Double getExtLastPrice4d() {
        return extLastPrice4d;
    }

    public Double getExtPercentChange4d() {
        return extPercentChange4d;
    }

    public String getExtTradeExchange() {
        return extTradeExchange;
    }

    public Double getExtTradeTime() {
        return extTradeTime;
    }

    public Double getExtTradeVolume() {
        return extTradeVolume;
    }

    public String getExtUpDown() {
        return extUpDown;
    }

    public Boolean getHalted() {
        return isHalted;
    }

    public Boolean getShortRestricted() {
        return isShortRestricted;
    }

    public Double getLastPrice4d() {
        return lastPrice4d;
    }

    public Double getOpenPrice4d() {
        return openPrice4d;
    }

    public Double getOpenTime() {
        return openTime;
    }

    public Double getOpenVolume() {
        return openVolume;
    }

    public Double getPercentChange4d() {
        return percentChange4d;
    }

    public Double getPrevClose4d() {
        return prevClose4d;
    }

    public Double getProtocolId() {
        return protocolId;
    }

    public String getRootTicker() {
        return rootTicker;
    }

    public Double getRtl() {
        return rtl;
    }

    public String getTicker() {
        return ticker;
    }

    public Double getTotalVolume() {
        return totalVolume;
    }

    public String getTradeExchange() {
        return tradeExchange;
    }

    public Double getTradeTime() {
        return tradeTime;
    }

    public Double getTradeVolume() {
        return tradeVolume;
    }

    public String getUpDown() {
        return upDown;
    }

    public Double getVolumePlus() {
        return volumePlus;
    }

    public Double getVwap4d() {
        return vwap4d;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append("QuoddTradeQuote(");
        if (this.ticker != null) {
            result.append("ticker: ").append(this.ticker);
        }
        if (this.changePrice4d != null) {
            result.append(", changePrice4d: ").append(this.changePrice4d);
        }
        if (this.dayHigh4d != null) {
            result.append(", dayHigh4d: ").append(this.dayHigh4d);
        }
        if (this.dayHighTime != null) {
            result.append(", dayHighTime: ").append(this.dayHighTime);
        }
        if (this.dayLow4d != null) {
            result.append(", dayLow4d: ").append(this.dayLow4d);
        }
        if (this.dayLowTime != null) {
            result.append(", dayLowTime: ").append(this.dayLowTime);
        }
        if (this.extChangePrice4d != null) {
            result.append(", extChangePrice4d: ").append(this.extChangePrice4d);
        }
        if (this.extLastPrice4d != null) {
            result.append(", extLastPrice4d: ").append(this.extLastPrice4d);
        }
        if (this.extPercentChange4d != null) {
            result.append(", extPercentChange4d: ").append(this.extPercentChange4d);
        }
        if (this.extTradeExchange != null) {
            result.append(", extTradeExchange: ").append(this.extTradeExchange);
        }
        if (this.extTradeTime != null) {
            result.append(", extTradeTime: ").append(this.extTradeTime);
        }
        if (this.extTradeVolume != null) {
            result.append(", extTradeVolume: ").append(this.extTradeVolume);
        }
        if (this.extUpDown != null) {
            result.append(", extUpDown: ").append(this.extUpDown);
        }
        if (this.isHalted != null) {
            result.append(", isHalted: ").append(this.isHalted);
        }
        if (this.isShortRestricted != null) {
            result.append(", isShortRestricted: ").append(this.isShortRestricted);
        }
        if (this.lastPrice4d != null) {
            result.append(", lastPrice4d: ").append(this.lastPrice4d);
        }
        if (this.openPrice4d != null) {
            result.append(", openPrice4d: ").append(this.openPrice4d);
        }
        if (this.openTime != null) {
            result.append(", openTime: ").append(this.openTime);
        }
        if (this.openVolume != null) {
            result.append(", openVolume: ").append(this.openVolume);
        }
        if (this.percentChange4d != null) {
            result.append(", percentChange4d: ").append(this.percentChange4d);
        }
        if (this.prevClose4d != null) {
            result.append(", prevClose4d: ").append(this.prevClose4d);
        }
        if (this.protocolId != null) {
            result.append(", protocolId: ").append(this.protocolId);
        }
        if (this.rootTicker != null) {
            result.append(", rootTicker: ").append(this.rootTicker);
        }
        if (this.rtl != null) {
            result.append(", rtl: ").append(this.rtl);
        }
        if (this.totalVolume != null) {
            result.append(", totalVolume: ").append(this.totalVolume);
        }
        if (this.tradeExchange != null) {
            result.append(", tradeExchange: ").append(this.tradeExchange);
        }
        if (this.tradeTime != null) {
            result.append(", tradeTime: ").append(this.tradeTime);
        }
        if (this.tradeVolume != null) {
            result.append(", tradeVolume: ").append(this.tradeVolume);
        }
        if (this.upDown != null) {
            result.append(", upDown: ").append(this.upDown);
        }
        if (this.volumePlus != null) {
            result.append(", volumePlus: ").append(this.volumePlus);
        }
        if (this.vwap4d != null) {
            result.append(", vwap4d: ").append(this.vwap4d);
        }
        result.append(")");

        return result.toString();
    }
}
