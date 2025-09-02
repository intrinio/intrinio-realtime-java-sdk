package intrinio.realtime.composite;

@FunctionalInterface
public interface OnOptionsTradeUpdated {
    void onOptionsTradeUpdated(OptionsContractData optionsContractData, DataCache dataCache, SecurityData securityData, intrinio.realtime.options.Trade trade);
}
