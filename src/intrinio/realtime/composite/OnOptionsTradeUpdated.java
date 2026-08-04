package intrinio.realtime.composite;

/**
 * Callback invoked when an option contract's latest trade is updated in the cache.
 */
@FunctionalInterface
public interface OnOptionsTradeUpdated {

    /**
     * @param optionsContractData contract cache slice after the update
     * @param dataCache           the owning cache
     * @param securityData        underlying security cache slice
     * @param trade               trade that was applied
     */
    void onOptionsTradeUpdated(OptionsContractData optionsContractData,
                               DataCache dataCache,
                               SecurityData securityData,
                               intrinio.realtime.options.Trade trade);
}
