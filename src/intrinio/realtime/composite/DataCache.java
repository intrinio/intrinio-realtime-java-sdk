package intrinio.realtime.composite;

import java.util.Map;

/**
 * A non-transactional, thread-safe, volatile local cache for storing the latest data from a websocket.
 */
public interface DataCache {

    /**
     * Get a supplementary data point from the general cache.
     */
    Double getSupplementaryDatum(String key);

    /**
     * Set a supplementary data point in the general cache.
     */
    boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update);

    /**
     * Get all supplementary data stored at the top level general cache.
     */
    Map<String, Double> getAllSupplementaryData();

    /**
     * Get a supplemental data point stored in a specific security's cache.
     */
    Double getSecuritySupplementalDatum(String tickerSymbol, String key);

    /**
     * Set a supplemental data point stored in a specific security's cache.
     */
    boolean setSecuritySupplementalDatum(String tickerSymbol, String key, Double datum, SupplementalDatumUpdate update);

    /**
     * Get a supplemental data point stored in a specific option contract's cache.
     */
    Double getOptionsContractSupplementalDatum(String tickerSymbol, String contract, String key);

    /**
     * Set a supplemental data point stored in a specific option contract's cache.
     */
    boolean setOptionSupplementalDatum(String tickerSymbol, String contract, String key, Double datum, SupplementalDatumUpdate update);

    /**
     * Get a supplemental data point stored in a specific option contract's cache.
     */
    Greek getOptionsContractGreekData(String tickerSymbol, String contract, String key);

    /**
     * Set a supplemental data point stored in a specific option contract's cache.
     */
    boolean setOptionGreekData(String tickerSymbol, String contract, String key, Greek data, GreekDataUpdate update);

    /**
     * Get the cache for a specific security
     */
    SecurityData getSecurityData(String tickerSymbol);

    /**
     * Get all security caches.
     */
    Map<String, SecurityData> getAllSecurityData();

    /**
     * Get a specific option contract's cache.
     */
    OptionsContractData getOptionsContractData(String tickerSymbol, String contract);

    /**
     * Get all option contract caches for a security.
     */
    Map<String, OptionsContractData> getAllOptionsContractData(String tickerSymbol);

    /**
     * Get the latest trade for a security.
     */
    intrinio.realtime.equities.Trade getLatestEquityTrade(String tickerSymbol);

    /**
     * Set the latest trade for a security.
     */
    boolean setEquityTrade(intrinio.realtime.equities.Trade trade);

    /**
     * Get the latest ask quote for a security.
     */
    intrinio.realtime.equities.Quote getLatestEquityAskQuote(String tickerSymbol);

    /**
     * Set the latest bid quote for a security.
     */
    intrinio.realtime.equities.Quote getLatestEquityBidQuote(String tickerSymbol);

    /**
     * Set the latest quote for a security.
     */
    boolean setEquityQuote(intrinio.realtime.equities.Quote quote);

    /**
     * Get the latest option contract trade.
     */
    intrinio.realtime.options.Trade getLatestOptionsTrade(String tickerSymbol, String contract);

    /**
     * Set the latest option contract trade.
     */
    boolean setOptionsTrade(intrinio.realtime.options.Trade trade);

    /**
     * Get the latest option contract quote.
     */
    intrinio.realtime.options.Quote getLatestOptionsQuote(String tickerSymbol, String contract);

    /**
     * Set the latest option contract quote.
     */
    boolean setOptionsQuote(intrinio.realtime.options.Quote quote);

    /**
     * Get the latest option contract refresh.
     */
    intrinio.realtime.options.Refresh getLatestOptionsRefresh(String tickerSymbol, String contract);

    /**
     * Set the latest option contract refresh.
     */
    boolean setOptionsRefresh(intrinio.realtime.options.Refresh refresh);

    /**
     * Get the latest option contract unusual activity.
     */
    intrinio.realtime.options.UnusualActivity getLatestOptionsUnusualActivity(String tickerSymbol, String contract);

    /**
     * Set the latest option contract unusual activity.
     */
    boolean setOptionsUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity);

    /**
     * Set the callback when the top level supplemental data is updated.
     */
    OnSupplementalDatumUpdated getSupplementalDatumUpdatedCallback();
    void setSupplementalDatumUpdatedCallback(OnSupplementalDatumUpdated callback);

    /**
     * Set the callback when a security's supplemental data is updated.
     */
    OnSecuritySupplementalDatumUpdated getSecuritySupplementalDatumUpdatedCallback();
    void setSecuritySupplementalDatumUpdatedCallback(OnSecuritySupplementalDatumUpdated callback);

    /**
     * Set the callback when an option contract's supplemental data is updated.
     */
    OnOptionsContractSupplementalDatumUpdated getOptionsContractSupplementalDatumUpdatedCallback();
    void setOptionsContractSupplementalDatumUpdatedCallback(OnOptionsContractSupplementalDatumUpdated callback);

    /**
     * Set the callback for when the latest equity trade is updated.
     */
    OnEquitiesTradeUpdated getEquitiesTradeUpdatedCallback();
    void setEquitiesTradeUpdatedCallback(OnEquitiesTradeUpdated callback);

    /**
     * Set the callback for when the latest equity quote is updated.
     */
    OnEquitiesQuoteUpdated getEquitiesQuoteUpdatedCallback();
    void setEquitiesQuoteUpdatedCallback(OnEquitiesQuoteUpdated callback);

    /**
     * Set the callback for when the latest option trade is updated.
     */
    OnOptionsTradeUpdated getOptionsTradeUpdatedCallback();
    void setOptionsTradeUpdatedCallback(OnOptionsTradeUpdated callback);

    /**
     * Set the callback for when the latest option quote is updated.
     */
    OnOptionsQuoteUpdated getOptionsQuoteUpdatedCallback();
    void setOptionsQuoteUpdatedCallback(OnOptionsQuoteUpdated callback);

    /**
     * Set the callback for when the latest option refresh is updated.
     */
    OnOptionsRefreshUpdated getOptionsRefreshUpdatedCallback();
    void setOptionsRefreshUpdatedCallback(OnOptionsRefreshUpdated callback);

    /**
     * Set the callback for when the latest option unusual activity is updated.
     */
    OnOptionsUnusualActivityUpdated getOptionsUnusualActivityUpdatedCallback();
    void setOptionsUnusualActivityUpdatedCallback(OnOptionsUnusualActivityUpdated callback);

    OnOptionsContractGreekDataUpdated getOptionsContractGreekDataUpdatedCallback();
    void setOptionsContractGreekDataUpdatedCallback(OnOptionsContractGreekDataUpdated callback);
}