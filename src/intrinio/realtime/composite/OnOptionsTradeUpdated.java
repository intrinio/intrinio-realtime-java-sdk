package intrinio.realtime.composite;

public interface OnOptionsTradeUpdated {
    void onOptionsTradeUpdated(OptionsContractData optionsContractData, CurrentDataCache currentDataCache, CurrentSecurityData currentSecurityData);
}
