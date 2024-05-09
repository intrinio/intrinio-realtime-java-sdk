package intrinio.realtime.composite;

public interface GreekCalculator {
    Greek calculate(GreekCalculationData calcData, String contract);
}
