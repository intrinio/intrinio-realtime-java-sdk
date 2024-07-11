package intrinio.realtime.composite;

import java.util.Map;

public interface OptionsContractData {

    String getContract();

    intrinio.realtime.options.Trade getTrade();

    intrinio.realtime.options.Quote getQuote();

    intrinio.realtime.options.Refresh getRefresh();

    boolean setTrade(intrinio.realtime.options.Trade trade);

    boolean setQuote(intrinio.realtime.options.Quote quote);

    boolean setRefresh(intrinio.realtime.options.Refresh refresh);

    Double getSupplementaryDatum(String key);

    boolean setSupplementaryDatum(String key, double datum);

    Map<String, Double> getAllSupplementaryData();
}
