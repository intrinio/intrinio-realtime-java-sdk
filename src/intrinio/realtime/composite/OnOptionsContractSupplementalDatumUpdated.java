package intrinio.realtime.composite;

@FunctionalInterface
public interface OnOptionsContractSupplementalDatumUpdated {
    void onOptionsContractSupplementalDatumUpdated(String key, Double datum, OptionsContractData optionsContractData, SecurityData securityData, DataCache dataCache);
}
