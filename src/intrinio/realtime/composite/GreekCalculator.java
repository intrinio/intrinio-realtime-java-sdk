package intrinio.realtime.composite;

public interface GreekCalculator {
    Greek calculate(String contract, CurrentSecurityData calcData, Double riskFreeInterestRate);
}
