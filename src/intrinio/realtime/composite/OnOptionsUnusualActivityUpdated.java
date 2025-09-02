package intrinio.realtime.composite;

@FunctionalInterface
public interface OnOptionsUnusualActivityUpdated {
    void onOptionsUnusualActivityUpdated(OptionsContractData optionsContractData, DataCache dataCache, SecurityData securityData, intrinio.realtime.options.UnusualActivity unusualActivity);
}
