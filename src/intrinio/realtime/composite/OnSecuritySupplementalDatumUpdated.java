package intrinio.realtime.composite;

@FunctionalInterface
public interface OnSecuritySupplementalDatumUpdated {
    void onSecuritySupplementalDatumUpdated(String key, Double datum, SecurityData securityData, DataCache dataCache);
}
