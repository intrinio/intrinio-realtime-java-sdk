package intrinio.realtime.composite;

/**
 * The function used to merge a Greek value into the cache for a given key.
 * Invoked atomically per key by concurrent map update logic.
 */
@FunctionalInterface
public interface GreekDataUpdate {

    /**
     * @param key      Greek series key
     * @param oldValue previously stored value (may be {@code null})
     * @param newValue incoming value (may be {@code null})
     * @return value to store; {@code null} removes the mapping
     */
    Greek greekDataUpdate(String key, Greek oldValue, Greek newValue);
}
