package intrinio.realtime.composite;

@FunctionalInterface
public interface OnOptionsRefreshUpdated {
    void onOptionsRefreshUpdated(OptionsContractData optionsContractData, DataCache dataCache, SecurityData securityData, intrinio.realtime.options.Refresh refresh);
}
