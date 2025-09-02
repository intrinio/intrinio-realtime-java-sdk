package intrinio.realtime.composite;

@FunctionalInterface
public interface OnEquitiesQuoteUpdated {
    void onEquitiesQuoteUpdated(SecurityData securityData, DataCache dataCache, intrinio.realtime.equities.Quote quote);
}
