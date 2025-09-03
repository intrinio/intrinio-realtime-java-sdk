package intrinio.realtime.composite;

@FunctionalInterface
public interface OnOptionsContractGreekDataUpdated {
    void onOptionsContractGreekDataUpdated(String key, Greek datum, OptionsContractData optionsContractData, SecurityData securityData, DataCache dataCache);
}
