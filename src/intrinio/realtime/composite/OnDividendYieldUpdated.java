package intrinio.realtime.composite;

public interface OnDividendYieldUpdated {
    void onDividendYieldUpdated(double dividendYield, CurrentSecurityData currentSecurityData, CurrentDataCache currentDataCache);
}
