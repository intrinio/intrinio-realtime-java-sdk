package intrinio.realtime.composite;

/**
 * Callback invoked when a top-level (cache-wide) supplemental datum is updated.
 */
@FunctionalInterface
public interface OnSupplementalDatumUpdated {

    /**
     * @param key       supplemental datum key
     * @param datum     new value that was stored
     * @param dataCache the owning cache
     */
    void onSupplementalDatumUpdated(String key, Double datum, DataCache dataCache);
}
