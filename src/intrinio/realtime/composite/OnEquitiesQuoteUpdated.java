package intrinio.realtime.composite;

/**
 * Callback invoked when a security's latest equities quote (ask or bid) is updated in the cache.
 */
@FunctionalInterface
public interface OnEquitiesQuoteUpdated {

    /**
     * @param securityData security cache slice after the update
     * @param dataCache    the owning cache
     * @param quote        quote that was applied
     */
    void onEquitiesQuoteUpdated(SecurityData securityData, DataCache dataCache, intrinio.realtime.equities.Quote quote);
}
