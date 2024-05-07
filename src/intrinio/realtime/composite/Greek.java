package intrinio.realtime.composite;

public record Greek (String ticker,
                     String contract,
                     intrinio.realtime.equities.Trade latestEquityTrade,
                     intrinio.realtime.options.Trade latestOptionTrade,
                     intrinio.realtime.options.Quote latestOptionQuote,
                     double riskFreeInterestRate,
                     double dividendYield,
                     double daysToExpiration,
                     double marketPrice,
                     double impliedVolatility,
                     double delta,
                     double gamma,
                     double theta,
                     double vega,
                     GreekCalculationMethod greekCalculationMethod){

}
