package intrinio.realtime.composite;

public interface OnEquitiesTradeUpdated {
    void onEquitiesTradeUpdated(CurrentSecurityData currentSecurityData, CurrentDataCache currentDataCache);
}
