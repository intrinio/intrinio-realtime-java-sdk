package intrinio.realtime.composite;

public interface OnSupplementalDatumUpdated {
    void onSupplementalDatumUpdated(String key, double datum, DataCache DataCache);
}
