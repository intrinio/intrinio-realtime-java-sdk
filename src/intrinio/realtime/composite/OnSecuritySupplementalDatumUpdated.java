package intrinio.realtime.composite;

public interface OnSecuritySupplementalDatumUpdated {
    void onSecuritySupplementalDatumUpdated(String key, double datum, SecurityData SecurityData, DataCache DataCache);
}
