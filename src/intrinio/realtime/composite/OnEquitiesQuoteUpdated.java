package intrinio.realtime.composite;

public interface OnEquitiesQuoteUpdated {
    void onEquitiesQuoteUpdated(SecurityData SecurityData, DataCache DataCache);
}
