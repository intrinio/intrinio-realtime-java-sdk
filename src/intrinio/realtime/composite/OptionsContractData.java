package intrinio.realtime.composite;

import java.util.Map;

public interface OptionsContractData {

    String getContract();

    intrinio.realtime.options.Trade getTrade();

    intrinio.realtime.options.Quote getQuote();

    boolean setTrade(intrinio.realtime.options.Trade trade);

    boolean setQuote(intrinio.realtime.options.Quote quote);

    Double getSupplementaryDatum(String key);

    boolean setSupplementaryDatum(String key, double datum);

    Map<String, Double> getAllSupplementaryData();
}
