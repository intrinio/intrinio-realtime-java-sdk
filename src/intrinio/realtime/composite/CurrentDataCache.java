package intrinio.realtime.composite;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CurrentDataCache
{
    //region Data Members
    private volatile Double riskFreeInterestRate;
    private final ConcurrentHashMap<String, CurrentSecurityData> data;
    private final Map<String, CurrentSecurityData> readonlyData;
    private OnRiskFreeInterestRateUpdated onRiskFreeInterestRateUpdated;
    private OnDividendYieldUpdated onDividendYieldUpdated;
    private OnEquitiesQuoteUpdated onEquitiesQuoteUpdated;
    private OnEquitiesTradeUpdated onEquitiesTradeUpdated;
    private OnOptionsQuoteUpdated onOptionsQuoteUpdated;
    private OnOptionsTradeUpdated onOptionsTradeUpdated;
    //endregion Data Members

    //region Constructors
    public CurrentDataCache(){
        riskFreeInterestRate = null;
        this.data = new ConcurrentHashMap<String, CurrentSecurityData>();
        this.readonlyData = java.util.Collections.unmodifiableMap(this.data);
        this.onRiskFreeInterestRateUpdated = null;
        this.onDividendYieldUpdated = null;
        this.onEquitiesQuoteUpdated = null;
        this.onEquitiesTradeUpdated = null;
        this.onOptionsQuoteUpdated = null;
        this.onOptionsTradeUpdated = null;
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
        boolean result = false;
        CurrentSecurityData currentSecurityData;
        if (data.containsKey(tickerSymbol)) {
            currentSecurityData = data.get(tickerSymbol);
            result = currentSecurityData.setDividendYield(dividendYield);
        }
        else {
            CurrentSecurityData newData = new CurrentSecurityData(tickerSymbol);
            CurrentSecurityData possiblyNewerData = data.putIfAbsent(tickerSymbol, newData);
            currentSecurityData = possiblyNewerData == null ? newData : possiblyNewerData;
            result = currentSecurityData.setDividendYield(dividendYield);
        }
        if (this.onDividendYieldUpdated != null){
            try{
                this.onDividendYieldUpdated.onDividendYieldUpdated(dividendYield, currentSecurityData, this);
            }catch (Exception e){
                System.out.println("Error in onRiskFreeInterestRateUpdated Callback: " + e.getMessage());
            }
        }
        return result;
    }

    public Double getRiskFreeInterestRate(){
        return this.riskFreeInterestRate;
    }

    public boolean setRiskFreeInterestRate(double riskFreeInterestRate){
        if (!Double.isNaN(riskFreeInterestRate) && !Double.isInfinite(riskFreeInterestRate)) {
            this.riskFreeInterestRate = riskFreeInterestRate;
            if (this.onRiskFreeInterestRateUpdated != null){
                try{
                    this.onRiskFreeInterestRateUpdated.onRiskFreeInterestRateUpdated(riskFreeInterestRate, this);
                }catch (Exception e){
                    System.out.println("Error in onRiskFreeInterestRateUpdated Callback: " + e.getMessage());
                }
            }
            return true;
        }
        else return false;
    }

    public void setOnRiskFreeInterestRateUpdated(OnRiskFreeInterestRateUpdated onRiskFreeInterestRateUpdated){
        this.onRiskFreeInterestRateUpdated = onRiskFreeInterestRateUpdated;
    }

    public void setOnDividendYieldUpdated(OnDividendYieldUpdated onDividendYieldUpdated){
        this.onDividendYieldUpdated = onDividendYieldUpdated;
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
    //endregion Public Methods

    //region Private Methods


    //endregion Private Methods
}
