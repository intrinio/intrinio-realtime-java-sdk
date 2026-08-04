package intrinio.realtime.composite;

import intrinio.realtime.options.Quote;
import intrinio.realtime.options.Refresh;
import intrinio.realtime.options.Trade;
import intrinio.realtime.options.UnusualActivity;

import java.util.Map;

/**
 * Per-option-contract slice of the composite cache: latest trade, quote, refresh,
 * unusual activity, plus supplemental numerics and Greek series.
 * <p>
 * Updates are non-transactional dirty sets unless noted.
 * </p>
 */
public interface OptionsContractData {

    /**
     * @return option contract identifier (e.g. {@code AAPL__240119C00150000})
     */
    String getContract();

    /** @return latest options trade, or {@code null} */
    Trade getLatestTrade();

    /** @return latest options quote, or {@code null} */
    Quote getLatestQuote();

    /** @return latest options refresh, or {@code null} */
    Refresh getLatestRefresh();

    /** @return latest unusual activity, or {@code null} */
    UnusualActivity getLatestUnusualActivity();

    /**
     * Dirty-set trade when {@code trade} is non-null and newer by timestamp.
     */
    boolean setTrade(Trade trade);

    /**
     * Dirty-set trade and invoke callback on success.
     */
    boolean setTrade(Trade trade,
                     OnOptionsTradeUpdated onOptionsTradeUpdated,
                     SecurityData securityData,
                     DataCache dataCache);

    /**
     * Dirty-set quote when {@code quote} is non-null and newer by timestamp.
     */
    boolean setQuote(Quote quote);

    boolean setQuote(Quote quote,
                     OnOptionsQuoteUpdated onOptionsQuoteUpdated,
                     SecurityData securityData,
                     DataCache dataCache);

    /**
     * Overwrite latest refresh (no timestamp comparison).
     */
    boolean setRefresh(Refresh refresh);

    boolean setRefresh(Refresh refresh,
                       OnOptionsRefreshUpdated onOptionsRefreshUpdated,
                       SecurityData securityData,
                       DataCache dataCache);

    /**
     * Overwrite latest unusual activity (no timestamp comparison).
     */
    boolean setUnusualActivity(UnusualActivity unusualActivity);

    boolean setUnusualActivity(UnusualActivity unusualActivity,
                               OnOptionsUnusualActivityUpdated onOptionsUnusualActivityUpdated,
                               SecurityData securityData,
                               DataCache dataCache);

    Double getSupplementaryDatum(String key);

    boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update);

    boolean setSupplementaryDatum(String key,
                                  Double datum,
                                  OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated,
                                  SecurityData securityData,
                                  DataCache dataCache,
                                  SupplementalDatumUpdate update);

    /**
     * @return live unmodifiable view of contract-level supplemental data
     */
    Map<String, Double> getAllSupplementaryData();

    Greek getGreekData(String key);

    boolean setGreekData(String key, Greek datum, GreekDataUpdate update);

    boolean setGreekData(String key,
                         Greek datum,
                         OnOptionsContractGreekDataUpdated onOptionsContractGreekDataUpdated,
                         SecurityData securityData,
                         DataCache dataCache,
                         GreekDataUpdate update);

    /**
     * @return live unmodifiable view of contract-level Greek series
     */
    Map<String, Greek> getAllGreekData();
}
