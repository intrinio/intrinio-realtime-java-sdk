package intrinio.realtime.composite;

import java.util.List;
import java.util.Map;

/**
 * Per-security slice of the composite cache: latest equities trade/quotes,
 * security-level supplemental data, and nested option-contract caches.
 * <p>
 * Updates are non-transactional dirty sets (typically by event timestamp) and are safe
 * for concurrent callers, but do not provide a consistent multi-field snapshot.
 * </p>
 */
public interface SecurityData {

    /**
     * @return equity ticker symbol for this cache entry
     */
    String getTickerSymbol();

    /** @return latest equities trade, or {@code null} */
    intrinio.realtime.equities.Trade getLatestEquitiesTrade();

    /** @return latest equities ask quote, or {@code null} */
    intrinio.realtime.equities.Quote getLatestEquitiesAskQuote();

    /** @return latest equities bid quote, or {@code null} */
    intrinio.realtime.equities.Quote getLatestEquitiesBidQuote();

    /**
     * @param key supplemental datum key
     * @return value or {@code null}
     */
    Double getSupplementaryDatum(String key);

    /**
     * Atomically merge a security-level supplemental value.
     *
     * @param key    datum key
     * @param datum  new value
     * @param update merge function
     * @return {@code true} if the stored value equals {@code datum} after merge
     */
    boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update);

    /**
     * Merge a security-level supplemental value and invoke a callback on success.
     */
    boolean setSupplementaryDatum(String key,
                                  Double datum,
                                  OnSecuritySupplementalDatumUpdated onSecuritySupplementalDatumUpdated,
                                  DataCache dataCache,
                                  SupplementalDatumUpdate update);

    /**
     * @return live unmodifiable view of security-level supplemental data
     */
    Map<String, Double> getAllSupplementaryData();

    /**
     * Dirty-set the latest equities trade when {@code trade} is newer.
     */
    boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade);

    /**
     * Dirty-set the latest equities trade and invoke callback on success.
     */
    boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade,
                             OnEquitiesTradeUpdated onEquitiesTradeUpdated,
                             DataCache dataCache);

    /**
     * Dirty-set the latest equities ask or bid quote by quote type when newer.
     */
    boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote);

    /**
     * Dirty-set the latest equities quote and invoke callback on success.
     */
    boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote,
                             OnEquitiesQuoteUpdated onEquitiesQuoteUpdated,
                             DataCache dataCache);

    /**
     * @param contract option contract id
     * @return contract cache or {@code null}
     */
    OptionsContractData getOptionsContractData(String contract);

    /**
     * @return live unmodifiable view of all option contract caches for this security
     */
    Map<String, OptionsContractData> getAllOptionsContractData();

    /**
     * @return list of known contract ids under this security
     */
    List<String> getContractNames();

    /** @return latest trade for the contract, or {@code null} */
    intrinio.realtime.options.Trade getOptionsContractTrade(String contract);

    boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade);

    boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade,
                                    OnOptionsTradeUpdated onOptionsTradeUpdated,
                                    DataCache dataCache);

    /** @return latest quote for the contract, or {@code null} */
    intrinio.realtime.options.Quote getOptionsContractQuote(String contract);

    boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote);

    boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote,
                                    OnOptionsQuoteUpdated onOptionsQuoteUpdated,
                                    DataCache dataCache);

    /** @return latest refresh for the contract, or {@code null} */
    intrinio.realtime.options.Refresh getOptionsContractRefresh(String contract);

    boolean setOptionsContractRefresh(intrinio.realtime.options.Refresh refresh);

    boolean setOptionsContractRefresh(intrinio.realtime.options.Refresh refresh,
                                      OnOptionsRefreshUpdated onOptionsRefreshUpdated,
                                      DataCache dataCache);

    /** @return latest unusual activity for the contract, or {@code null} */
    intrinio.realtime.options.UnusualActivity getOptionsContractUnusualActivity(String contract);

    boolean setOptionsContractUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity);

    boolean setOptionsContractUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity,
                                              OnOptionsUnusualActivityUpdated onOptionsUnusualActivityUpdated,
                                              DataCache dataCache);

    Double getOptionsContractSupplementalDatum(String contract, String key);

    boolean setOptionsContractSupplementalDatum(String contract, String key, Double datum, SupplementalDatumUpdate update);

    boolean setOptionsContractSupplementalDatum(String contract,
                                                String key,
                                                Double datum,
                                                OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated,
                                                DataCache dataCache,
                                                SupplementalDatumUpdate update);

    Greek getOptionsContractGreekData(String contract, String key);

    boolean setOptionsContractGreekData(String contract, String key, Greek data, GreekDataUpdate update);

    boolean setOptionsContractGreekData(String contract,
                                        String key,
                                        Greek data,
                                        OnOptionsContractGreekDataUpdated onOptionsContractGreekDataUpdated,
                                        DataCache dataCache,
                                        GreekDataUpdate update);
}
