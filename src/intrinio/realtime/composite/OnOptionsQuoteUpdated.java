package intrinio.realtime.composite;

/**
 * Callback invoked when an option contract's latest quote is updated in the cache.
 */
@FunctionalInterface
public interface OnOptionsQuoteUpdated {

    /**
     * @param optionsContractData contract cache slice after the update
     * @param dataCache           the owning cache
     * @param securityData        underlying security cache slice
     * @param quote               quote that was applied
     */
    void onOptionsQuoteUpdated(OptionsContractData optionsContractData,
                               DataCache dataCache,
                               SecurityData securityData,
                               intrinio.realtime.options.Quote quote);
}
