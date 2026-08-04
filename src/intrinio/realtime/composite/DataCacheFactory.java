package intrinio.realtime.composite;

/**
 * Factory for {@link DataCache} instances.
 * <p>
 * Each call to {@link #create()} returns a new, empty, thread-safe, non-transactional cache
 * suitable for wiring into equities/options WebSocket handlers and {@link GreekClient}.
 * </p>
 */
public final class DataCacheFactory {

    private DataCacheFactory() {
    }

    /**
     * @return a new {@link DataCache} implementation
     */
    public static DataCache create() {
        return new CurrentDataCache();
    }
}
