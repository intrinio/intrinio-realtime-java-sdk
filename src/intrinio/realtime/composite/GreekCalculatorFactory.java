package intrinio.realtime.composite;

public class GreekCalculatorFactory
{
    GreekCalculator GetGreekCalculator(GreekCalculationMethod method)
    {
        return switch (method) {
            case BJERKSUND_STRENSLAND -> new BjerksundStrenslandGreekCalculator();
            case BLACK_SCHOLES -> new BlackScholesGreekCalculator();
        };
    }
}