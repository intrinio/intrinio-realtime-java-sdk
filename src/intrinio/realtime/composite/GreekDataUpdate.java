package intrinio.realtime.composite;

/**
 * The function used to update the Greek value in the cache.
 */
@FunctionalInterface
public interface GreekDataUpdate {
    Greek greekDataUpdate(String key, Greek oldValue, Greek newValue);
}
