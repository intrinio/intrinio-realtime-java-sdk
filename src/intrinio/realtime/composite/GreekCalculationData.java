package intrinio.realtime.composite;

import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

public class GreekCalculationData {
    private double dividendYield;
    private double riskFreeInterestRate;
    private intrinio.realtime.equities.Trade underlyingTrade;
    private ConcurrentHashMap<String, OptionsContractData> contracts;

    public GreekCalculationData(intrinio.realtime.equities.Trade underlyingTrade, double dividendYield, double riskFreeInterestRate){
        this.dividendYield = dividendYield;
        this.riskFreeInterestRate = riskFreeInterestRate;
        this.underlyingTrade = underlyingTrade;
        this.contracts = new ConcurrentHashMap<String, OptionsContractData>();
    }

    public double getDividendYield(){
        return dividendYield;
    }

    public double getRiskFreeInterestRate(){
        return riskFreeInterestRate;
    }

    public intrinio.realtime.equities.Trade getUnderlyingTrade(){
        return underlyingTrade;
    }

    public OptionsContractData getOptionsContractData(String contract){
        return contracts.getOrDefault(contract, null);
    }

    public Collection<OptionsContractData> getOptionsContracts(){
        return contracts.values();
    }

    public void setDividendYield(double dividendYield){
        this.dividendYield = dividendYield;
    }

    public void setRiskFreeInterestRate(double riskFreeInterestRate){
        this.riskFreeInterestRate = riskFreeInterestRate;
    }

    public boolean setUnderlyingTrade(intrinio.realtime.equities.Trade trade){
        //dirty set
        if (trade.timestamp() > underlyingTrade.timestamp()) {
            underlyingTrade = trade;
            return true;
        }
        return false;
    }

    public boolean setOptionsTrade(intrinio.realtime.options.Trade trade){
        //dirty set
        if (contracts.containsKey(trade.contract())){
            return contracts.get(trade.contract()).setLatestTrade(trade);
        }
        else{
            OptionsContractData data = new OptionsContractData(trade.contract(), trade, null);
            contracts.putIfAbsent(trade.contract(), data);
            return true;
        }
    }

    public boolean setOptionsQuote(intrinio.realtime.options.Quote quote){
        //dirty set
        if (contracts.containsKey(quote.contract())){
            return contracts.get(quote.contract()).setLatestQuote(quote);
        }
        else{
            OptionsContractData data = new OptionsContractData(quote.contract(), null, quote);
            contracts.putIfAbsent(quote.contract(), data);
            return true;
        }
    }
}
