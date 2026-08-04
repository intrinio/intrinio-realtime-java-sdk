package intrinio.realtime.composite;

/**
 * Callback invoked when a security-level supplemental datum is updated.
 */
@FunctionalInterface
public interface OnSecuritySupplementalDatumUpdated {

    /**
     * @param key          supplemental datum key
     * @param datum        new value that was stored
     * @param securityData security cache slice
     * @param dataCache    the owning cache
     */
    void onSecuritySupplementalDatumUpdated(String key, Double datum, SecurityData securityData, DataCache dataCache);
}
