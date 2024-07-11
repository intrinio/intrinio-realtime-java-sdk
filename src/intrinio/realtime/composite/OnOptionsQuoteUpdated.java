package intrinio.realtime.composite;

public interface OnOptionsQuoteUpdated {
    void onOptionsQuoteUpdated(OptionsContractData optionsContractData, DataCache dataCache, SecurityData securityData);
}
