package intrinio.realtime.composite;

@FunctionalInterface
public interface OnOptionsQuoteUpdated {
    void onOptionsQuoteUpdated(OptionsContractData optionsContractData, DataCache dataCache, SecurityData securityData, intrinio.realtime.options.Quote quote);
}
