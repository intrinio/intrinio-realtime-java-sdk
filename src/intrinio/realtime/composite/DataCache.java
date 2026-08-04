package intrinio.realtime.composite;

import java.util.Map;

/**
 * A non-transactional, thread-safe, volatile local cache for storing the latest data from
 * equities and options WebSocket feeds, plus optional supplemental and Greek values.
 * <p>
 * <strong>Concurrency model:</strong> this cache does <em>not</em> provide transactional
 * snapshots. Updates use concurrent maps and “dirty” timestamp checks (accept a value only
 * when it is newer than what is stored). Readers may observe a mix of old and new fields
 * across concurrent writers. Callbacks run on the calling/update thread and should be short.
 * </p>
 * <p>
 * Obtain instances via {@link DataCacheFactory#create()}.
 * </p>
 */
public interface DataCache {

    //region Supplementary Data

    /**
     * Get a supplementary data point from the general (top-level) cache.
     *
     * @param key datum key
     * @return the value, or {@code null} if absent
     */
    Double getSupplementaryDatum(String key);

    /**
     * Set a supplementary data point in the general cache.
     * The provided {@link SupplementalDatumUpdate} merges old and new values atomically per key.
     *
     * @param key    datum key
     * @param datum  new value (may be {@code null} to clear via the update function)
     * @param update merge function
     * @return {@code true} if the stored value equals {@code datum} after the update
     */
    boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update);

    /**
     * Get all supplementary data stored at the top-level general cache.
     * The returned map is a live, unmodifiable view of concurrent storage.
     *
     * @return unmodifiable view of top-level supplemental data
     */
    Map<String, Double> getAllSupplementaryData();

    /**
     * Get a supplemental data point stored in a specific security's cache.
     *
     * @param tickerSymbol equity ticker
     * @param key          datum key
     * @return the value, or {@code null} if absent
     */
    Double getSecuritySupplementalDatum(String tickerSymbol, String key);

    /**
     * Set a supplemental data point stored in a specific security's cache.
     * Creates the security sub-cache if needed.
     *
     * @param tickerSymbol equity ticker
     * @param key          datum key
     * @param datum        new value
     * @param update       merge function
     * @return {@code true} if the value was accepted by the merge
     */
    boolean setSecuritySupplementalDatum(String tickerSymbol, String key, Double datum, SupplementalDatumUpdate update);

    /**
     * Get a supplemental data point stored in a specific option contract's cache.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @param key          datum key
     * @return the value, or {@code null} if absent
     */
    Double getOptionsContractSupplementalDatum(String tickerSymbol, String contract, String key);

    /**
     * Set a supplemental data point stored in a specific option contract's cache.
     * Creates security and contract sub-caches if needed.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @param key          datum key
     * @param datum        new value
     * @param update       merge function
     * @return {@code true} if the value was accepted by the merge
     */
    boolean setOptionSupplementalDatum(String tickerSymbol, String contract, String key, Double datum, SupplementalDatumUpdate update);

    //endregion Supplementary Data

    //region Greek Data

    /**
     * Get Greek data stored for a specific option contract.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @param key          Greek series key (e.g. {@link GreekClient#BLACK_SCHOLES_KEY_NAME})
     * @return the Greek value, or {@code null} if absent
     */
    Greek getOptionsContractGreekData(String tickerSymbol, String contract, String key);

    /**
     * Set Greek data for a specific option contract.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @param key          Greek series key
     * @param data         new Greek value
     * @param update       merge function
     * @return {@code true} if the value was accepted by the merge
     */
    boolean setOptionGreekData(String tickerSymbol, String contract, String key, Greek data, GreekDataUpdate update);

    //endregion Greek Data

    //region Sub-caches

    /**
     * Get the cache for a specific security.
     *
     * @param tickerSymbol equity ticker
     * @return security data, or {@code null} if never created
     */
    SecurityData getSecurityData(String tickerSymbol);

    /**
     * Get all security caches.
     * The returned map is a live, unmodifiable view of concurrent storage.
     *
     * @return unmodifiable view of all securities
     */
    Map<String, SecurityData> getAllSecurityData();

    /**
     * Get a specific option contract's cache.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @return contract data, or {@code null} if absent
     */
    OptionsContractData getOptionsContractData(String tickerSymbol, String contract);

    /**
     * Get all option contract caches for a security.
     *
     * @param tickerSymbol underlying ticker
     * @return unmodifiable view of contracts, or an empty map if the security is unknown
     */
    Map<String, OptionsContractData> getAllOptionsContractData(String tickerSymbol);

    //endregion Sub-caches

    //region Equities

    /**
     * Get the latest trade for a security.
     *
     * @param tickerSymbol equity ticker
     * @return latest trade, or {@code null}
     */
    intrinio.realtime.equities.Trade getLatestEquityTrade(String tickerSymbol);

    /**
     * Set the latest trade for a security (dirty set by timestamp).
     * Creates the security sub-cache if needed and may invoke {@link OnEquitiesTradeUpdated}.
     *
     * @param trade equities trade
     * @return {@code true} if the trade was stored as the new latest
     */
    boolean setEquityTrade(intrinio.realtime.equities.Trade trade);

    /**
     * Get the latest ask quote for a security.
     *
     * @param tickerSymbol equity ticker
     * @return latest ask quote, or {@code null}
     */
    intrinio.realtime.equities.Quote getLatestEquityAskQuote(String tickerSymbol);

    /**
     * Get the latest bid quote for a security.
     *
     * @param tickerSymbol equity ticker
     * @return latest bid quote, or {@code null}
     */
    intrinio.realtime.equities.Quote getLatestEquityBidQuote(String tickerSymbol);

    /**
     * Set the latest quote for a security (ask or bid by quote type; dirty set by timestamp).
     *
     * @param quote equities quote
     * @return {@code true} if the quote was stored as the new latest for its side
     */
    boolean setEquityQuote(intrinio.realtime.equities.Quote quote);

    //endregion Equities

    //region Options

    /**
     * Get the latest option contract trade.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @return latest trade, or {@code null}
     */
    intrinio.realtime.options.Trade getLatestOptionsTrade(String tickerSymbol, String contract);

    /**
     * Set the latest option contract trade (dirty set by timestamp).
     *
     * @param trade options trade
     * @return {@code true} if the trade was stored as the new latest
     */
    boolean setOptionsTrade(intrinio.realtime.options.Trade trade);

    /**
     * Get the latest option contract quote.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @return latest quote, or {@code null}
     */
    intrinio.realtime.options.Quote getLatestOptionsQuote(String tickerSymbol, String contract);

    /**
     * Set the latest option contract quote (dirty set by timestamp).
     *
     * @param quote options quote
     * @return {@code true} if the quote was stored as the new latest
     */
    boolean setOptionsQuote(intrinio.realtime.options.Quote quote);

    /**
     * Get the latest option contract refresh.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @return latest refresh, or {@code null}
     */
    intrinio.realtime.options.Refresh getLatestOptionsRefresh(String tickerSymbol, String contract);

    /**
     * Set the latest option contract refresh (always overwrites).
     *
     * @param refresh options refresh
     * @return {@code true} if stored
     */
    boolean setOptionsRefresh(intrinio.realtime.options.Refresh refresh);

    /**
     * Get the latest option contract unusual activity.
     *
     * @param tickerSymbol underlying ticker
     * @param contract     option contract id
     * @return latest unusual activity, or {@code null}
     */
    intrinio.realtime.options.UnusualActivity getLatestOptionsUnusualActivity(String tickerSymbol, String contract);

    /**
     * Set the latest option contract unusual activity (always overwrites).
     *
     * @param unusualActivity unusual activity event
     * @return {@code true} if stored
     */
    boolean setOptionsUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity);

    //endregion Options

    //region Callbacks

    /** @return callback for top-level supplemental updates, or {@code null} */
    OnSupplementalDatumUpdated getSupplementalDatumUpdatedCallback();

    /**
     * Set the callback when the top-level supplemental data is updated.
     * Replaces any previous callback (compose/chain externally if multiple listeners are needed).
     */
    void setSupplementalDatumUpdatedCallback(OnSupplementalDatumUpdated callback);

    /** @return callback for security-level supplemental updates, or {@code null} */
    OnSecuritySupplementalDatumUpdated getSecuritySupplementalDatumUpdatedCallback();

    /** Set the callback when a security's supplemental data is updated. */
    void setSecuritySupplementalDatumUpdatedCallback(OnSecuritySupplementalDatumUpdated callback);

    /** @return callback for option-contract supplemental updates, or {@code null} */
    OnOptionsContractSupplementalDatumUpdated getOptionsContractSupplementalDatumUpdatedCallback();

    /** Set the callback when an option contract's supplemental data is updated. */
    void setOptionsContractSupplementalDatumUpdatedCallback(OnOptionsContractSupplementalDatumUpdated callback);

    /** @return callback for equities trade updates, or {@code null} */
    OnEquitiesTradeUpdated getEquitiesTradeUpdatedCallback();

    /** Set the callback for when the latest equity trade is updated. */
    void setEquitiesTradeUpdatedCallback(OnEquitiesTradeUpdated callback);

    /** @return callback for equities quote updates, or {@code null} */
    OnEquitiesQuoteUpdated getEquitiesQuoteUpdatedCallback();

    /** Set the callback for when the latest equity quote is updated. */
    void setEquitiesQuoteUpdatedCallback(OnEquitiesQuoteUpdated callback);

    /** @return callback for options trade updates, or {@code null} */
    OnOptionsTradeUpdated getOptionsTradeUpdatedCallback();

    /** Set the callback for when the latest option trade is updated. */
    void setOptionsTradeUpdatedCallback(OnOptionsTradeUpdated callback);

    /** @return callback for options quote updates, or {@code null} */
    OnOptionsQuoteUpdated getOptionsQuoteUpdatedCallback();

    /** Set the callback for when the latest option quote is updated. */
    void setOptionsQuoteUpdatedCallback(OnOptionsQuoteUpdated callback);

    /** @return callback for options refresh updates, or {@code null} */
    OnOptionsRefreshUpdated getOptionsRefreshUpdatedCallback();

    /** Set the callback for when the latest option refresh is updated. */
    void setOptionsRefreshUpdatedCallback(OnOptionsRefreshUpdated callback);

    /** @return callback for options unusual-activity updates, or {@code null} */
    OnOptionsUnusualActivityUpdated getOptionsUnusualActivityUpdatedCallback();

    /** Set the callback for when the latest option unusual activity is updated. */
    void setOptionsUnusualActivityUpdatedCallback(OnOptionsUnusualActivityUpdated callback);

    /** @return callback for option Greek updates, or {@code null} */
    OnOptionsContractGreekDataUpdated getOptionsContractGreekDataUpdatedCallback();

    /** Set the callback for when option contract Greek data is updated. */
    void setOptionsContractGreekDataUpdatedCallback(OnOptionsContractGreekDataUpdated callback);

    //endregion Callbacks
}
