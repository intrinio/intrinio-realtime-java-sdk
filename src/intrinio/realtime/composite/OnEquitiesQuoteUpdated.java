package intrinio.realtime.composite;

public interface OnEquitiesQuoteUpdated {
    void onEquitiesQuoteUpdated(CurrentSecurityData currentSecurityData, CurrentDataCache currentDataCache);
}
