package intrinio.realtime.composite;

public class BlackScholesGreekCalculator implements GreekCalculator
{
    private final double LOW_VOL = 0.0;
    private final double HIGH_VOL = 5.0;
    private final double VOL_TOLERANCE = 0.0001;
    private final double MIN_Z_SCORE = -8.0;
    private final double MAX_Z_SCORE = 8.0;

    @Override
    public Greek calculate(String contract, CurrentSecurityData calcData, Double riskFreeInterestRate) {
        intrinio.realtime.equities.Trade underlyingTrade = calcData.getEquitiesTrade();
        intrinio.realtime.options.Trade latestOptionTrade = calcData.getOptionsTrade(contract);
        intrinio.realtime.options.Quote latestOptionQuote = calcData.getOptionsQuote(contract);
        if (underlyingTrade == null || latestOptionTrade == null || latestOptionQuote == null)
            return null;
        if (latestOptionQuote.askPrice() <= 0.0 || latestOptionQuote.bidPrice() <= 0.0)
            return null;
        if (riskFreeInterestRate == null || riskFreeInterestRate <= 0.0)
            return null;

        boolean isPut = latestOptionTrade.isPut();
        double underlyingPrice = underlyingTrade.price();
        double strike = latestOptionTrade.getStrikePrice();
        double daysToExpiration = getDaysToExpiration(latestOptionTrade, latestOptionQuote);
        double dividendYield = calcData.getDividendYield();
        double marketPrice = (latestOptionQuote.askPrice() + latestOptionQuote.bidPrice()) / 2.0;
        double impliedVolatility = calcImpliedVolatility(isPut, underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice);
        double sigma = impliedVolatility;
        double delta = calcDelta(isPut, underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma);
        double gamma = calcGamma(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma);
        double theta = calcTheta(isPut, underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma);
        double vega = calcVega(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma);

        return getGreek(calcData, riskFreeInterestRate, contract, daysToExpiration, marketPrice, impliedVolatility, delta, gamma, theta, vega);
    }

    private double calcImpliedVolatilityCall(double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice) {
        double low = LOW_VOL, high = HIGH_VOL;
        while ((high - low) > VOL_TOLERANCE){
            if (calcPriceCall(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, (high + low) / 2.0, dividendYield) > marketPrice)
                high = (high + low) / 2.0;
            else
                low = (high + low) / 2.0;
        }

        return (high + low) / 2.0;
    }

    private double calcImpliedVolatilityPut(double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice) {
        double low = LOW_VOL, high = HIGH_VOL;
        while ((high - low) > VOL_TOLERANCE){
            if (calcPricePut(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, (high + low) / 2.0, dividendYield) > marketPrice)
                high = (high + low) / 2.0;
            else
                low = (high + low) / 2.0;
        }

        return (high + low) / 2.0;
    }

    private double calcImpliedVolatility(boolean isPut, double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice){
        if (isPut)
            return calcImpliedVolatilityPut(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice);
        return calcImpliedVolatilityCall(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice);
    }

    private double calcDeltaCall(double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice, double sigma){
        return normalSDist( d1( underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield ) );
    }

    private double calcDeltaPut(double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice, double sigma){
        return calcDeltaCall( underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma) - 1;
    }

    private double calcDelta(boolean isPut, double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice, double sigma){
        if (isPut)
            return calcDeltaPut(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma);
        else return calcDeltaCall(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma);
    }

    private double calcGamma(double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice, double sigma){
        return phi( d1( underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield ) ) / ( underlyingPrice * sigma * Math.sqrt(daysToExpiration) );
    }

    private double calcThetaCall(double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice, double sigma){
        double term1 = underlyingPrice * phi( d1( underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield ) ) * sigma / ( 2 * Math.sqrt(daysToExpiration) );
        double term2 = riskFreeInterestRate * strike * Math.exp(-1.0 * riskFreeInterestRate * daysToExpiration) * normalSDist( d2( underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield ) );
        return ( - term1 - term2 ) / 365.25;
    }

    private double calcThetaPut(double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice, double sigma){
        double term1 = underlyingPrice * phi( d1( underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield ) ) * sigma / ( 2 * Math.sqrt(daysToExpiration) );
        double term2 = riskFreeInterestRate * strike * Math.exp(-1.0 * riskFreeInterestRate * daysToExpiration) * normalSDist( - d2( underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield ) );
        return ( - term1 + term2 ) / 365.25;
    }

    private double calcTheta(boolean isPut, double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice, double sigma){
        if (isPut)
            return calcThetaPut(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma);
        else return calcThetaCall(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, dividendYield, marketPrice, sigma);
    }

    private double calcVega(double underlyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice, double sigma){
        return 0.01 * underlyingPrice * Math.sqrt(daysToExpiration) * phi(d1(underlyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield));
    }

    private double d1(double underylyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double sigma, double dividendYield){
        double numerator = ( Math.log(underylyingPrice / strike) + (riskFreeInterestRate - dividendYield + 0.5 * Math.pow(sigma, 2.0) ) * daysToExpiration);
        double denominator = ( sigma * Math.sqrt(daysToExpiration));
        return numerator / denominator;
    }

    private double d2(double underylyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double sigma, double dividendYield){
        return d1( underylyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield ) - ( sigma * Math.sqrt(daysToExpiration) );
    }

    private double normalSDist(double z){
        if (z < MIN_Z_SCORE)
            return 0.0;
        if (z > MAX_Z_SCORE)
            return 1.0;
        double i = 3.0, sum = 0.0, term = z;
        while ((sum + term) != sum){
            sum = sum + term;
            term = term * z * z / i;
            i += 2.0;
        }
        return 0.5 + sum * phi(z);
    }

    private double phi(double x){
        double numerator = Math.exp(-1.0 * x*x / 2.0);
        double denominator = Math.sqrt(2.0 * Math.PI);
        return numerator / denominator;
    }

    private double calcPriceCall(double underylyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double sigma, double dividendYield){
        double d1 = d1( underylyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield );
        double discounted_underlying = Math.exp(-1.0 * dividendYield * daysToExpiration) * underylyingPrice;
        double probability_weighted_value_of_being_exercised = discounted_underlying * normalSDist( d1 );

        double d2 = d1 - ( sigma * Math.sqrt(daysToExpiration) );
        double discounted_strike = Math.exp(-1.0 * riskFreeInterestRate * daysToExpiration) * strike;
        double probability_weighted_value_of_discounted_strike = discounted_strike * normalSDist( d2 );

        return probability_weighted_value_of_being_exercised - probability_weighted_value_of_discounted_strike;
    }

    private double calcPricePut(double underylyingPrice, double strike, double daysToExpiration, double riskFreeInterestRate, double sigma, double dividendYield){
        double d2 = d2( underylyingPrice, strike, daysToExpiration, riskFreeInterestRate, sigma, dividendYield );
        double discounted_strike = strike * Math.exp(-1.0 * riskFreeInterestRate * daysToExpiration);
        double probabiltity_weighted_value_of_discounted_strike = discounted_strike * normalSDist( -1.0 * d2 );

        double d1 = d2 + ( sigma * Math.sqrt(daysToExpiration) );
        double discounted_underlying = underylyingPrice * Math.exp(-1.0 * dividendYield * daysToExpiration);
        double probability_weighted_value_of_being_exercised = discounted_underlying * normalSDist( -1.0 * d1 );

        return probabiltity_weighted_value_of_discounted_strike - probability_weighted_value_of_being_exercised;
    }

    private double getDaysToExpiration(intrinio.realtime.options.Trade latestOptionTrade, intrinio.realtime.options.Quote latestOptionQuote){
        double latestActivity = Math.max(latestOptionTrade.timestamp(), latestOptionQuote.timestamp());
        long expirationAsUnixWholeSeconds = latestOptionTrade.getExpirationDate().toEpochSecond();
        double fractional = ((double)(latestOptionTrade.getExpirationDate().getNano())) / 1_000_000_000.0;
        double expiration = (((double)expirationAsUnixWholeSeconds) + fractional);
        return (expiration - latestActivity) / 86400.0; //86400 is seconds in a day
    }

    private Greek getGreek(CurrentSecurityData calcData, Double riskFreeInterestRate, String contract, double daysToExpiration, double marketPrice, double impliedVolatility, double delta, double gamma, double theta, double vega){
        return new Greek(calcData.getTickerSymbol(),
                contract,
                calcData.getEquitiesTrade(),
                calcData.getAllOptionsContractData().get(contract).getLatestTrade(),//latestOptionTrade,
                calcData.getAllOptionsContractData().get(contract).getLatestQuote(),//latestOptionQuote,
                riskFreeInterestRate,
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
