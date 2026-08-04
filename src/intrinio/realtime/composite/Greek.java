package intrinio.realtime.composite;

/**
 * Immutable container for option Greek values produced by a Greek calculator
 * (for example {@link BlackScholesGreekCalculator}).
 * <p>
 * Instances are intended to be stored in the non-transactional composite cache
 * and published to callbacks. Equality is value-based so cache update functions
 * can detect whether a newly computed Greek is identical to the previous value.
 * </p>
 *
 * @param impliedVolatility     Mid/market implied volatility used for the primary Greeks
 * @param delta                 Option delta
 * @param gamma                 Option gamma
 * @param theta                 Option theta (per calendar day)
 * @param vega                  Option vega (per 1% volatility move)
 * @param askImpliedVolatility  Implied volatility solved from the ask price (0 if unavailable)
 * @param bidImpliedVolatility  Implied volatility solved from the bid price (0 if unavailable)
 * @param isValid               {@code true} when the calculation succeeded with usable inputs
 */
public record Greek(
        double impliedVolatility,
        double delta,
        double gamma,
        double theta,
        double vega,
        double askImpliedVolatility,
        double bidImpliedVolatility,
        boolean isValid) {

    /**
     * Convenience constructor matching the historical field layout without ask/bid IVs.
     * Ask and bid implied volatilities default to {@code 0.0}.
     *
     * @param impliedVolatility Mid/market implied volatility
     * @param delta             Option delta
     * @param gamma             Option gamma
     * @param theta             Option theta
     * @param vega              Option vega
     * @param isValid           Whether the calculation is valid
     */
    public Greek(double impliedVolatility, double delta, double gamma, double theta, double vega, boolean isValid) {
        this(impliedVolatility, delta, gamma, theta, vega, 0.0D, 0.0D, isValid);
    }

    @Override
    public String toString() {
        return "Greek{" +
                "impliedVolatility=" + impliedVolatility +
                ", delta=" + delta +
                ", gamma=" + gamma +
                ", theta=" + theta +
                ", vega=" + vega +
                ", askImpliedVolatility=" + askImpliedVolatility +
                ", bidImpliedVolatility=" + bidImpliedVolatility +
                ", isValid=" + isValid +
                '}';
    }
}
