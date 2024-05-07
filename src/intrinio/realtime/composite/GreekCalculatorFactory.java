package intrinio.realtime.composite;

public class GreekCalculatorFactory
{
    GreekCalculator GetGreekCalculator(GreekCalculationMethod method)
    {
        return switch (method) {
            case BLACK_SCHOLES -> new BlackScholesGreekCalculator();
        };
    }
}