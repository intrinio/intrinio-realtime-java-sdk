package intrinio.realtime.composite;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CurrentDataCache
{
    //region Data Members
    private volatile Double riskFreeInterestRate;
    private final ConcurrentHashMap<String, CurrentSecurityData> data;
    private final Map<String, CurrentSecurityData> readonlyData;
    //endregion Data Members

    //region Constructors
    public CurrentDataCache(){
        riskFreeInterestRate = null;
        this.data = new ConcurrentHashMap<String, CurrentSecurityData>();
        this.readonlyData = java.util.Collections.unmodifiableMap(this.data);
    }
    //endregion Constructors

    //region Public Methods
    public CurrentSecurityData getCurrentSecurityData(String tickerSymbol){
        return data.getOrDefault(tickerSymbol, null);
    }

    public Map<String, CurrentSecurityData> getAllCurrentSecurityData(){
        return readonlyData;
    }

    public intrinio.realtime.equities.Trade getEquityTrade(String tickerSymbol){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getEquitiesTrade();
        else return null;
    }

    public boolean setEquityTrade(intrinio.realtime.equities.Trade trade){
        String symbol = trade.symbol();
        if (data.containsKey(symbol))
            return data.get(symbol).setEquitiesTrade(trade);
        else {
            CurrentSecurityData newData = new CurrentSecurityData(symbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(symbol, newData);
            return Objects.requireNonNullElse(possiblyNewerData, newData).setEquitiesTrade(trade);
        }
    }

    public intrinio.realtime.equities.Quote getEquityQuote(String tickerSymbol){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getEquitiesQuote();
        else return null;
    }

    public boolean setEquityQuote(intrinio.realtime.equities.Quote quote){
        String symbol = quote.symbol();
        if (data.containsKey(symbol))
            return data.get(symbol).setEquitiesQuote(quote);
        else {
            CurrentSecurityData newData = new CurrentSecurityData(symbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(symbol, newData);
            return Objects.requireNonNullElse(possiblyNewerData, newData).setEquitiesQuote(quote);
        }
    }

    public OptionsContractData getOptionsContractData(String tickerSymbol, String contract){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getOptionsContractData(contract);
        else return null;
    }

    public intrinio.realtime.options.Trade getOptionsTrade(String tickerSymbol, String contract){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getOptionsTrade(contract);
        else return null;
    }

    public boolean setOptionsTrade(intrinio.realtime.options.Trade trade){
        String underlyingSymbol = trade.getUnderlyingSymbol();
        if (data.containsKey(underlyingSymbol))
            return data.get(underlyingSymbol).setOptionsTrade(trade);
        else {
            CurrentSecurityData newData = new CurrentSecurityData(underlyingSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(underlyingSymbol, newData);
            return Objects.requireNonNullElse(possiblyNewerData, newData).setOptionsTrade(trade);
        }
    }

    public intrinio.realtime.options.Quote getOptionsQuote(String tickerSymbol, String contract){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getOptionsQuote(contract);
        else return null;
    }

    public boolean setOptionsQuote(intrinio.realtime.options.Quote quote){
        String underlyingSymbol = quote.getUnderlyingSymbol();
        if (data.containsKey(underlyingSymbol))
            return data.get(underlyingSymbol).setOptionsQuote(quote);
        else {
            CurrentSecurityData newData = new CurrentSecurityData(underlyingSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(underlyingSymbol, newData);
            return Objects.requireNonNullElse(possiblyNewerData, newData).setOptionsQuote(quote);
        }
    }

    public Double getDividendYield(String tickerSymbol){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).getDividendYield();
        else return null;
    }

    public boolean setDividendYield(String tickerSymbol, double dividendYield){
        if (data.containsKey(tickerSymbol))
            return data.get(tickerSymbol).setDividendYield(dividendYield);
        else {
            CurrentSecurityData newData = new CurrentSecurityData(tickerSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(tickerSymbol, newData);
            return Objects.requireNonNullElse(possiblyNewerData, newData).setDividendYield(dividendYield);
        }
    }

    public Double getRiskFreeInterestRate(){
        return this.riskFreeInterestRate;
    }

    public boolean setRiskFreeInterestRate(double riskFreeInterestRate){
        if (!Double.isNaN(riskFreeInterestRate) && !Double.isInfinite(riskFreeInterestRate)) {
            this.riskFreeInterestRate = riskFreeInterestRate;
            return true;
        }
        else return false;
    }
    //endregion Public Methods

    //region Private Methods


    //endregion Private Methods
}
