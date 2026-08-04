package intrinio.realtime.composite;

/**
 * The function used to merge a supplemental numeric value into the cache for a given key.
 * Invoked atomically per key by concurrent map update logic.
 */
@FunctionalInterface
public interface SupplementalDatumUpdate {

    /**
     * @param key      supplemental datum key
     * @param oldValue previously stored value (may be {@code null})
     * @param newValue incoming value (may be {@code null})
     * @return value to store; {@code null} removes the mapping
     */
    Double supplementalDatumUpdate(String key, Double oldValue, Double newValue);
}
