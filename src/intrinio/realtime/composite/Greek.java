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
    @Override
    public String toString(){
        return String.format("Contract: %s\r\n\tUnderlyingPrice: %s\r\n\tLast Trade: %s\r\n\tRFIR: %s\r\n\tdy: %s\r\n\tdays: %s\r\n\tMarket Price: %s\r\n\tIV: %s\r\n\tdelta: %s\r\n\tgamma: %s\r\n\ttheta: %s\r\n\tvega: %s \r\n\tMethod: %s",
                this.contract,
                this.latestEquityTrade.price(),
                this.latestOptionTrade.price(),
                this.riskFreeInterestRate,
                this.dividendYield,
                this.daysToExpiration,
                this.marketPrice,
                this.impliedVolatility,
                this.delta,
                this.gamma,
                this.theta,
                this.vega,
                this.greekCalculationMethod);
    }
}
