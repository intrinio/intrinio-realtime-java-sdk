package intrinio.realtime.composite;

public interface OnOptionsContractSupplementalDatumUpdated {
    void onOptionsContractSupplementalDatumUpdated(String key, double datum, OptionsContractData optionsContractData, SecurityData securityData, DataCache dataCache);
}
