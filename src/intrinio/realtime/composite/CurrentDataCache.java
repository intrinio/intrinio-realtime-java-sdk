package intrinio.realtime.composite;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CurrentDataCache implements DataCache
{
    //region Data Members
    private final ConcurrentHashMap<String, CurrentSecurityData> data;
    private final Map<String, SecurityData> readonlyData;
    private OnSupplementalDatumUpdated onSupplementalDatumUpdated;
    private OnSecuritySupplementalDatumUpdated onSecuritySupplementalDatumUpdated;
    private OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated;
    private OnEquitiesQuoteUpdated onEquitiesQuoteUpdated;
    private OnEquitiesTradeUpdated onEquitiesTradeUpdated;
    private OnOptionsQuoteUpdated onOptionsQuoteUpdated;
    private OnOptionsTradeUpdated onOptionsTradeUpdated;
    private OnOptionsRefreshUpdated onOptionsRefreshUpdated;
    private final ConcurrentHashMap<String, Double> supplementaryData;
    private final Map<String, Double> readonlySupplementaryData;
    //endregion Data Members

    //region Constructors
    public CurrentDataCache(){
        this.data = new ConcurrentHashMap<String, CurrentSecurityData>();
        this.readonlyData = java.util.Collections.unmodifiableMap(this.data);
        this.onSupplementalDatumUpdated = null;
        this.onSecuritySupplementalDatumUpdated = null;
        this.onOptionsContractSupplementalDatumUpdated = null;
        this.onEquitiesQuoteUpdated = null;
        this.onEquitiesTradeUpdated = null;
        this.onOptionsQuoteUpdated = null;
        this.onOptionsTradeUpdated = null;
        this.onOptionsRefreshUpdated = null;
        this.supplementaryData = new ConcurrentHashMap<String, Double>();
        this.readonlySupplementaryData = java.util.Collections.unmodifiableMap(supplementaryData);
    }
    //endregion Constructors

    //region Public Methods
    public Double getsupplementaryDatum(String key){
        return supplementaryData.getOrDefault(key, null);
    }

    public boolean setsupplementaryDatum(String key, double datum){
        boolean isSet = datum == supplementaryData.compute(key, (k, oldValue) -> datum);
        if (this.onSupplementalDatumUpdated != null){
            try{
                this.onSupplementalDatumUpdated.onSupplementalDatumUpdated(key, datum, this);
            }catch (Exception e){
                Log("Error in setsupplementaryDatum Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    public Map<String, Double> getAllSupplementaryData(){return readonlySupplementaryData;}

    public SecurityData getSecurityData(String tickerSymbol){
        return data.getOrDefault(tickerSymbol, null);
    }

    public Map<String, SecurityData> getAllSecurityData(){
        return readonlyData;
    }

    public intrinio.realtime.equities.Trade getEquityTrade(String tickerSymbol){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getEquitiesTrade();
        else return null;
    }

    public boolean setEquityTrade(intrinio.realtime.equities.Trade trade){
        String symbol = trade.symbol();
        CurrentSecurityData securityData;
        if (data.containsKey(symbol)){
            securityData = data.get(symbol);
        }
        else {
            CurrentSecurityData newData = new CurrentSecurityData(symbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(symbol, newData);
            securityData = Objects.requireNonNullElse(possiblyNewerData, newData);
        }
        return securityData.setEquitiesTrade(trade, this.onEquitiesTradeUpdated, this);
    }

    public intrinio.realtime.equities.Quote getEquityQuote(String tickerSymbol){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getEquitiesQuote();
        else return null;
    }

    public boolean setEquityQuote(intrinio.realtime.equities.Quote quote){
        String symbol = quote.symbol();
        CurrentSecurityData securityData;
        if (data.containsKey(symbol)){
            securityData = data.get(symbol);
        }
        else {
            CurrentSecurityData newData = new CurrentSecurityData(symbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(symbol, newData);
            securityData = Objects.requireNonNullElse(possiblyNewerData, newData);
        }
        return securityData.setEquitiesQuote(quote, this.onEquitiesQuoteUpdated, this);
    }

    public OptionsContractData getOptionsContractData(String tickerSymbol, String contract){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getOptionsContractData(contract);
        else return null;
    }

    public intrinio.realtime.options.Trade getOptionsTrade(String tickerSymbol, String contract){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getOptionsContractTrade(contract);
        else return null;
    }

    public boolean setOptionsTrade(intrinio.realtime.options.Trade trade){
        String underlyingSymbol = trade.getUnderlyingSymbol();
        CurrentSecurityData securityData;
        if (data.containsKey(underlyingSymbol)){
            securityData = data.get(underlyingSymbol);
        }
        else {
            CurrentSecurityData newData = new CurrentSecurityData(underlyingSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(underlyingSymbol, newData);
            securityData = Objects.requireNonNullElse(possiblyNewerData, newData);
        }
        return securityData.setOptionsTrade(trade, this.onOptionsTradeUpdated, this);
    }

    public intrinio.realtime.options.Quote getOptionsQuote(String tickerSymbol, String contract){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getOptionsContractQuote(contract);
        else return null;
    }

    public boolean setOptionsQuote(intrinio.realtime.options.Quote quote){
        String underlyingSymbol = quote.getUnderlyingSymbol();
        CurrentSecurityData securityData;
        if (data.containsKey(underlyingSymbol)){
            securityData = data.get(underlyingSymbol);
        }
        else {
            CurrentSecurityData newData = new CurrentSecurityData(underlyingSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(underlyingSymbol, newData);
            securityData = Objects.requireNonNullElse(possiblyNewerData, newData);
        }
        return securityData.setOptionsQuote(quote, this.onOptionsQuoteUpdated, this);
    }

    public intrinio.realtime.options.Refresh getOptionsRefresh(String tickerSymbol, String contract){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getOptionsContractRefresh(contract);
        else return null;
    }

    public boolean setOptionsRefresh(intrinio.realtime.options.Refresh refresh){
        String underlyingSymbol = refresh.getUnderlyingSymbol();
        CurrentSecurityData securityData;
        if (data.containsKey(underlyingSymbol)){
            securityData = data.get(underlyingSymbol);
        }
        else {
            CurrentSecurityData newData = new CurrentSecurityData(underlyingSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(underlyingSymbol, newData);
            securityData = Objects.requireNonNullElse(possiblyNewerData, newData);
        }
        return securityData.setOptionsRefresh(refresh, this.onOptionsRefreshUpdated, this);
    }

    public Double getSecuritySupplementalDatum(String tickerSymbol, String key){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getSupplementaryDatum(key);
        else return null;
    }

    public boolean setSecuritySupplementalDatum(String tickerSymbol, String key, double datum){
        boolean result = false;
        CurrentSecurityData currentSecurityData;
        if (data.containsKey(tickerSymbol)) {
            currentSecurityData = data.get(tickerSymbol);
        }
        else {
            CurrentSecurityData newData = new CurrentSecurityData(tickerSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(tickerSymbol, newData);
            currentSecurityData = possiblyNewerData == null ? newData : possiblyNewerData;
        }
        return currentSecurityData.setSupplementaryDatum(key, datum, this.onSecuritySupplementalDatumUpdated, this);
    }

    public Double getOptionsContractSupplementalDatum(String tickerSymbol, String contract, String key){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getOptionsContractSupplementalDatum(contract, key);
        else return null;
    }

    public boolean setOptionSupplementalDatum(String tickerSymbol, String contract, String key, double datum){
        CurrentSecurityData currentSecurityData;
        if (data.containsKey(tickerSymbol)) {
            currentSecurityData = data.get(tickerSymbol);
        }
        else {
            CurrentSecurityData newData = new CurrentSecurityData(tickerSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(tickerSymbol, newData);
            currentSecurityData = possiblyNewerData == null ? newData : possiblyNewerData;
        }
        return currentSecurityData.setOptionsContractSupplementalDatum(contract, key, datum, onOptionsContractSupplementalDatumUpdated, this);
    }

    public void setOnSupplementalDatumUpdated(OnSupplementalDatumUpdated onSupplementalDatumUpdated){
        this.onSupplementalDatumUpdated = onSupplementalDatumUpdated;
    }

    public void setOnSecuritySupplementalDatumUpdated(OnSecuritySupplementalDatumUpdated onSecuritySupplementalDatumUpdated){
        this.onSecuritySupplementalDatumUpdated = onSecuritySupplementalDatumUpdated;
    }

    public void setOnOptionSupplementalDatumUpdated(OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated){
        this.onOptionsContractSupplementalDatumUpdated = onOptionsContractSupplementalDatumUpdated;
    }

    public void setOnEquitiesQuoteUpdated(OnEquitiesQuoteUpdated onEquitiesQuoteUpdated){
        this.onEquitiesQuoteUpdated = onEquitiesQuoteUpdated;
    }

    public void setOnEquitiesTradeUpdated(OnEquitiesTradeUpdated onEquitiesTradeUpdated){
        this.onEquitiesTradeUpdated = onEquitiesTradeUpdated;
    }

    public void setOnOptionsQuoteUpdated(OnOptionsQuoteUpdated onOptionsQuoteUpdated){
        this.onOptionsQuoteUpdated = onOptionsQuoteUpdated;
    }

    public void setOnOptionsTradeUpdated(OnOptionsTradeUpdated onOptionsTradeUpdated){
        this.onOptionsTradeUpdated = onOptionsTradeUpdated;
    }

    public void setOnOptionsRefreshUpdated(OnOptionsRefreshUpdated onOptionsRefreshUpdated){
        this.onOptionsRefreshUpdated = onOptionsRefreshUpdated;
    }
    //endregion Public Methods

    //region Private Methods
    private void Log(String message){
        System.out.println(message);
    }

    //endregion Private Methods
}
