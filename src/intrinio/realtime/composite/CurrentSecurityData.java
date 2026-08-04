package intrinio.realtime.composite;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default {@link SecurityData} implementation for a single equity ticker.
 * <p>
 * Latest equities trade/quote fields are updated with non-locking dirty timestamp checks.
 * Nested option contracts live in a {@link ConcurrentHashMap}. Concurrent writers may interleave;
 * this is intentional and matches the C# non-transactional cache design.
 * </p>
 */
class CurrentSecurityData implements SecurityData {

    /** Equity ticker for this cache entry. */
    private final String tickerSymbol;

    /** Latest equities trade (dirty-set by timestamp). */
    private volatile intrinio.realtime.equities.Trade latestTrade;

    /** Latest equities ask quote (dirty-set by timestamp). */
    private volatile intrinio.realtime.equities.Quote latestAskQuote;

    /** Latest equities bid quote (dirty-set by timestamp). */
    private volatile intrinio.realtime.equities.Quote latestBidQuote;

    /** Concurrent map of option contract id → contract cache. */
    private final ConcurrentHashMap<String, OptionsContractData> contracts = new ConcurrentHashMap<>();

    /** Unmodifiable live view of {@link #contracts}. */
    private final Map<String, OptionsContractData> readonlyContracts = Collections.unmodifiableMap(contracts);

    /** Concurrent map of security-level supplemental numerics. */
    private final ConcurrentHashMap<String, Double> supplementaryData = new ConcurrentHashMap<>();

    /** Unmodifiable live view of {@link #supplementaryData}. */
    private final Map<String, Double> readonlySupplementaryData = Collections.unmodifiableMap(supplementaryData);

    CurrentSecurityData(String tickerSymbol,
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
    public boolean setSupplementaryDatum(String key,
                                         Double datum,
                                         OnSecuritySupplementalDatumUpdated onSecuritySupplementalDatumUpdated,
                                         DataCache dataCache,
                                         SupplementalDatumUpdate update) {
        boolean result = setSupplementaryDatum(key, datum, update);
        if (result && onSecuritySupplementalDatumUpdated != null) {
            try {
                onSecuritySupplementalDatumUpdated.onSecuritySupplementalDatumUpdated(key, datum, this, dataCache);
            } catch (Exception e) {
                log("Error in onSecuritySupplementalDatumUpdated Callback: " + e.getMessage());
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
        // dirty set: accept only if no trade yet or incoming timestamp is strictly newer
        if (this.latestTrade == null || (trade != null && trade.timestamp() > this.latestTrade.timestamp())) {
            this.latestTrade = trade;
            return true;
        }
        return false;
    }

    @Override
    public boolean setEquitiesTrade(intrinio.realtime.equities.Trade trade,
                                    OnEquitiesTradeUpdated onEquitiesTradeUpdated,
                                    DataCache dataCache) {
        boolean isSet = setEquitiesTrade(trade);
        if (isSet && onEquitiesTradeUpdated != null) {
            try {
                onEquitiesTradeUpdated.onEquitiesTradeUpdated(this, dataCache, trade);
            } catch (Exception e) {
                log("Error in onEquitiesTradeUpdated Callback: " + e.getMessage());
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
    public boolean setEquitiesQuote(intrinio.realtime.equities.Quote quote,
                                    OnEquitiesQuoteUpdated onEquitiesQuoteUpdated,
                                    DataCache dataCache) {
        boolean isSet = this.setEquitiesQuote(quote);
        if (isSet && onEquitiesQuoteUpdated != null) {
            try {
                onEquitiesQuoteUpdated.onEquitiesQuoteUpdated(this, dataCache, quote);
            } catch (Exception e) {
                log("Error in onEquitiesQuoteUpdated Callback: " + e.getMessage());
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

    @Override
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
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, trade, null, null, null));
            return current.setTrade(trade);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractTrade(intrinio.realtime.options.Trade trade,
                                           OnOptionsTradeUpdated onOptionsTradeUpdated,
                                           DataCache dataCache) {
        if (trade != null) {
            String contract = trade.contract();
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, trade, null, null, null));
            return current.setTrade(trade, onOptionsTradeUpdated, this, dataCache);
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
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, quote, null, null));
            return current.setQuote(quote);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractQuote(intrinio.realtime.options.Quote quote,
                                           OnOptionsQuoteUpdated onOptionsQuoteUpdated,
                                           DataCache dataCache) {
        if (quote != null) {
            String contract = quote.contract();
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, quote, null, null));
            return current.setQuote(quote, onOptionsQuoteUpdated, this, dataCache);
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
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, null, refresh, null));
            return current.setRefresh(refresh);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractRefresh(intrinio.realtime.options.Refresh refresh,
                                             OnOptionsRefreshUpdated onOptionsRefreshUpdated,
                                             DataCache dataCache) {
        if (refresh != null) {
            String contract = refresh.contract();
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, null, refresh, null));
            return current.setRefresh(refresh, onOptionsRefreshUpdated, this, dataCache);
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
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, null, null, unusualActivity));
            return current.setUnusualActivity(unusualActivity);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity,
                                                     OnOptionsUnusualActivityUpdated onOptionsUnusualActivityUpdated,
                                                     DataCache dataCache) {
        if (unusualActivity != null) {
            String contract = unusualActivity.contract();
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, null, null, unusualActivity));
            return current.setUnusualActivity(unusualActivity, onOptionsUnusualActivityUpdated, this, dataCache);
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
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, null, null, null));
            return current.setSupplementaryDatum(key, datum, update);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractSupplementalDatum(String contract,
                                                       String key,
                                                       Double datum,
                                                       OnOptionsContractSupplementalDatumUpdated onOptionsContractSupplementalDatumUpdated,
                                                       DataCache dataCache,
                                                       SupplementalDatumUpdate update) {
        if (contract != null && !contract.trim().isEmpty()) {
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, null, null, null));
            return current.setSupplementaryDatum(key, datum, onOptionsContractSupplementalDatumUpdated, this, dataCache, update);
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
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, null, null, null));
            return current.setGreekData(key, data, update);
        }
        return false;
    }

    @Override
    public boolean setOptionsContractGreekData(String contract,
                                               String key,
                                               Greek data,
                                               OnOptionsContractGreekDataUpdated onOptionsContractGreekDataUpdated,
                                               DataCache dataCache,
                                               GreekDataUpdate update) {
        if (contract != null && !contract.trim().isEmpty()) {
            OptionsContractData current = contracts.computeIfAbsent(
                    contract, k -> new CurrentOptionsContractData(contract, null, null, null, null));
            return current.setGreekData(key, data, onOptionsContractGreekDataUpdated, this, dataCache, update);
        }
        return false;
    }

    private void log(String message) {
        System.out.println(message);
    }
}
