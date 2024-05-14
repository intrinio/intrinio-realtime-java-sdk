package intrinio.realtime.composite;

public interface OnOptionsQuoteUpdated {
    void onOptionsQuoteUpdated(OptionsContractData optionsContractData, CurrentDataCache currentDataCache, CurrentSecurityData currentSecurityData);
}
