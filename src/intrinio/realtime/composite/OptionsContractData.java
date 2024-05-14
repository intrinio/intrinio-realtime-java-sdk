package intrinio.realtime.composite;

public class OptionsContractData {
    private final String contract;
    private volatile intrinio.realtime.options.Trade latestTrade;
    private volatile intrinio.realtime.options.Quote latestQuote;

    public OptionsContractData(String contract, intrinio.realtime.options.Trade latestTrade, intrinio.realtime.options.Quote latestQuote){
        this.contract = contract;
        this.latestTrade = latestTrade;
        this.latestQuote = latestQuote;
    }

    public String getContract(){
        return this.contract;
    }

    public intrinio.realtime.options.Trade getLatestTrade(){
        return this.latestTrade;
    }

    public intrinio.realtime.options.Quote getLatestQuote(){
        return this.latestQuote;
    }

    public boolean setLatestTrade(intrinio.realtime.options.Trade trade){
        //dirty set
        if ((latestTrade == null) || (trade.timestamp() > latestTrade.timestamp())) {
            latestTrade = trade;
            return true;
        }
        return false;
    }

    public boolean setLatestQuote(intrinio.realtime.options.Quote quote){
        //dirty set
        if ((latestQuote == null) || (quote.timestamp() > latestQuote.timestamp())) {
            latestQuote = quote;
            return true;
        }
        return false;
    }
}
