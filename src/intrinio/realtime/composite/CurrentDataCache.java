package intrinio.realtime.composite;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link DataCache} implementation.
 * <p>
 * Uses {@link ConcurrentHashMap} for securities and top-level supplemental data.
 * Per-field market-data updates are <strong>dirty sets</strong> (no locks, no transactions):
 * a writer may overwrite another writer's value if timestamps allow, and readers may observe
 * partially updated aggregates. Callbacks are invoked synchronously on the updating thread;
 * exceptions in callbacks are logged and swallowed so the feed is not disrupted.
 * </p>
 */
class CurrentDataCache implements DataCache {

    /** Concurrent map of ticker → security sub-cache. */
    private final ConcurrentHashMap<String, SecurityData> securities = new ConcurrentHashMap<>();

    /** Unmodifiable live view of {@link #securities}. */
    private final Map<String, SecurityData> readonlySecurities = Collections.unmodifiableMap(securities);

    /** Concurrent map of top-level supplemental numeric data. */
    private final ConcurrentHashMap<String, Double> supplementaryData = new ConcurrentHashMap<>();

    /** Unmodifiable live view of {@link #supplementaryData}. */
    private final Map<String, Double> readonlySupplementaryData = Collections.unmodifiableMap(supplementaryData);

    /** Optional callback for top-level supplemental updates. */
    private volatile OnSupplementalDatumUpdated supplementalDatumUpdatedCallback;

    /** Optional callback for security-level supplemental updates. */
    private volatile OnSecuritySupplementalDatumUpdated securitySupplementalDatumUpdatedCallback;

    /** Optional callback for option-contract supplemental updates. */
    private volatile OnOptionsContractSupplementalDatumUpdated optionsContractSupplementalDatumUpdatedCallback;

    /** Optional callback for option Greek updates. */
    private volatile OnOptionsContractGreekDataUpdated optionsContractGreekDataUpdatedCallback;

    /** Optional callback for equities trade updates. */
    private volatile OnEquitiesTradeUpdated equitiesTradeUpdatedCallback;

    /** Optional callback for equities quote updates. */
    private volatile OnEquitiesQuoteUpdated equitiesQuoteUpdatedCallback;

    /** Optional callback for options trade updates. */
    private volatile OnOptionsTradeUpdated optionsTradeUpdatedCallback;

    /** Optional callback for options quote updates. */
    private volatile OnOptionsQuoteUpdated optionsQuoteUpdatedCallback;

    /** Optional callback for options refresh updates. */
    private volatile OnOptionsRefreshUpdated optionsRefreshUpdatedCallback;

    /** Optional callback for options unusual-activity updates. */
    private volatile OnOptionsUnusualActivityUpdated optionsUnusualActivityUpdatedCallback;

    CurrentDataCache() {
    }

    //region Supplementary Data

    @Override
    public Double getSupplementaryDatum(String key) {
        return supplementaryData.getOrDefault(key, null);
    }

    @Override
    public boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update) {
        // compute is atomic per key; returning null from update removes the mapping
        Double newValue = supplementaryData.compute(key, (k, oldValue) -> update.supplementalDatumUpdate(k, oldValue, datum));
        boolean result = java.util.Objects.equals(datum, newValue);
        if (result && supplementalDatumUpdatedCallback != null) {
            try {
                supplementalDatumUpdatedCallback.onSupplementalDatumUpdated(key, datum, this);
            } catch (Exception e) {
                log("Error in OnSupplementalDatumUpdated Callback: " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public Map<String, Double> getAllSupplementaryData() {
        return readonlySupplementaryData;
    }

    @Override
    public Double getSecuritySupplementalDatum(String tickerSymbol, String key) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getSupplementaryDatum(key) : null;
    }

    @Override
    public boolean setSecuritySupplementalDatum(String tickerSymbol, String key, Double datum, SupplementalDatumUpdate update) {
        if (tickerSymbol != null && !tickerSymbol.trim().isEmpty()) {
            // Get-or-create security without locking the whole map
            SecurityData securityData = securities.computeIfAbsent(
                    tickerSymbol, k -> new CurrentSecurityData(tickerSymbol, null, null, null));
            return securityData.setSupplementaryDatum(key, datum, securitySupplementalDatumUpdatedCallback, this, update);
        }
        return false;
    }

    @Override
    public Double getOptionsContractSupplementalDatum(String tickerSymbol, String contract, String key) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractSupplementalDatum(contract, key) : null;
    }

    @Override
    public boolean setOptionSupplementalDatum(String tickerSymbol, String contract, String key, Double datum, SupplementalDatumUpdate update) {
        if (tickerSymbol != null && !tickerSymbol.trim().isEmpty()) {
            SecurityData securityData = securities.computeIfAbsent(
                    tickerSymbol, k -> new CurrentSecurityData(tickerSymbol, null, null, null));
            return securityData.setOptionsContractSupplementalDatum(
                    contract, key, datum, optionsContractSupplementalDatumUpdatedCallback, this, update);
        }
        return false;
    }

    //endregion Supplementary Data

    //region Greeks

    @Override
    public Greek getOptionsContractGreekData(String tickerSymbol, String contract, String key) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractGreekData(contract, key) : null;
    }

    @Override
    public boolean setOptionGreekData(String tickerSymbol, String contract, String key, Greek data, GreekDataUpdate update) {
        if (tickerSymbol != null && !tickerSymbol.trim().isEmpty()) {
            SecurityData securityData = securities.computeIfAbsent(
                    tickerSymbol, k -> new CurrentSecurityData(tickerSymbol, null, null, null));
            return securityData.setOptionsContractGreekData(
                    contract, key, data, optionsContractGreekDataUpdatedCallback, this, update);
        }
        return false;
    }

    //endregion Greeks

    //region Sub-caches

    @Override
    public SecurityData getSecurityData(String tickerSymbol) {
        return securities.get(tickerSymbol);
    }

    @Override
    public Map<String, SecurityData> getAllSecurityData() {
        return readonlySecurities;
    }

    @Override
    public OptionsContractData getOptionsContractData(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractData(contract) : null;
    }

    @Override
    public Map<String, OptionsContractData> getAllOptionsContractData(String tickerSymbol) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getAllOptionsContractData() : Collections.emptyMap();
    }

    //endregion Sub-caches

    //region Equities

    @Override
    public intrinio.realtime.equities.Trade getLatestEquityTrade(String tickerSymbol) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getLatestEquitiesTrade() : null;
    }

    @Override
    public boolean setEquityTrade(intrinio.realtime.equities.Trade trade) {
        if (trade != null) {
            String symbol = trade.symbol();
            SecurityData securityData = securities.computeIfAbsent(
                    symbol, k -> new CurrentSecurityData(symbol, trade, null, null));
            return securityData.setEquitiesTrade(trade, equitiesTradeUpdatedCallback, this);
        }
        return false;
    }

    /**
     * Convenience handler for equities trade callbacks / plug-in style wiring.
     */
    public void onTrade(intrinio.realtime.equities.Trade trade) {
        setEquityTrade(trade);
    }

    @Override
    public intrinio.realtime.equities.Quote getLatestEquityAskQuote(String tickerSymbol) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getLatestEquitiesAskQuote() : null;
    }

    @Override
    public intrinio.realtime.equities.Quote getLatestEquityBidQuote(String tickerSymbol) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getLatestEquitiesBidQuote() : null;
    }

    @Override
    public boolean setEquityQuote(intrinio.realtime.equities.Quote quote) {
        if (quote != null) {
            String symbol = quote.symbol();
            SecurityData securityData = securities.computeIfAbsent(
                    symbol,
                    k -> new CurrentSecurityData(
                            symbol,
                            null,
                            quote.type() == intrinio.realtime.equities.QuoteType.ASK ? quote : null,
                            quote.type() == intrinio.realtime.equities.QuoteType.BID ? quote : null));
            return securityData.setEquitiesQuote(quote, equitiesQuoteUpdatedCallback, this);
        }
        return false;
    }

    /**
     * Convenience handler for equities quote callbacks / plug-in style wiring.
     */
    public void onQuote(intrinio.realtime.equities.Quote quote) {
        setEquityQuote(quote);
    }

    //endregion Equities

    //region Options

    @Override
    public intrinio.realtime.options.Trade getLatestOptionsTrade(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractTrade(contract) : null;
    }

    @Override
    public boolean setOptionsTrade(intrinio.realtime.options.Trade trade) {
        if (trade != null) {
            String underlyingSymbol = trade.getUnderlyingSymbol();
            SecurityData securityData = securities.computeIfAbsent(
                    underlyingSymbol, k -> new CurrentSecurityData(underlyingSymbol, null, null, null));
            return securityData.setOptionsContractTrade(trade, optionsTradeUpdatedCallback, this);
        }
        return false;
    }

    /**
     * Convenience handler for options trade callbacks / plug-in style wiring.
     */
    public void onTrade(intrinio.realtime.options.Trade trade) {
        setOptionsTrade(trade);
    }

    @Override
    public intrinio.realtime.options.Quote getLatestOptionsQuote(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractQuote(contract) : null;
    }

    @Override
    public boolean setOptionsQuote(intrinio.realtime.options.Quote quote) {
        if (quote != null) {
            String underlyingSymbol = quote.getUnderlyingSymbol();
            SecurityData securityData = securities.computeIfAbsent(
                    underlyingSymbol, k -> new CurrentSecurityData(underlyingSymbol, null, null, null));
            return securityData.setOptionsContractQuote(quote, optionsQuoteUpdatedCallback, this);
        }
        return false;
    }

    /**
     * Convenience handler for options quote callbacks / plug-in style wiring.
     */
    public void onQuote(intrinio.realtime.options.Quote quote) {
        setOptionsQuote(quote);
    }

    @Override
    public intrinio.realtime.options.Refresh getLatestOptionsRefresh(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractRefresh(contract) : null;
    }

    @Override
    public boolean setOptionsRefresh(intrinio.realtime.options.Refresh refresh) {
        if (refresh != null) {
            String underlyingSymbol = refresh.getUnderlyingSymbol();
            SecurityData securityData = securities.computeIfAbsent(
                    underlyingSymbol, k -> new CurrentSecurityData(underlyingSymbol, null, null, null));
            return securityData.setOptionsContractRefresh(refresh, optionsRefreshUpdatedCallback, this);
        }
        return false;
    }

    /**
     * Convenience handler for options refresh callbacks / plug-in style wiring.
     */
    public void onRefresh(intrinio.realtime.options.Refresh refresh) {
        setOptionsRefresh(refresh);
    }

    @Override
    public intrinio.realtime.options.UnusualActivity getLatestOptionsUnusualActivity(String tickerSymbol, String contract) {
        SecurityData securityData = securities.get(tickerSymbol);
        return securityData != null ? securityData.getOptionsContractUnusualActivity(contract) : null;
    }

    @Override
    public boolean setOptionsUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity) {
        if (unusualActivity != null) {
            String underlyingSymbol = unusualActivity.getUnderlyingSymbol();
            SecurityData securityData = securities.computeIfAbsent(
                    underlyingSymbol, k -> new CurrentSecurityData(underlyingSymbol, null, null, null));
            return securityData.setOptionsContractUnusualActivity(
                    unusualActivity, optionsUnusualActivityUpdatedCallback, this);
        }
        return false;
    }

    /**
     * Convenience handler for options unusual-activity callbacks / plug-in style wiring.
     */
    public void onUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity) {
        setOptionsUnusualActivity(unusualActivity);
    }

    //endregion Options

    //region Callback accessors

    @Override
    public OnSupplementalDatumUpdated getSupplementalDatumUpdatedCallback() {
        return supplementalDatumUpdatedCallback;
    }

    @Override
    public void setSupplementalDatumUpdatedCallback(OnSupplementalDatumUpdated supplementalDatumUpdatedCallback) {
        this.supplementalDatumUpdatedCallback = supplementalDatumUpdatedCallback;
    }

    @Override
    public OnSecuritySupplementalDatumUpdated getSecuritySupplementalDatumUpdatedCallback() {
        return securitySupplementalDatumUpdatedCallback;
    }

    @Override
    public void setSecuritySupplementalDatumUpdatedCallback(OnSecuritySupplementalDatumUpdated securitySupplementalDatumUpdatedCallback) {
        this.securitySupplementalDatumUpdatedCallback = securitySupplementalDatumUpdatedCallback;
    }

    @Override
    public OnOptionsContractSupplementalDatumUpdated getOptionsContractSupplementalDatumUpdatedCallback() {
        return optionsContractSupplementalDatumUpdatedCallback;
    }

    @Override
    public void setOptionsContractSupplementalDatumUpdatedCallback(OnOptionsContractSupplementalDatumUpdated optionsContractSupplementalDatumUpdatedCallback) {
        this.optionsContractSupplementalDatumUpdatedCallback = optionsContractSupplementalDatumUpdatedCallback;
    }

    @Override
    public OnOptionsContractGreekDataUpdated getOptionsContractGreekDataUpdatedCallback() {
        return optionsContractGreekDataUpdatedCallback;
    }

    @Override
    public void setOptionsContractGreekDataUpdatedCallback(OnOptionsContractGreekDataUpdated optionsContractGreekDataUpdatedCallback) {
        this.optionsContractGreekDataUpdatedCallback = optionsContractGreekDataUpdatedCallback;
    }

    @Override
    public OnEquitiesTradeUpdated getEquitiesTradeUpdatedCallback() {
        return equitiesTradeUpdatedCallback;
    }

    @Override
    public void setEquitiesTradeUpdatedCallback(OnEquitiesTradeUpdated equitiesTradeUpdatedCallback) {
        this.equitiesTradeUpdatedCallback = equitiesTradeUpdatedCallback;
    }

    @Override
    public OnEquitiesQuoteUpdated getEquitiesQuoteUpdatedCallback() {
        return equitiesQuoteUpdatedCallback;
    }

    @Override
    public void setEquitiesQuoteUpdatedCallback(OnEquitiesQuoteUpdated equitiesQuoteUpdatedCallback) {
        this.equitiesQuoteUpdatedCallback = equitiesQuoteUpdatedCallback;
    }

    @Override
    public OnOptionsTradeUpdated getOptionsTradeUpdatedCallback() {
        return optionsTradeUpdatedCallback;
    }

    @Override
    public void setOptionsTradeUpdatedCallback(OnOptionsTradeUpdated optionsTradeUpdatedCallback) {
        this.optionsTradeUpdatedCallback = optionsTradeUpdatedCallback;
    }

    @Override
    public OnOptionsQuoteUpdated getOptionsQuoteUpdatedCallback() {
        return optionsQuoteUpdatedCallback;
    }

    @Override
    public void setOptionsQuoteUpdatedCallback(OnOptionsQuoteUpdated optionsQuoteUpdatedCallback) {
        this.optionsQuoteUpdatedCallback = optionsQuoteUpdatedCallback;
    }

    @Override
    public OnOptionsRefreshUpdated getOptionsRefreshUpdatedCallback() {
        return optionsRefreshUpdatedCallback;
    }

    @Override
    public void setOptionsRefreshUpdatedCallback(OnOptionsRefreshUpdated optionsRefreshUpdatedCallback) {
        this.optionsRefreshUpdatedCallback = optionsRefreshUpdatedCallback;
    }

    @Override
    public OnOptionsUnusualActivityUpdated getOptionsUnusualActivityUpdatedCallback() {
        return optionsUnusualActivityUpdatedCallback;
    }

    @Override
    public void setOptionsUnusualActivityUpdatedCallback(OnOptionsUnusualActivityUpdated optionsUnusualActivityUpdatedCallback) {
        this.optionsUnusualActivityUpdatedCallback = optionsUnusualActivityUpdatedCallback;
    }

    //endregion Callback accessors

    private void log(String message) {
        System.out.println(message);
    }
}
