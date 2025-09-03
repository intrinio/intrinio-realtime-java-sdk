package intrinio.realtime.composite;

/**
 * The function used to update the Supplemental value in the cache.
 */
@FunctionalInterface
public interface SupplementalDatumUpdate {
    Double supplementalDatumUpdate(String key, Double oldValue, Double newValue);
}
