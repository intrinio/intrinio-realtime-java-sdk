package intrinio.realtime.composite;

/**
 * Strategy used by {@link GreekClient} to compute and store Greeks for a single option contract.
 * <p>
 * Implementations typically read the latest equities/options state and supplemental rates from
 * the provided cache objects, compute a {@link Greek}, and write it via
 * {@link DataCache#setOptionGreekData}. They must tolerate concurrent, non-transactional
 * cache state (fields may change while the calculator runs).
 * </p>
 */
@FunctionalInterface
public interface CalculateNewGreek {

    /**
     * Compute and optionally store a new Greek for the given contract.
     *
     * @param optionsContractData contract-level cache slice
     * @param securityData        underlying security cache slice
     * @param dataCache           top-level composite cache
     */
    void calculateNewGreek(OptionsContractData optionsContractData, SecurityData securityData, DataCache dataCache);
}
