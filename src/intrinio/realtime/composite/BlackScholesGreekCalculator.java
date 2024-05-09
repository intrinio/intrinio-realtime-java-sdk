package intrinio.realtime.composite;

import jdk.jfr.Timespan;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class BlackScholesGreekCalculator implements GreekCalculator
{
    @Override
    public Greek calculate(GreekCalculationData calcData, String contract) {
        intrinio.realtime.equities.Trade underlyingTrade = calcData.getUnderlyingTrade();
        if (underlyingTrade == null)
            return null;
        OptionsContractData contractData = calcData.getOptionsContractData(contract);
        if (contractData == null)
            return null;
        intrinio.realtime.options.Trade latestOptionTrade = contractData.getLatestTrade();
        intrinio.realtime.options.Quote latestOptionQuote = contractData.getLatestQuote();
        if (latestOptionTrade == null || latestOptionQuote == null)
            return null;
        if (latestOptionQuote.askPrice() <= 0.0 || latestOptionQuote.bidPrice() <= 0.0)
            return null;

        double underlyingPrice = underlyingTrade.price();
        double strike = latestOptionTrade.getStrikePrice();
        double daysToExpiration = getDaysToExpiration(latestOptionTrade, latestOptionQuote);
        double riskFreeInterestRate = calcData.getRiskFreeInterestRate();
        double marketPrice = (latestOptionQuote.askPrice() + latestOptionQuote.bidPrice()) / 2.0;
        double impliedVolatility = 0.0;
        double delta = 0.0;
        double gamma = 0.0;
        double theta = 0.0;
        double vega = 0.0;

        return getGreek(calcData, contract, daysToExpiration, marketPrice, impliedVolatility, delta, gamma, theta, vega);
    }

    private double getDaysToExpiration(intrinio.realtime.options.Trade latestOptionTrade, intrinio.realtime.options.Quote latestOptionQuote){
        double latestActivity = Math.max(latestOptionTrade.timestamp(), latestOptionQuote.timestamp());
        long expirationAsUnixWholeSeconds = latestOptionTrade.getExpirationDate().toEpochSecond();
        double fractional = ((double)(latestOptionTrade.getExpirationDate().getNano())) / 1_000_000_000.0;
        double expiration = (((double)expirationAsUnixWholeSeconds) + fractional);
        return (expiration - latestActivity) / 86400.0; //86400 is seconds in a day
    }

    private Greek getGreek(GreekCalculationData calcData,
                           String contract,
                           double daysToExpiration,
                           double marketPrice,
                           double impliedVolatility,
                           double delta,
                           double gamma,
                           double theta,
                           double vega){
        return new Greek(calcData.getUnderlyingTrade().symbol(),
                contract,
                calcData.getUnderlyingTrade(),
                calcData.getOptionsContracts().get(contract).getLatestTrade(),//latestOptionTrade,
                calcData.getOptionsContracts().get(contract).getLatestQuote(),//latestOptionQuote,
                calcData.getRiskFreeInterestRate(),
                calcData.getDividendYield(),
                daysToExpiration,
                marketPrice,
                impliedVolatility,
                delta,
                gamma,
                theta,
                vega,
                GreekCalculationMethod.BLACK_SCHOLES);
    }
}
