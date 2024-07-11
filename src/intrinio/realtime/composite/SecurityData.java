package intrinio.realtime.composite;

import java.util.List;
import java.util.Map;

public interface SecurityData {

    String getTickerSymbol();

    Double getSupplementaryDatum(String key);

    boolean setSupplementaryDatum(String key, double datum);

    Map<String, Double> getAllSupplementaryData();

    intrinio.realtime.equities.Trade getEquitiesTrade();

    intrinio.realtime.equities.Quote getEquitiesQuote();

    OptionsContractData getOptionsContractData(String contract);

    Map<String, OptionsContractData> getAllOptionsContractData();

    List<String> getContractNames(String ticker);

    boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade);

    boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote);

    intrinio.realtime.options.Trade getOptionsContractTrade(String contract);

    boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade);

    intrinio.realtime.options.Quote getOptionsContractQuote(String contract);

    boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote);

    Double getOptionsContractSupplementalDatum(String contract, String key);

    boolean setOptionsContractSupplementalDatum(String contract, String key, double datum);
}
