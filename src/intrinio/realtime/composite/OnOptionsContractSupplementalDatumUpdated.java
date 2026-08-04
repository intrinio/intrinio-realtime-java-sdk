package intrinio.realtime.composite;

/**
 * Callback invoked when an option-contract supplemental datum is updated.
 */
@FunctionalInterface
public interface OnOptionsContractSupplementalDatumUpdated {

    /**
     * @param key                 supplemental datum key
     * @param datum               new value that was stored
     * @param optionsContractData contract cache slice
     * @param securityData        underlying security cache slice
     * @param dataCache           the owning cache
     */
    void onOptionsContractSupplementalDatumUpdated(String key,
                                                   Double datum,
                                                   OptionsContractData optionsContractData,
                                                   SecurityData securityData,
                                                   DataCache dataCache);
}
