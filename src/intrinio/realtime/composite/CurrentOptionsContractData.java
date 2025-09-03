package intrinio.realtime.composite;

import intrinio.realtime.options.Trade;
import intrinio.realtime.options.Quote;
import intrinio.realtime.options.Refresh;
import intrinio.realtime.options.UnusualActivity;
import intrinio.realtime.options.QuoteType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.Map;

/**
 * Not for Use yet. Subject to change.
 */
class CurrentOptionsContractData implements OptionsContractData {
    private final String contract;
    private Trade latestTrade;
    private Quote latestQuote;
    private Refresh latestRefresh;
    private UnusualActivity latestUnusualActivity;
    private final ConcurrentHashMap<String, Double> supplementaryData = new ConcurrentHashMap<>();
    private final Map<String, Double> readonlySupplementaryData = Collections.unmodifiableMap(supplementaryData);
    private final ConcurrentHashMap<String, Greek> greekData = new ConcurrentHashMap<>();
    private final Map<String, Greek> readonlyGreekData = Collections.unmodifiableMap(greekData);

    public CurrentOptionsContractData(String contract,
                                      Trade latestTrade,
                                      Quote latestQuote,
                                      Refresh latestRefresh,
                                      UnusualActivity latestUnusualActivity) {
        this.contract = contract;
        this.latestTrade = latestTrade;
        this.latestQuote = latestQuote;
        this.latestRefresh = latestRefresh;
        this.latestUnusualActivity = latestUnusualActivity;
    }

    @Override
    public String getContract() {
        return this.contract;
    }

    @Override
    public Trade getLatestTrade() {
        return this.latestTrade;
    }

    @Override
    public Quote getLatestQuote() {
        return this.latestQuote;
    }

    @Override
    public Refresh getLatestRefresh() {
        return this.latestRefresh;
    }

    @Override
    public UnusualActivity getLatestUnusualActivity() {
        return this.latestUnusualActivity;
    }

    @Override
    public boolean setTrade(Trade trade) {
        //dirty set
        if (this.latestTrade == null || (trade != null && trade.timestamp() > this.latestTrade.timestamp())) {
            this.latestTrade = trade;
            return true;
        }
        return false;
    }

    @Override
    public boolean setTrade(Trade trade, OnOptionsTradeUpdated onOptionsTradeUpdated, SecurityData securityData, DataCache dataCache) {
        boolean isSet = setTrade(trade);
        if (isSet && onOptionsTradeUpdated != null) {
            try {
                onOptionsTradeUpdated.onOptionsTradeUpdated(this, dataCache, securityData, trade);
            } catch (Exception e) {
                Log("Error in OnOptionsTradeUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    @Override
    public boolean setQuote(Quote quote) {
        //dirty set
        if (this.latestQuote == null || (quote != null && quote.timestamp() > this.latestQuote.timestamp())) {
            this.latestQuote = quote;
            return true;
        }
        return false;
    }

    @Override
    public boolean setQuote(Quote quote, OnOptionsQuoteUpdated onOptionsQuoteUpdated, SecurityData securityData, DataCache dataCache) {
        boolean isSet = this.setQuote(quote);
        if (isSet && onOptionsQuoteUpdated != null) {
            try {
                onOptionsQuoteUpdated.onOptionsQuoteUpdated(this, dataCache, securityData, quote);
            } catch (Exception e) {
                Log("Error in onOptionsQuoteUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    @Override
    public boolean setRefresh(Refresh refresh) {
        this.latestRefresh = refresh;
        return true;
    }

    @Override
    public boolean setRefresh(Refresh refresh, OnOptionsRefreshUpdated onOptionsRefreshUpdated, SecurityData securityData, DataCache dataCache) {
        boolean isSet = this.setRefresh(refresh);
        if (isSet && onOptionsRefreshUpdated != null) {
            try {
                onOptionsRefreshUpdated.onOptionsRefreshUpdated(this, dataCache, securityData, refresh);
            } catch (Exception e) {
                Log("Error in onOptionsRefreshUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    @Override
    public boolean setUnusualActivity(UnusualActivity unusualActivity) {
        this.latestUnusualActivity = unusualActivity;
        return true;
    }

    @Override
    public boolean setUnusualActivity(UnusualActivity unusualActivity, OnOptionsUnusualActivityUpdated onOptionsUnusualActivityUpdated, SecurityData securityData, DataCache dataCache) {
        boolean isSet = this.setUnusualActivity(unusualActivity);
        if (isSet && onOptionsUnusualActivityUpdated != null) {
            try {
                onOptionsUnusualActivityUpdated.onOptionsUnusualActivityUpdated(this, dataCache, securityData, unusualActivity);
            } catch (Exception e) {
                Log("Error in onOptionsUnusualActivityUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    @Override
    public Double getSupplementaryDatum(String key) {
        return supplementaryData.getOrDefault(key, null);
    }

    @Override
    public boolean setSupplementaryDatum(String key, Double datum, SupplementalDatumUpdate update) {
        Double newValue = supplementaryData.compute(key, (k, oldValue) -> update.supplementalDatumUpdate(k, oldValue, datum));
        return java.util.Objects.equals(datum, newValue);
    }

    @Override
    public boolean setSupplementaryDatum(String key, Double datum, OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated, SecurityData securityData, DataCache dataCache, SupplementalDatumUpdate update) {
        boolean result = setSupplementaryDatum(key, datum, update);
        if (result && onOptionsContractSupplementalDatumUpdated != null) {
            try {
                onOptionsContractSupplementalDatumUpdated.onOptionsContractSupplementalDatumUpdated(key, datum, this, securityData, dataCache);
            } catch (Exception e) {
                Log("Error in onOptionsContractSupplementalDatumUpdated Callback: " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public Map<String, Double> getAllSupplementaryData() {
        return readonlySupplementaryData;
    }

    @Override
    public Greek getGreekData(String key) {
        return greekData.getOrDefault(key, null);
    }

    @Override
    public boolean setGreekData(String key, Greek datum, GreekDataUpdate update) {
        Greek newValue = greekData.compute(key, (k, oldValue) -> update.greekDataUpdate(k, oldValue, datum));
        return (newValue != null && datum != null && newValue.equals(datum))
                || (newValue == null && datum == null);
    }

    @Override
    public boolean setGreekData(String key, Greek datum, OnOptionsContractGreekDataUpdated onOptionsContractGreekDataUpdated, SecurityData securityData, DataCache dataCache, GreekDataUpdate update) {
        boolean result = setGreekData(key, datum, update);
        if (result && onOptionsContractGreekDataUpdated != null) {
            try {
                onOptionsContractGreekDataUpdated.onOptionsContractGreekDataUpdated(key, datum, this, securityData, dataCache);
            } catch (Exception e) {
                Log("Error in onOptionsContractGreekDataUpdated Callback: " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public Map<String, Greek> getAllGreekData() {
        return readonlyGreekData;
    }

    private void Log(String message){
        System.out.println(message);
    }
}