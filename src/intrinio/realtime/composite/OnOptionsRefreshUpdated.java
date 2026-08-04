package intrinio.realtime.composite;

/**
 * Callback invoked when an option contract's latest refresh is updated in the cache.
 */
@FunctionalInterface
public interface OnOptionsRefreshUpdated {

    /**
     * @param optionsContractData contract cache slice after the update
     * @param dataCache           the owning cache
     * @param securityData        underlying security cache slice
     * @param refresh             refresh that was applied
     */
    void onOptionsRefreshUpdated(OptionsContractData optionsContractData,
                                 DataCache dataCache,
                                 SecurityData securityData,
                                 intrinio.realtime.options.Refresh refresh);
}
