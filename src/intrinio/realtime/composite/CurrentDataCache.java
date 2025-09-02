package intrinio.realtime.composite;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class CurrentDataCache implements DataCache {
    private final ConcurrentHashMap<String, SecurityData> securities = new ConcurrentHashMap<>();
    private final Map<String, SecurityData> readonlySecurities = Collections.unmodifiableMap(securities);
    private final ConcurrentHashMap<String, Double> supplementaryData = new ConcurrentHashMap<>();
    private final Map<String, Double> readonlySupplementaryData = Collections.unmodifiableMap(supplementaryData);

    private OnSupplementalDatumUpdated supplementalDatumUpdatedCallback;
    private OnSecuritySupplementalDatumUpdated securitySupplementalDatumUpdatedCallback;
    private OnOptionsContractSupplementalDatumUpdated optionsContractSupplementalDatumUpdatedCallback;

    private OnOptionsContractGreekDataUpdated optionsContractGreekDataUpdatedCallback;

    private OnEquitiesTradeUpdated equitiesTradeUpdatedCallback;
    private OnEquitiesQuoteUpdated equitiesQuoteUpdatedCallback;

    private OnOptionsTradeUpdated optionsTradeUpdatedCallback;
    private OnOptionsQuoteUpdated optionsQuoteUpdatedCallback;
    private OnOptionsRefreshUpdated optionsRefreshUpdatedCallback;
    private OnOptionsUnusualActivityUpdated optionsUnusualActivityUpdatedCallback;

    public CurrentDataCache() {
    }

    public Double getSupplementaryDatum(String key) {
        return supplementaryData.getOrDefault(key, null);
    }

    public boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update) {
        Double newValue = supplementaryData.compute(key, (k, oldValue) -> update.supplementalDatumUpdate(k, oldValue, datum));
        boolean result = java.util.Objects.equals(datum, newValue);
        if (result && supplementalDatumUpdatedCallback != null) {
            try {
                supplementalDatumUpdatedCallback.onSupplementalDatumUpdated(key, datum, this);
            } catch (Exception e) {
                Log("Error in OnSupplementalDatumUpdated Callback: " + e.getMessage());
            }
        }
        return result;
    }

    public Map<String, Double> getAllSupplementaryData() {
        return readonlySupplementaryData;
    }

    public Double getSecuritySupplementalDatum(String tickerSymbol, String key) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getSupplementaryDatum(key) : null;
    }

    public boolean setSecuritySupplementalDatum(String tickerSymbol, String key, Double datum, SupplementalDatumUpdate update) {
        if (tickerSymbol != null && !tickerSymbol.trim().isEmpty()) {
            SecurityData securityData = securities.computeIfAbsent(tickerSymbol, k -> new CurrentSecurityData(tickerSymbol, null, null, null));
            return securityData.setSupplementaryDatum(key, datum, securitySupplementalDatumUpdatedCallback, this, update);
        }
        return false;
    }

    public Double getOptionsContractSupplementalDatum(String tickerSymbol, String contract, String key) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractSupplementalDatum(contract, key) : null;
    }

    public boolean setOptionSupplementalDatum(String tickerSymbol, String contract, String key, Double datum, SupplementalDatumUpdate update) {
        if (tickerSymbol != null && !tickerSymbol.trim().isEmpty()) {
            SecurityData securityData = securities.computeIfAbsent(tickerSymbol, k -> new CurrentSecurityData(tickerSymbol, null, null, null));
            return securityData.setOptionsContractSupplementalDatum(contract, key, datum, optionsContractSupplementalDatumUpdatedCallback, this, update);
        }
        return false;
    }

    public Greek getOptionsContractGreekData(String tickerSymbol, String contract, String key) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractGreekData(contract, key) : null;
    }

    public boolean setOptionGreekData(String tickerSymbol, String contract, String key, Greek data, GreekDataUpdate update) {
        if (tickerSymbol != null && !tickerSymbol.trim().isEmpty()) {
            SecurityData securityData = securities.computeIfAbsent(tickerSymbol, k -> new CurrentSecurityData(tickerSymbol, null, null, null));
            return securityData.setOptionsContractGreekData(contract, key, data, optionsContractGreekDataUpdatedCallback, this, update);
        }
        return false;
    }

    public SecurityData getSecurityData(String tickerSymbol) {
        return securities.get(tickerSymbol);
    }

    public Map<String, SecurityData> getAllSecurityData() {
        return readonlySecurities;
    }

    public OptionsContractData getOptionsContractData(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractData(contract) : null;
    }

    public Map<String, OptionsContractData> getAllOptionsContractData(String tickerSymbol) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getAllOptionsContractData() : Collections.emptyMap();
    }

    public intrinio.realtime.equities.Trade getLatestEquityTrade(String tickerSymbol) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getLatestEquitiesTrade() : null;
    }

    public boolean setEquityTrade(intrinio.realtime.equities.Trade trade) {
        if (trade != null) {
            String symbol = trade.symbol();
            SecurityData securityData = securities.computeIfAbsent(symbol, k -> new CurrentSecurityData(symbol, trade, null, null));
            return securityData.setEquitiesTrade(trade, equitiesTradeUpdatedCallback, this);
        }
        return false;
    }

    public void onTrade(intrinio.realtime.equities.Trade trade) {
        setEquityTrade(trade);
    }

    public intrinio.realtime.equities.Quote getLatestEquityAskQuote(String tickerSymbol) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getLatestEquitiesAskQuote() : null;
    }

    public intrinio.realtime.equities.Quote getLatestEquityBidQuote(String tickerSymbol) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getLatestEquitiesBidQuote() : null;
    }

    public boolean setEquityQuote(intrinio.realtime.equities.Quote quote) {
        if (quote != null) {
            String symbol = quote.symbol();
            SecurityData securityData = securities.computeIfAbsent(symbol, k -> new CurrentSecurityData(symbol, null, quote.type() == intrinio.realtime.equities.QuoteType.ASK ? quote : null, quote.type() == intrinio.realtime.equities.QuoteType.BID ? quote : null));
            return securityData.setEquitiesQuote(quote, equitiesQuoteUpdatedCallback, this);
        }
        return false;
    }

    public void onQuote(intrinio.realtime.equities.Quote quote) {
        setEquityQuote(quote);
    }

    public intrinio.realtime.options.Trade getLatestOptionsTrade(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractTrade(contract) : null;
    }

    public boolean setOptionsTrade(intrinio.realtime.options.Trade trade) {
        if (trade != null) {
            String underlyingSymbol = trade.getUnderlyingSymbol();
            SecurityData securityData = securities.computeIfAbsent(underlyingSymbol, k -> new CurrentSecurityData(underlyingSymbol, null, null, null));
            return securityData.setOptionsContractTrade(trade, optionsTradeUpdatedCallback, this);
        }
        return false;
    }

    public void onTrade(intrinio.realtime.options.Trade trade) {
        setOptionsTrade(trade);
    }

    public intrinio.realtime.options.Quote getLatestOptionsQuote(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractQuote(contract) : null;
    }

    public boolean setOptionsQuote(intrinio.realtime.options.Quote quote) {
        if (quote != null) {
            String underlyingSymbol = quote.getUnderlyingSymbol();
            SecurityData securityData = securities.computeIfAbsent(underlyingSymbol, k -> new CurrentSecurityData(underlyingSymbol, null, null, null));
            return securityData.setOptionsContractQuote(quote, optionsQuoteUpdatedCallback, this);
        }
        return false;
    }

    public void onQuote(intrinio.realtime.options.Quote quote) {
        setOptionsQuote(quote);
    }

    public intrinio.realtime.options.Refresh getLatestOptionsRefresh(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractRefresh(contract) : null;
    }

    public boolean setOptionsRefresh(intrinio.realtime.options.Refresh refresh) {
        if (refresh != null) {
            String underlyingSymbol = refresh.getUnderlyingSymbol();
            SecurityData securityData = securities.computeIfAbsent(underlyingSymbol, k -> new CurrentSecurityData(underlyingSymbol, null, null, null));
            return securityData.setOptionsContractRefresh(refresh, optionsRefreshUpdatedCallback, this);
        }
        return false;
    }

    public void onRefresh(intrinio.realtime.options.Refresh refresh) {
        setOptionsRefresh(refresh);
    }

    public intrinio.realtime.options.UnusualActivity getLatestOptionsUnusualActivity(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractUnusualActivity(contract) : null;
    }

    public boolean setOptionsUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity) {
        if (unusualActivity != null) {
            String underlyingSymbol = unusualActivity.getUnderlyingSymbol();
            SecurityData securityData = securities.computeIfAbsent(underlyingSymbol, k -> new CurrentSecurityData(underlyingSymbol, null, null, null));
            return securityData.setOptionsContractUnusualActivity(unusualActivity, optionsUnusualActivityUpdatedCallback, this);
        }
        return false;
    }

    public void onUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity) {
        setOptionsUnusualActivity(unusualActivity);
    }

    public OnSupplementalDatumUpdated getSupplementalDatumUpdatedCallback() {
        return supplementalDatumUpdatedCallback;
    }

    public void setSupplementalDatumUpdatedCallback(OnSupplementalDatumUpdated supplementalDatumUpdatedCallback) {
        this.supplementalDatumUpdatedCallback = supplementalDatumUpdatedCallback;
    }

    public OnSecuritySupplementalDatumUpdated getSecuritySupplementalDatumUpdatedCallback() {
        return securitySupplementalDatumUpdatedCallback;
    }

    public void setSecuritySupplementalDatumUpdatedCallback(OnSecuritySupplementalDatumUpdated securitySupplementalDatumUpdatedCallback) {
        this.securitySupplementalDatumUpdatedCallback = securitySupplementalDatumUpdatedCallback;
    }

    public OnOptionsContractSupplementalDatumUpdated getOptionsContractSupplementalDatumUpdatedCallback() {
        return optionsContractSupplementalDatumUpdatedCallback;
    }

    public void setOptionsContractSupplementalDatumUpdatedCallback(OnOptionsContractSupplementalDatumUpdated optionsContractSupplementalDatumUpdatedCallback) {
        this.optionsContractSupplementalDatumUpdatedCallback = optionsContractSupplementalDatumUpdatedCallback;
    }

    public OnOptionsContractGreekDataUpdated getOptionsContractGreekDataUpdatedCallback() {
        return optionsContractGreekDataUpdatedCallback;
    }

    public void setOptionsContractGreekDataUpdatedCallback(OnOptionsContractGreekDataUpdated optionsContractGreekDataUpdatedCallback) {
        this.optionsContractGreekDataUpdatedCallback = optionsContractGreekDataUpdatedCallback;
    }

    public OnEquitiesTradeUpdated getEquitiesTradeUpdatedCallback() {
        return equitiesTradeUpdatedCallback;
    }

    public void setEquitiesTradeUpdatedCallback(OnEquitiesTradeUpdated equitiesTradeUpdatedCallback) {
        this.equitiesTradeUpdatedCallback = equitiesTradeUpdatedCallback;
    }

    public OnEquitiesQuoteUpdated getEquitiesQuoteUpdatedCallback() {
        return equitiesQuoteUpdatedCallback;
    }

    public void setEquitiesQuoteUpdatedCallback(OnEquitiesQuoteUpdated equitiesQuoteUpdatedCallback) {
        this.equitiesQuoteUpdatedCallback = equitiesQuoteUpdatedCallback;
    }

    public OnOptionsTradeUpdated getOptionsTradeUpdatedCallback() {
        return optionsTradeUpdatedCallback;
    }

    public void setOptionsTradeUpdatedCallback(OnOptionsTradeUpdated optionsTradeUpdatedCallback) {
        this.optionsTradeUpdatedCallback = optionsTradeUpdatedCallback;
    }

    public OnOptionsQuoteUpdated getOptionsQuoteUpdatedCallback() {
        return optionsQuoteUpdatedCallback;
    }

    public void setOptionsQuoteUpdatedCallback(OnOptionsQuoteUpdated optionsQuoteUpdatedCallback) {
        this.optionsQuoteUpdatedCallback = optionsQuoteUpdatedCallback;
    }

    public OnOptionsRefreshUpdated getOptionsRefreshUpdatedCallback() {
        return optionsRefreshUpdatedCallback;
    }

    public void setOptionsRefreshUpdatedCallback(OnOptionsRefreshUpdated optionsRefreshUpdatedCallback) {
        this.optionsRefreshUpdatedCallback = optionsRefreshUpdatedCallback;
    }

    public OnOptionsUnusualActivityUpdated getOptionsUnusualActivityUpdatedCallback() {
        return optionsUnusualActivityUpdatedCallback;
    }

    public void setOptionsUnusualActivityUpdatedCallback(OnOptionsUnusualActivityUpdated optionsUnusualActivityUpdatedCallback) {
        this.optionsUnusualActivityUpdatedCallback = optionsUnusualActivityUpdatedCallback;
    }
    
    private void Log(String message){
        System.out.println(message);
    }
}