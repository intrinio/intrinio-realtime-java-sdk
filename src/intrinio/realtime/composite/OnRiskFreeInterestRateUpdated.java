package intrinio.realtime.composite;

public interface OnRiskFreeInterestRateUpdated {
    void onRiskFreeInterestRateUpdated(double riskFreeInterestRate, CurrentDataCache currentDataCache);
}
