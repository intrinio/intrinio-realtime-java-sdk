package intrinio.realtime.composite;

public interface OnEquitiesTradeUpdated {
    void onEquitiesTradeUpdated(SecurityData securityData, DataCache dataCache);
}
