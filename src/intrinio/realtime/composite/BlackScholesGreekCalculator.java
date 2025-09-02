package intrinio.realtime.composite;

import java.util.Date;

public class BlackScholesGreekCalculator {
    private static final double LOW_VOL = 0.0D;
    private static final double HIGH_VOL = 5.0D;
    private static final double VOL_TOLERANCE = 1e-12D;
    private static final double MIN_Z_SCORE = -8.0D;
    private static final double MAX_Z_SCORE = 8.0D;
    private static final double root2Pi = Math.sqrt(2.0D * Math.PI);

    public static Greek calculate(double riskFreeInterestRate, double dividendYield, double underlyingPrice, double latestEventUnixTimestamp, double marketPrice, boolean isPut, double strike, Date expirationDate) {
        if (marketPrice <= 0.0D || riskFreeInterestRate <= 0.0D || underlyingPrice <= 0.0D)
            return new Greek(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false);

        double yearsToExpiration = getYearsToExpiration(latestEventUnixTimestamp, expirationDate);

        if (yearsToExpiration <= 0.0D || strike <= 0.0D)
            return new Greek(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false);

        double impliedVolatility = calcImpliedVolatility(isPut, underlyingPrice, strike, yearsToExpiration, riskFreeInterestRate, dividendYield, marketPrice);
        if (impliedVolatility == 0.0D)
            return new Greek(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false);

        // Compute common values once for all Greeks to avoid redundant calcs
        double sqrtT = Math.sqrt(yearsToExpiration);
        double d1 = d1(underlyingPrice, strike, yearsToExpiration, riskFreeInterestRate, impliedVolatility, dividendYield);
        double d2 = d1 - impliedVolatility * sqrtT;
        double expQt = Math.exp(-dividendYield * yearsToExpiration);
        double expRt = Math.exp(-riskFreeInterestRate * yearsToExpiration);
        double nD1 = cumulativeNormalDistribution(d1);
        double nD2 = cumulativeNormalDistribution(d2);
        double phiD1 = normalPdf(d1);

        double delta = isPut ? expQt * (nD1 - 1.0D) : expQt * nD1;
        double gamma = expQt * phiD1 / (underlyingPrice * impliedVolatility * sqrtT);
        double vega = 0.01D * underlyingPrice * expQt * sqrtT * phiD1;

        // Theta with correct dividend adjustments
        double term1 = expQt * underlyingPrice * phiD1 * impliedVolatility / (2.0D * sqrtT);
        double term2 = riskFreeInterestRate * strike * expRt * (isPut ? (1.0D - nD2) : nD2);
        double term3 = dividendYield * underlyingPrice * expQt * (isPut ? (1.0D - nD1) : nD1);
        double theta = isPut ? (-term1 + term2 - term3) / 365.25D : (-term1 - term2 + term3) / 365.25D;

        return new Greek(impliedVolatility, delta, gamma, theta, vega, true);
    }

    private static double calcImpliedVolatility(boolean isPut, double underlyingPrice, double strike, double yearsToExpiration, double riskFreeInterestRate, double dividendYield, double marketPrice) {
        double tol = 1e-10D;
        double forward = underlyingPrice * Math.exp((riskFreeInterestRate - dividendYield) * yearsToExpiration);
        double m = forward / strike;
        double sigma = Math.sqrt(2.0D * Math.abs(Math.log(m)) / yearsToExpiration);
        if (Double.isNaN(sigma) || sigma <= 0.0D) sigma = 0.3D;

        int maxIter = 50;
        for (int iter = 0; iter < maxIter; iter++) {
            double price = isPut ? calcPricePut(underlyingPrice, strike, yearsToExpiration, riskFreeInterestRate, sigma, dividendYield) : calcPriceCall(underlyingPrice, strike, yearsToExpiration, riskFreeInterestRate, sigma, dividendYield);
            double diff = price - marketPrice;
            if (Math.abs(diff) < tol) break;

            double d1 = d1(underlyingPrice, strike, yearsToExpiration, riskFreeInterestRate, sigma, dividendYield);
            double vega = underlyingPrice * Math.exp(-dividendYield * yearsToExpiration) * Math.sqrt(yearsToExpiration) * normalPdf(d1);
            if (Math.abs(vega) < 1e-10D) break; // avoid division by zero

            sigma -= diff / vega;
            if (sigma <= 0.0D) sigma = 0.0001D; // prevent negative or zero
        }

        return sigma;
    }

    private static double d1(double underlyingPrice, double strike, double yearsToExpiration, double riskFreeInterestRate, double sigma, double dividendYield) {
        double numerator = Math.log(underlyingPrice / strike) + (riskFreeInterestRate - dividendYield + 0.5D * sigma * sigma) * yearsToExpiration;
        double denominator = sigma * Math.sqrt(yearsToExpiration);
        return numerator / denominator;
    }

    private static double d2(double underlyingPrice, double strike, double yearsToExpiration, double riskFreeInterestRate, double sigma, double dividendYield) {
        return d1(underlyingPrice, strike, yearsToExpiration, riskFreeInterestRate, sigma, dividendYield) - sigma * Math.sqrt(yearsToExpiration);
    }

    private static double cumulativeNormalDistribution(double z) {
        if (Math.abs(z) < 1.5D)
            return cumulativeNormalDistributionSeries(z);

        if (z > MAX_Z_SCORE) return 1.0D;
        if (z < MIN_Z_SCORE) return 0.0D;

        boolean isNegative = z < 0.0D;
        if (isNegative) z = -z;

        double t = 1.0D / (1.0D + 0.2316419D * z);
        double poly = t * (0.319381530D + t * (-0.356563782D + t * (1.781477937D + t * (-1.821255978D + t * 1.330274429D))));

        double pdf = Math.exp(-0.5D * z * z) / root2Pi;
        double tail = pdf * poly;

        return isNegative ? tail : 1.0D - tail;
    }

    private static double cumulativeNormalDistributionSeries(double z) {
        double absZ = Math.abs(z);
        double sum = 0.0D;
        double term = absZ;
        double i = 3.0D;
        while (sum + term != sum) {
            sum += term;
            term = term * absZ * absZ / i;
            i += 2.0D;
        }
        double pdf = Math.exp(-0.5D * absZ * absZ) / root2Pi;
        double half = pdf * sum;
        return z >= 0.0D ? 0.5D + half : 0.5D - half;
    }

    private static double normalPdf(double x) {
        return Math.exp(-0.5D * x * x) / root2Pi;
    }

    private static double calcPriceCall(double underlyingPrice, double strike, double yearsToExpiration, double riskFreeInterestRate, double sigma, double dividendYield) {
        double d1 = d1(underlyingPrice, strike, yearsToExpiration, riskFreeInterestRate, sigma, dividendYield);
        double d2 = d1 - sigma * Math.sqrt(yearsToExpiration);
        double discountedUnderlying = Math.exp(-dividendYield * yearsToExpiration) * underlyingPrice;
        double discountedStrike = Math.exp(-riskFreeInterestRate * yearsToExpiration) * strike;
        return discountedUnderlying * cumulativeNormalDistribution(d1) - discountedStrike * cumulativeNormalDistribution(d2);
    }

    private static double calcPricePut(double underlyingPrice, double strike, double yearsToExpiration, double riskFreeInterestRate, double sigma, double dividendYield) {
        double d1 = d1(underlyingPrice, strike, yearsToExpiration, riskFreeInterestRate, sigma, dividendYield);
        double d2 = d1 - sigma * Math.sqrt(yearsToExpiration);
        double discountedUnderlying = Math.exp(-dividendYield * yearsToExpiration) * underlyingPrice;
        double discountedStrike = Math.exp(-riskFreeInterestRate * yearsToExpiration) * strike;
        return discountedStrike * cumulativeNormalDistribution(-d2) - discountedUnderlying * cumulativeNormalDistribution(-d1);
    }

    private static double getYearsToExpiration(double latestActivityUnixTime, Date expirationDate) {
        double expiration = expirationDate.getTime() / 1000.0D;
        return (expiration - latestActivityUnixTime) / 31557600.0D;
    }
}