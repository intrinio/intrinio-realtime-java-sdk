package intrinio.realtime.composite;

@FunctionalInterface
public interface OnEquitiesTradeUpdated {
    void onEquitiesTradeUpdated(SecurityData securityData, DataCache dataCache, intrinio.realtime.equities.Trade trade);
}
