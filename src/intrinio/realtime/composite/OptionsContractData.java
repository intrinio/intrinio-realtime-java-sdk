package intrinio.realtime.composite;

import intrinio.realtime.options.Trade;
import intrinio.realtime.options.Quote;
import intrinio.realtime.options.Refresh;
import intrinio.realtime.options.UnusualActivity;
import intrinio.realtime.options.QuoteType;
import java.util.Map;

/**
 * Not for Use yet. Subject to change.
 */
public interface OptionsContractData {
    String getContract();

    Trade getLatestTrade();
    Quote getLatestQuote();
    Refresh getLatestRefresh();
    UnusualActivity getLatestUnusualActivity();

    boolean setTrade(Trade trade);
    boolean setTrade(Trade trade, OnOptionsTradeUpdated onOptionsTradeUpdated, SecurityData securityData, DataCache dataCache);
    boolean setQuote(Quote quote);
    boolean setQuote(Quote quote, OnOptionsQuoteUpdated onOptionsQuoteUpdated, SecurityData securityData, DataCache dataCache);
    boolean setRefresh(Refresh refresh);
    boolean setRefresh(Refresh refresh, OnOptionsRefreshUpdated onOptionsRefreshUpdated, SecurityData securityData, DataCache dataCache);
    boolean setUnusualActivity(UnusualActivity unusualActivity);
    boolean setUnusualActivity(UnusualActivity unusualActivity, OnOptionsUnusualActivityUpdated onOptionsUnusualActivityUpdated, SecurityData securityData, DataCache dataCache);

    Double getSupplementaryDatum(String key);
    boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update);
    boolean setSupplementaryDatum(String key, Double datum, OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated, SecurityData securityData, DataCache dataCache, SupplementalDatumUpdate update);
    Map<String, Double> getAllSupplementaryData();

    Greek getGreekData(String key);
    boolean setGreekData(String key, Greek datum, GreekDataUpdate update);
    boolean setGreekData(String key, Greek datum, OnOptionsContractGreekDataUpdated onOptionsContractGreekDataUpdated, SecurityData securityData, DataCache dataCache, GreekDataUpdate update);
    Map<String, Greek> getAllGreekData();
}