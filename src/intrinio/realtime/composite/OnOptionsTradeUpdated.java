package intrinio.realtime.composite;

public interface OnOptionsTradeUpdated {
    void onOptionsTradeUpdated(OptionsContractData optionsContractData, DataCache dataCache, SecurityData securityData);
}
