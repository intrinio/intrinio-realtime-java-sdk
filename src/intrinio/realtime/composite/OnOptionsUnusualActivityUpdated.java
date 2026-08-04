package intrinio.realtime.composite;

/**
 * Callback invoked when an option contract's latest unusual activity is updated in the cache.
 */
@FunctionalInterface
public interface OnOptionsUnusualActivityUpdated {

    /**
     * @param optionsContractData contract cache slice after the update
     * @param dataCache           the owning cache
     * @param securityData        underlying security cache slice
     * @param unusualActivity     unusual activity that was applied
     */
    void onOptionsUnusualActivityUpdated(OptionsContractData optionsContractData,
                                         DataCache dataCache,
                                         SecurityData securityData,
                                         intrinio.realtime.options.UnusualActivity unusualActivity);
}
