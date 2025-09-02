package intrinio.realtime.composite;

public class DataCacheFactory {
    public static DataCache create() {
        return new CurrentDataCache();
    }
}