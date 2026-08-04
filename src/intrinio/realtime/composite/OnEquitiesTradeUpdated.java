package intrinio.realtime.composite;

/**
 * Callback invoked when a security's latest equities trade is updated in the cache.
 */
@FunctionalInterface
public interface OnEquitiesTradeUpdated {

    /**
     * @param securityData security cache slice after the update
     * @param dataCache    the owning cache
     * @param trade        trade that was applied
     */
    void onEquitiesTradeUpdated(SecurityData securityData, DataCache dataCache, intrinio.realtime.equities.Trade trade);
}
