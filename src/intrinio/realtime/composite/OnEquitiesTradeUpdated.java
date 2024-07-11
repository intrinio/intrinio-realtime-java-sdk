package intrinio.realtime.composite;

public interface OnEquitiesTradeUpdated {
    void onEquitiesTradeUpdated(SecurityData SecurityData, DataCache DataCache);
}
