package intrinio.realtime.composite;

@FunctionalInterface
public interface OnSupplementalDatumUpdated {
    void onSupplementalDatumUpdated(String key, Double datum, DataCache dataCache);
}
