package intrinio.realtime.composite;

import java.util.*;

public interface DataCache
{
    Double getsupplementaryDatum(String key);

    boolean setsupplementaryDatum(String key, double datum);

    Map<String, Double> getAllSupplementaryData();

    SecurityData getSecurityData(String tickerSymbol);

    Map<String, SecurityData> getAllSecurityData();

    intrinio.realtime.equities.Trade getEquityTrade(String tickerSymbol);

    boolean setEquityTrade(intrinio.realtime.equities.Trade trade);

    intrinio.realtime.equities.Quote getEquityQuote(String tickerSymbol);

    boolean setEquityQuote(intrinio.realtime.equities.Quote quote);

    OptionsContractData getOptionsContractData(String tickerSymbol, String contract);

    intrinio.realtime.options.Trade getOptionsTrade(String tickerSymbol, String contract);

    boolean setOptionsTrade(intrinio.realtime.options.Trade trade);

    intrinio.realtime.options.Quote getOptionsQuote(String tickerSymbol, String contract);

    boolean setOptionsQuote(intrinio.realtime.options.Quote quote);

    intrinio.realtime.options.Refresh getOptionsRefresh(String tickerSymbol, String contract);

    boolean setOptionsRefresh(intrinio.realtime.options.Refresh refresh);

    Double getSecuritySupplementalDatum(String tickerSymbol, String key);

    boolean setSecuritySupplementalDatum(String tickerSymbol, String key, double datum);

    Double getOptionsContractSupplementalDatum(String tickerSymbol, String contract, String key);

    boolean setOptionSupplementalDatum(String tickerSymbol, String contract, String key, double datum);

    void setOnSupplementalDatumUpdated(OnSupplementalDatumUpdated onSupplementalDatumUpdated);

    void setOnSecuritySupplementalDatumUpdated(OnSecuritySupplementalDatumUpdated onSecuritySupplementalDatumUpdated);

    void setOnOptionSupplementalDatumUpdated(OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated);

    void setOnEquitiesQuoteUpdated(OnEquitiesQuoteUpdated onEquitiesQuoteUpdated);

    void setOnEquitiesTradeUpdated(OnEquitiesTradeUpdated onEquitiesTradeUpdated);

    void setOnOptionsQuoteUpdated(OnOptionsQuoteUpdated onOptionsQuoteUpdated);

    void setOnOptionsTradeUpdated(OnOptionsTradeUpdated onOptionsTradeUpdated);

    void setOnOptionsRefreshUpdated(OnOptionsRefreshUpdated onOptionsRefreshUpdated);
}
