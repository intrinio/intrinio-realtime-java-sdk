package intrinio.realtime.composite;

/**
 * Callback invoked when an option contract's Greek series value is updated in the cache.
 */
@FunctionalInterface
public interface OnOptionsContractGreekDataUpdated {

    /**
     * @param key                 Greek series key (e.g. {@link GreekClient#BLACK_SCHOLES_KEY_NAME})
     * @param datum               new Greek value that was stored
     * @param optionsContractData contract cache slice
     * @param securityData        underlying security cache slice
     * @param dataCache           the owning cache
     */
    void onOptionsContractGreekDataUpdated(String key,
                                           Greek datum,
                                           OptionsContractData optionsContractData,
                                           SecurityData securityData,
                                           DataCache dataCache);
}
