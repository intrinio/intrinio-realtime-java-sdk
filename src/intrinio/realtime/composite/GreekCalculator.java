package intrinio.realtime.composite;

public interface GreekCalculator {
    Greek Calculate(GreekCalculationData calcData, String contract);
}
