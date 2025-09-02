package intrinio.realtime.composite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class CurrentSecurityData implements SecurityData {
    private final String tickerSymbol;
    private intrinio.realtime.equities.Trade latestTrade;
    private intrinio.realtime.equities.Quote latestAskQuote;
    private intrinio.realtime.equities.Quote latestBidQuote;
    private final ConcurrentHashMap<String, OptionsContractData> contracts = new ConcurrentHashMap<>();
    private final Map<String, OptionsContractData> readonlyContracts = Collections.unmodifiableMap(contracts);
    private final ConcurrentHashMap<String, Double> supplementaryData = new ConcurrentHashMap<>();
    private final Map<String, Double> readonlySupplementaryData = Collections.unmodifiableMap(supplementaryData);

    public CurrentSecurityData(String tickerSymbol,
                        intrinio.realtime.equities.Trade latestTrade,
                        intrinio.realtime.equities.Quote latestAskQuote,
                        intrinio.realtime.equities.Quote latestBidQuote) {
        this.tickerSymbol = tickerSymbol;
        this.latestTrade = latestTrade;
        this.latestAskQuote = latestAskQuote;
        this.latestBidQuote = latestBidQuote;
    }

    @Override
    public String getTickerSymbol() {
        return tickerSymbol;
    }

    @Override
    public intrinio.realtime.equities.Trade getLatestEquitiesTrade() {
        return latestTrade;
    }

    @Override
    public intrinio.realtime.equities.Quote getLatestEquitiesAskQuote() {
        return latestAskQuote;
    }

    @Override
    public intrinio.realtime.equities.Quote getLatestEquitiesBidQuote() {
        return latestBidQuote;
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
    public boolean setSupplementaryDatum(String key, Double datum, OnSecuritySupplementalDatumUpdated onSecuritySupplementalDatumUpdated, DataCache dataCache, SupplementalDatumUpdate update) {
        boolean result = setSupplementaryDatum(key, datum, update);
        if (result && onSecuritySupplementalDatumUpdated != null) {
            try {
                onSecuritySupplementalDatumUpdated.onSecuritySupplementalDatumUpdated(key, datum, this, dataCache);
            } catch (Exception e) {
                Log("Error in onSecuritySupplementalDatumUpdated Callback: " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public Map<String, Double> getAllSupplementaryData() {
        return readonlySupplementaryData;
    }

    @Override
    public boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade) {
        //dirty set
        if (this.latestTrade == null || (trade != null && trade.timestamp() > this.latestTrade.timestamp())) {
            this.latestTrade = trade;
            return true;
        }
        return false;
    }

    @Override
    public boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade, OnEquitiesTradeUpdated onEquitiesTradeUpdated, DataCache dataCache) {
        boolean isSet = setEquitiesTrade(trade);
        if (isSet && onEquitiesTradeUpdated != null) {
            try {
                onEquitiesTradeUpdated.onEquitiesTradeUpdated(this, dataCache, trade);
            } catch (Exception e) {
                Log("Error in onEquitiesTradeUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    @Override
    public boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote) {
        if (quote != null) {
            if (quote.type() == intrinio.realtime.equities.QuoteType.ASK) {
                if (this.latestAskQuote == null || (quote.timestamp() > this.latestAskQuote.timestamp())) {
                    this.latestAskQuote = quote;
                    return true;
                }
                return false;
            } else { // Bid
                if (this.latestBidQuote == null || (quote.timestamp() > this.latestBidQuote.timestamp())) {
                    this.latestBidQuote = quote;
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote, OnEquitiesQuoteUpdated onEquitiesQuoteUpdated, DataCache dataCache) {
        boolean isSet = this.setEquitiesQuote(quote);
        if (isSet && onEquitiesQuoteUpdated != null) {
            try {
                onEquitiesQuoteUpdated.onEquitiesQuoteUpdated(this, dataCache, quote);
            } catch (Exception e) {
                Log("Error in onEquitiesQuoteUpdated Callback: " + e.getMessage());
            }
        }
        return isSet;
    }

    @Override
    public OptionsContractData getOptionsContractData(String contract) {
        return contracts.get(contract);
    }

    @Override
    public Map<String, OptionsContractData> getAllOptionsContractData() {
        return readonlyContracts;
    }

    public List<String> getContractNames() {
        return contracts.values().stream().map(OptionsContractData::getContract).collect(Collectors.toList());
    }

    @Override
    public intrinio.realtime.options.Trade getOptionsContractTrade(String contract) {
        OptionsContractData optionsContractData = contracts.get(contract);
        return optionsContractData != null ? optionsContractData.getLatestTrade() : null;
    }

    @Override
    public boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade) {
        if (trade != null) {
            String contract = trade.contract();
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                CurrentOptionsContractData newDatum = new CurrentOptionsContractData(contract, trade, null, null, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setTrade(trade);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade, OnOptionsTradeUpdated onOptionsTradeUpdated, DataCache dataCache) {
        if (trade != null) {
            String contract = trade.contract();
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, trade, null, null, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setTrade(trade, onOptionsTradeUpdated, this, dataCache);
        }
        return false;
    }

    @Override
    public intrinio.realtime.options.Quote getOptionsContractQuote(String contract) {
        OptionsContractData optionsContractData = contracts.get(contract);
        return optionsContractData != null ? optionsContractData.getLatestQuote() : null;
    }

    @Override
    public boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote) {
        if (quote != null) {
            String contract = quote.contract();
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, quote, null, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setQuote(quote);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote, OnOptionsQuoteUpdated onOptionsQuoteUpdated, DataCache dataCache) {
        if (quote != null) {
            String contract = quote.contract();
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, quote, null, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setQuote(quote, onOptionsQuoteUpdated, this, dataCache);
        }
        return false;
    }

    @Override
    public intrinio.realtime.options.Refresh getOptionsContractRefresh(String contract) {
        OptionsContractData optionsContractData = contracts.get(contract);
        return optionsContractData != null ? optionsContractData.getLatestRefresh() : null;
    }

    @Override
    public boolean setOptionsContractRefresh(intrinio.realtime.options.Refresh refresh) {
        if (refresh != null) {
            String contract = refresh.contract();
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, null, refresh, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setRefresh(refresh);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractRefresh(intrinio.realtime.options.Refresh refresh, OnOptionsRefreshUpdated onOptionsRefreshUpdated, DataCache dataCache) {
        if (refresh != null) {
            String contract = refresh.contract();
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, null, refresh, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setRefresh(refresh, onOptionsRefreshUpdated, this, dataCache);
        }
        return false;
    }

    @Override
    public intrinio.realtime.options.UnusualActivity getOptionsContractUnusualActivity(String contract) {
        OptionsContractData optionsContractData = contracts.get(contract);
        return optionsContractData != null ? optionsContractData.getLatestUnusualActivity() : null;
    }

    @Override
    public boolean setOptionsContractUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity) {
        if (unusualActivity != null) {
            String contract = unusualActivity.contract();
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, null, null, unusualActivity);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setUnusualActivity(unusualActivity);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity, OnOptionsUnusualActivityUpdated onOptionsUnusualActivityUpdated, DataCache dataCache) {
        if (unusualActivity != null) {
            String contract = unusualActivity.contract();
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, null, null, unusualActivity);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setUnusualActivity(unusualActivity, onOptionsUnusualActivityUpdated, this, dataCache);
        }
        return false;
    }

    @Override
    public Double getOptionsContractSupplementalDatum(String contract, String key) {
        OptionsContractData optionsContractData = contracts.get(contract);
        return optionsContractData != null ? optionsContractData.getSupplementaryDatum(key) : null;
    }

    @Override
    public boolean setOptionsContractSupplementalDatum(String contract, String key, Double datum, SupplementalDatumUpdate update) {
        if (contract != null && !contract.trim().isEmpty()) {
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, null, null, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setSupplementaryDatum(key, datum, update);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractSupplementalDatum(String contract, String key, Double datum, OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated, DataCache dataCache, SupplementalDatumUpdate update) {
        if (contract != null && !contract.trim().isEmpty()) {
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, null, null, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setSupplementaryDatum(key, datum, onOptionsContractSupplementalDatumUpdated, this, dataCache, update);
        }
        return false;
    }

    @Override
    public Greek getOptionsContractGreekData(String contract, String key) {
        OptionsContractData optionsContractData = contracts.get(contract);
        return optionsContractData != null ? optionsContractData.getGreekData(key) : null;
    }

    @Override
    public boolean setOptionsContractGreekData(String contract, String key, Greek data, GreekDataUpdate update) {
        if (contract != null && !contract.trim().isEmpty()) {
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, null, null, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setGreekData(key, data, update);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractGreekData(String contract, String key, Greek data, OnOptionsContractGreekDataUpdated onOptionsContractGreekDataUpdated, DataCache dataCache, GreekDataUpdate update) {
        if (contract != null && !contract.trim().isEmpty()) {
            OptionsContractData currentOptionsContractData = contracts.get(contract);
            if (currentOptionsContractData == null) {
                OptionsContractData newDatum = new CurrentOptionsContractData(contract, null, null, null, null);
                currentOptionsContractData = contracts.computeIfAbsent(contract, k -> newDatum);
            }
            return currentOptionsContractData.setGreekData(key, data, onOptionsContractGreekDataUpdated, this, dataCache, update);
        }
        return false;
    }

    private void Log(String message){
        System.out.println(message);
    }
}