package intrinio.realtime.composite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CurrentSecurityData {
    private final String tickerSymbol;
    private volatile intrinio.realtime.equities.Trade equitiesTrade;
    private volatile intrinio.realtime.equities.Quote equitiesQuote;
    private final ConcurrentHashMap<String, OptionsContractData> contracts;
    private final Map<String, OptionsContractData> readonlyContracts;
    private volatile Double dividendYield;

    public CurrentSecurityData(String tickerSymbol){
        this.tickerSymbol = tickerSymbol;
        this.dividendYield = null;
        this.contracts = new ConcurrentHashMap<String, OptionsContractData>();
        this.readonlyContracts = java.util.Collections.unmodifiableMap(contracts);
    }

    public String getTickerSymbol(){
        return tickerSymbol;
    }

    public Double getDividendYield(){
        return dividendYield;
    }

    public boolean setDividendYield(double dividendYield){
        if (!Double.isNaN(dividendYield) && !Double.isInfinite(dividendYield)) {
            this.dividendYield = dividendYield;
            return true;
        }
        else return false;
    }

    public intrinio.realtime.equities.Trade getEquitiesTrade(){
        return equitiesTrade;
    }

    public intrinio.realtime.equities.Quote getEquitiesQuote(){
        return equitiesQuote;
    }

    public OptionsContractData getOptionsContractData(String contract){
        return contracts.getOrDefault(contract, null);
    }

    public Map<String, OptionsContractData> getAllOptionsContractData(){
        return readonlyContracts;
    }

    public List<String> getContractNames(String ticker){
        return contracts.values().stream().map(OptionsContractData::getContract).collect(Collectors.toList());
    }

    public boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade){
        //dirty set
        if ((equitiesTrade == null) || (trade.timestamp() > equitiesTrade.timestamp())) {
            equitiesTrade = trade;
            return true;
        }
        return false;
    }

    public boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote){
        //dirty set
        if ((equitiesQuote == null) || (quote.timestamp() > equitiesQuote.timestamp())) {
            equitiesQuote = quote;
            return true;
        }
        return false;
    }

    public intrinio.realtime.options.Trade getOptionsTrade(String contract){
        if (contracts.containsKey(contract))
            return contracts.get(contract).getLatestTrade();
        else return null;
    }

    public boolean setOptionsTrade(intrinio.realtime.options.Trade trade){
        //dirty set
        if (contracts.containsKey(trade.contract())){
            return contracts.get(trade.contract()).setLatestTrade(trade);
        }
        else{
            OptionsContractData data = new OptionsContractData(trade.contract(), trade, null);
            OptionsContractData possiblyNewerData = contracts.putIfAbsent(trade.contract(), data);
            if (possiblyNewerData != null)
                return possiblyNewerData.setLatestTrade(trade);
            return true;
        }
    }

    public intrinio.realtime.options.Quote getOptionsQuote(String contract){
        if (contracts.containsKey(contract))
            return contracts.get(contract).getLatestQuote();
        else return null;
    }

    public boolean setOptionsQuote(intrinio.realtime.options.Quote quote){
        //dirty set
        if (contracts.containsKey(quote.contract())){
            return contracts.get(quote.contract()).setLatestQuote(quote);
        }
        else{
            OptionsContractData data = new OptionsContractData(quote.contract(), null, quote);
            OptionsContractData possiblyNewerData = contracts.putIfAbsent(quote.contract(), data);
            if (possiblyNewerData != null)
                return possiblyNewerData.setLatestQuote(quote);
            return true;
        }
    }
}
