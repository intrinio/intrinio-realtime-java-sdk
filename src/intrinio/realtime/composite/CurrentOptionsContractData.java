package intrinio.realtime.composite;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CurrentOptionsContractData implements OptionsContractData {
    private final String contract;
    private volatile intrinio.realtime.options.Trade latestTrade;
    private volatile intrinio.realtime.options.Quote latestQuote;
    private volatile intrinio.realtime.options.Refresh latestRefresh;
    private final ConcurrentHashMap<String, Double> supplementaryData;
    private final Map<String, Double> readonlySupplementaryData;

    public CurrentOptionsContractData(String contract, intrinio.realtime.options.Trade latestTrade, intrinio.realtime.options.Quote latestQuote, intrinio.realtime.options.Refresh latestRefresh){
        this.contract = contract;
        this.latestTrade = latestTrade;
        this.latestQuote = latestQuote;
        this.latestRefresh = latestRefresh;
        this.supplementaryData = new ConcurrentHashMap<String, Double>();
        this.readonlySupplementaryData = java.util.Collections.unmodifiableMap(supplementaryData);
    }

    public String getContract(){
        return this.contract;
    }

    public intrinio.realtime.options.Trade getTrade(){
        return this.latestTrade;
    }

    public intrinio.realtime.options.Quote getQuote(){
        return this.latestQuote;
    }

    public intrinio.realtime.options.Refresh getRefresh(){
        return this.latestRefresh;
    }

    public boolean setTrade(intrinio.realtime.options.Trade trade){
        //dirty set
        if ((latestTrade == null) || (trade.timestamp() > latestTrade.timestamp())) {
            latestTrade = trade;
            return true;
        }
        return false;
    }

    boolean setTrade(intrinio.realtime.options.Trade trade, OnOptionsTradeUpdated onOptionsTradeUpdated, SecurityData securityData, DataCache dataCache){
        boolean isSet = this.setTrade(trade);
        if (isSet && onOptionsTradeUpdated != null){
            try{
                onOptionsTradeUpdated.onOptionsTradeUpdated(this, dataCache, securityData);
            }catch (Exception e){
                Log("Error in onOptionsTradeUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    public boolean setQuote(intrinio.realtime.options.Quote quote){
        //dirty set
        if ((latestQuote == null) || (quote.timestamp() > latestQuote.timestamp())) {
            latestQuote = quote;
            return true;
        }
        return false;
    }

    boolean setQuote(intrinio.realtime.options.Quote quote, OnOptionsQuoteUpdated onOptionsQuoteUpdated, SecurityData securityData, DataCache dataCache){
        boolean isSet = this.setQuote(quote);
        if (isSet && onOptionsQuoteUpdated != null){
            try{
                onOptionsQuoteUpdated.onOptionsQuoteUpdated(this, dataCache, securityData);
            }catch (Exception e){
                Log("Error in onOptionsQuoteUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    public boolean setRefresh(intrinio.realtime.options.Refresh refresh){
        latestRefresh = refresh;
        return true;
    }

    boolean setRefresh(intrinio.realtime.options.Refresh refresh, OnOptionsRefreshUpdated onOptionsRefreshUpdated, SecurityData securityData, DataCache dataCache){
        boolean isSet = this.setRefresh(refresh);
        if (isSet && onOptionsRefreshUpdated != null){
            try{
                onOptionsRefreshUpdated.onOptionsRefreshUpdated(this, dataCache, securityData);
            }catch (Exception e){
                Log("Error in onOptionsRefreshUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    public Double getSupplementaryDatum(String key){
        return supplementaryData.getOrDefault(key, null);
    }

    public boolean setSupplementaryDatum(String key, double datum){
        return datum == supplementaryData.compute(key, (k, oldValue) -> datum);
    }

    boolean setSupplementaryDatum(String key, double datum, OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated, SecurityData securityData, DataCache dataCache){
        boolean result = setSupplementaryDatum(key, datum);
        if (result && onOptionsContractSupplementalDatumUpdated != null){
            try{
                onOptionsContractSupplementalDatumUpdated.onOptionsContractSupplementalDatumUpdated(key, datum, this, securityData, dataCache);
            }catch (Exception e){
                Log("Error in onOptionsContractSupplementalDatumUpdated Callback: " + e.getMessage());
            }
        }
        return result;
    }

    public Map<String, Double> getAllSupplementaryData(){return readonlySupplementaryData;}

    //region Private Methods
    private void Log(String message){
        System.out.println(message);
    }

    //endregion Private Methods
}
