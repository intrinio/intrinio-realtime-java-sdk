package intrinio.realtime.composite;

public interface GreekCalculator {
    Greek calculate(String contract, SecurityData calcData, Double riskFreeInterestRate);
}
