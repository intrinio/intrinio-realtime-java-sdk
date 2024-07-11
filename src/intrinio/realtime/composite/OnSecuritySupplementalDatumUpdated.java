package intrinio.realtime.composite;

public interface OnSecuritySupplementalDatumUpdated {
    void onSecuritySupplementalDatumUpdated(String key, double datum, SecurityData securityData, DataCache dataCache);
}
