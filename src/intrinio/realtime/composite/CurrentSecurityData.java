package intrinio.realtime.composite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CurrentSecurityData implements SecurityData{
    private final String tickerSymbol;
    private volatile intrinio.realtime.equities.Trade equitiesTrade;
    private volatile intrinio.realtime.equities.Quote equitiesQuote;
    private final ConcurrentHashMap<String, CurrentOptionsContractData> contracts;
    private final Map<String, OptionsContractData> readonlyContracts;
    private final ConcurrentHashMap<String, Double> supplementaryData;
    private final Map<String, Double> readonlySupplementaryData;

    public CurrentSecurityData(String tickerSymbol){
        this.tickerSymbol = tickerSymbol;
        this.contracts = new ConcurrentHashMap<String, CurrentOptionsContractData>();
        this.readonlyContracts = java.util.Collections.unmodifiableMap(contracts);
        this.supplementaryData = new ConcurrentHashMap<String, Double>();
        this.readonlySupplementaryData = java.util.Collections.unmodifiableMap(supplementaryData);
    }

    public String getTickerSymbol(){
        return tickerSymbol;
    }

    public Double getSupplementaryDatum(String key){
        return supplementaryData.getOrDefault(key, null);
    }

    public boolean setSupplementaryDatum(String key, double datum){
        return datum == supplementaryData.compute(key, (k, oldValue) -> datum);
    }

    boolean setSupplementaryDatum(String key, double datum, OnSecuritySupplementalDatumUpdated onSecuritySupplementalDatumUpdated, CurrentDataCache currentDataCache){
        boolean result = setSupplementaryDatum(key, datum);
        if (result && onSecuritySupplementalDatumUpdated != null){
            try{
                onSecuritySupplementalDatumUpdated.onSecuritySupplementalDatumUpdated(key, datum, this, currentDataCache);
            }catch (Exception e){
                System.out.println("Error in onSecuritySupplementalDatumUpdated Callback: " + e.getMessage());
            }
        }
        return result;
    }

    public Map<String, Double> getAllSupplementaryData(){return readonlySupplementaryData;}

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
        return contracts.values().stream().map(CurrentOptionsContractData::getContract).collect(Collectors.toList());
    }

    public boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade){
        //dirty set
        if ((equitiesTrade == null) || (trade.timestamp() > equitiesTrade.timestamp())) {
            equitiesTrade = trade;
            return true;
        }
        return false;
    }

    boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade, OnEquitiesTradeUpdated onEquitiesTradeUpdated, DataCache dataCache){
        boolean isSet = this.setEquitiesTrade(trade);
        if (isSet && onEquitiesTradeUpdated != null){
            try{
                onEquitiesTradeUpdated.onEquitiesTradeUpdated(this, dataCache);
            }catch (Exception e){
                System.out.println("Error in onEquitiesTradeUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    public boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote){
        //dirty set
        if ((equitiesQuote == null) || (quote.timestamp() > equitiesQuote.timestamp())) {
            equitiesQuote = quote;
            return true;
        }
        return false;
    }

    boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote, OnEquitiesQuoteUpdated onEquitiesQuoteUpdated, DataCache dataCache){
        boolean isSet = this.setEquitiesQuote(quote);
        if (isSet && onEquitiesQuoteUpdated != null){
            try{
                onEquitiesQuoteUpdated.onEquitiesQuoteUpdated(this, dataCache);
            }catch (Exception e){
                System.out.println("Error in onEquitiesQuoteUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    public intrinio.realtime.options.Trade getOptionsContractTrade(String contract){
        if (contracts.containsKey(contract))
            return contracts.get(contract).getTrade();
        else return null;
    }

    public boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade){
        //dirty set
        if (contracts.containsKey(trade.contract())){
            return contracts.get(trade.contract()).setTrade(trade);
        }
        else{
            CurrentOptionsContractData data = new CurrentOptionsContractData(trade.contract(), trade, null);
            CurrentOptionsContractData possiblyNewerData = contracts.putIfAbsent(trade.contract(), data);
            if (possiblyNewerData != null)
                return possiblyNewerData.setTrade(trade);
            return true;
        }
    }

    public boolean setOptionsTrade(intrinio.realtime.options.Trade trade, OnOptionsTradeUpdated onOptionsTradeUpdated, DataCache dataCache){
        CurrentOptionsContractData currentOptionsContractData;
        String contract = trade.contract();
        if (contracts.containsKey(contract)) {
            currentOptionsContractData = contracts.get(contract);
        }
        else {
            CurrentOptionsContractData newData = new CurrentOptionsContractData(contract, null, null);
            CurrentOptionsContractData possiblyNewerData = contracts.putIfAbsent(contract, newData);
            currentOptionsContractData = possiblyNewerData == null ? newData : possiblyNewerData;
        }
        return currentOptionsContractData.setTrade(trade, onOptionsTradeUpdated, this, dataCache);
    }

    public intrinio.realtime.options.Quote getOptionsContractQuote(String contract){
        if (contracts.containsKey(contract))
            return contracts.get(contract).getQuote();
        else return null;
    }

    public boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote){
        //dirty set
        if (contracts.containsKey(quote.contract())){
            return contracts.get(quote.contract()).setQuote(quote);
        }
        else{
            CurrentOptionsContractData data = new CurrentOptionsContractData(quote.contract(), null, quote);
            CurrentOptionsContractData possiblyNewerData = contracts.putIfAbsent(quote.contract(), data);
            if (possiblyNewerData != null)
                return possiblyNewerData.setQuote(quote);
            return true;
        }
    }

    public boolean setOptionsQuote(intrinio.realtime.options.Quote quote, OnOptionsQuoteUpdated onOptionsQuoteUpdated, DataCache dataCache){
        CurrentOptionsContractData currentOptionsContractData;
        String contract = quote.contract();
        if (contracts.containsKey(contract)) {
            currentOptionsContractData = contracts.get(contract);
        }
        else {
            CurrentOptionsContractData newData = new CurrentOptionsContractData(contract, null, null);
            CurrentOptionsContractData possiblyNewerData = contracts.putIfAbsent(contract, newData);
            currentOptionsContractData = possiblyNewerData == null ? newData : possiblyNewerData;
        }
        return currentOptionsContractData.setQuote(quote, onOptionsQuoteUpdated, this, dataCache);
    }

    public Double getOptionsContractSupplementalDatum(String contract, String key){
        if (contracts.containsKey(contract))
            return contracts.get(contract).getSupplementaryDatum(key);
        else return null;
    }

    public boolean setOptionsContractSupplementalDatum(String contract, String key, double datum){
        CurrentOptionsContractData currentOptionsContractData;
        if (contracts.containsKey(contract)) {
            currentOptionsContractData = contracts.get(contract);
        }
        else {
            CurrentOptionsContractData newData = new CurrentOptionsContractData(contract, null, null);
            CurrentOptionsContractData possiblyNewerData = contracts.putIfAbsent(contract, newData);
            currentOptionsContractData = possiblyNewerData == null ? newData : possiblyNewerData;
        }
        return currentOptionsContractData.setSupplementaryDatum(key, datum);
    }

    boolean setOptionsContractSupplementalDatum(String contract, String key, double datum, OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated, DataCache dataCache){
        CurrentOptionsContractData currentOptionsContractData;
        if (contracts.containsKey(contract)) {
            currentOptionsContractData = contracts.get(contract);
        }
        else {
            CurrentOptionsContractData newData = new CurrentOptionsContractData(contract, null, null);
            CurrentOptionsContractData possiblyNewerData = contracts.putIfAbsent(contract, newData);
            currentOptionsContractData = possiblyNewerData == null ? newData : possiblyNewerData;
        }
        return currentOptionsContractData.setSupplementaryDatum(key, datum, onOptionsContractSupplementalDatumUpdated, this, dataCache);
    }
}
