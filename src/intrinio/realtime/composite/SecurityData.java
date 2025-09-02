package intrinio.realtime.composite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface SecurityData {
    String getTickerSymbol();

    intrinio.realtime.equities.Trade getLatestEquitiesTrade();
    intrinio.realtime.equities.Quote getLatestEquitiesAskQuote();
    intrinio.realtime.equities.Quote getLatestEquitiesBidQuote();

    Double getSupplementaryDatum(String key);

    boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update);
    boolean setSupplementaryDatum(String key, Double datum, OnSecuritySupplementalDatumUpdated onSecuritySupplementalDatumUpdated, DataCache dataCache, SupplementalDatumUpdate update);

    Map<String, Double> getAllSupplementaryData();

    boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade);
    boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade, OnEquitiesTradeUpdated onEquitiesTradeUpdated, DataCache dataCache);

    boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote);
    boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote, OnEquitiesQuoteUpdated onEquitiesQuoteUpdated, DataCache dataCache);

    OptionsContractData getOptionsContractData(String contract);

    Map<String, OptionsContractData> getAllOptionsContractData();

    List<String> getContractNames();

    intrinio.realtime.options.Trade getOptionsContractTrade(String contract);

    boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade);
    boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade, OnOptionsTradeUpdated onOptionsTradeUpdated, DataCache dataCache);

    intrinio.realtime.options.Quote getOptionsContractQuote(String contract);

    boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote);
    boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote, OnOptionsQuoteUpdated onOptionsQuoteUpdated, DataCache dataCache);

    intrinio.realtime.options.Refresh getOptionsContractRefresh(String contract);

    boolean setOptionsContractRefresh(intrinio.realtime.options.Refresh refresh);
    boolean setOptionsContractRefresh(intrinio.realtime.options.Refresh refresh, OnOptionsRefreshUpdated onOptionsRefreshUpdated, DataCache dataCache);

    intrinio.realtime.options.UnusualActivity getOptionsContractUnusualActivity(String contract);

    boolean setOptionsContractUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity);
    boolean setOptionsContractUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity, OnOptionsUnusualActivityUpdated onOptionsUnusualActivityUpdated, DataCache dataCache);

    Double getOptionsContractSupplementalDatum(String contract, String key);

    boolean setOptionsContractSupplementalDatum(String contract, String key, Double datum, SupplementalDatumUpdate update);
    boolean setOptionsContractSupplementalDatum(String contract, String key, Double datum, OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated, DataCache dataCache, SupplementalDatumUpdate update);

    Greek getOptionsContractGreekData(String contract, String key);

    boolean setOptionsContractGreekData(String contract, String key, Greek data, GreekDataUpdate update);
    boolean setOptionsContractGreekData(String contract, String key, Greek data, OnOptionsContractGreekDataUpdated onOptionsContractGreekDataUpdated, DataCache dataCache, GreekDataUpdate update);
}