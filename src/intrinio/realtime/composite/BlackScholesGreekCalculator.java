package intrinio.realtime.composite;

public class BlackScholesGreekCalculator implements GreekCalculator
{
    @Override
    public Greek Calculate(GreekCalculationData calcData, String contract) {
        return new Greek(calcData.getUnderlyingTrade().symbol(),
                contract,
                calcData.getUnderlyingTrade(),
                calcData.getOptionsContracts().stream().findFirst().get().getLatestTrade(),//latestOptionTrade,
                calcData.getOptionsContracts().stream().findFirst().get().getLatestQuote(),//latestOptionQuote,
                calcData.getRiskFreeInterestRate(),
                calcData.getDividendYield(),
                1.0,//double daysToExpiration,
                1,//double marketPrice,
                1,//double impliedVolatility,
                1,//double delta,
                1,//double gamma,
                1,//double theta,
                1,//double vega,
                GreekCalculationMethod.BLACK_SCHOLES);
    }
}
