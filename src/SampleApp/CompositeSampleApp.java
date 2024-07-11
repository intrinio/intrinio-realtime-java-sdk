package SampleApp;

import intrinio.realtime.composite.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class CompositeSampleApp {
    public static void run(String[] args){
        String apiKey = "API_KEY_HERE";
        String tickerSymbol = "NVDA";

        intrinio.realtime.options.Config optionsConfig = null;
        try{
            optionsConfig = new intrinio.realtime.options.Config(apiKey, intrinio.realtime.options.Provider.OPRA, null, new String[]{tickerSymbol}, 8);
        }catch (Exception e){
            System.out.println("Error parsing options config: " + e.getMessage());
            return;
        }

        intrinio.realtime.equities.Config equitiesConfig = null;
        try{
            equitiesConfig = new intrinio.realtime.equities.Config(apiKey, intrinio.realtime.equities.Provider.NASDAQ_BASIC, null, new String[]{tickerSymbol}, true, 4);
        }catch (Exception e){
            System.out.println("Error parsing equities config: " + e.getMessage());
            return;
        }

        intrinio.realtime.composite.DataCache currentDataCache = new CurrentDataCache();

        //Initialize Options Client and wire it to the cache
        intrinio.realtime.options.OnTrade optionsTradeHandler = currentDataCache::setOptionsTrade;
        intrinio.realtime.options.OnQuote optionsQuoteHandler = currentDataCache::setOptionsQuote;
        intrinio.realtime.options.OnRefresh optionsRefreshHandler = currentDataCache::setOptionsRefresh;
        intrinio.realtime.options.OnUnusualActivity optionsUnusualActivityHandler = null;
        intrinio.realtime.options.Client optionsClient = new intrinio.realtime.options.Client(optionsConfig);
        optionsClient.setOnTrade(optionsTradeHandler);
        optionsClient.setOnQuote(optionsQuoteHandler);
        optionsClient.setOnRefresh(optionsRefreshHandler);
        //optionsClient.setOnUnusualActivity(optionsUnusualActivityHandler);

        //Initialize Equities Client and wire it to the cache
        intrinio.realtime.equities.OnTrade equitiesTradeHandler = currentDataCache::setEquityTrade;
        intrinio.realtime.equities.OnQuote equitiesQuoteHandler = currentDataCache::setEquityQuote;
        intrinio.realtime.equities.Client equitiesClient = new intrinio.realtime.equities.Client(equitiesTradeHandler, equitiesQuoteHandler, equitiesConfig);

        Runtime.getRuntime().addShutdownHook(new Thread( new Runnable() {
            public void run() {
                intrinio.realtime.equities.Client.Log("Stopping sample app");
                optionsClient.leave();
                optionsClient.stop();
                equitiesClient.leave();
                equitiesClient.stop();
            }
        }));

        try{
            equitiesClient.start();
            //equitiesClient.joinLobby();
            equitiesClient.join();
            optionsClient.start();
            //optionsClient.joinLobby();
            optionsClient.join();
        }catch (Exception e) {
            e.printStackTrace();
        }

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                try{
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime now = LocalDateTime.now();
                    String date = dtf.format(now);
                    intrinio.realtime.options.Client.Log(date + " " + optionsClient.getStats());
                    intrinio.realtime.equities.Client.Log(date + " " + equitiesClient.getStats());
                    SecurityData securityData = currentDataCache.getSecurityData(tickerSymbol);
                    intrinio.realtime.options.Client.Log(date + " " + tickerSymbol + " latest trade:\r\n\t" + securityData.getEquitiesTrade());
                    intrinio.realtime.options.Client.Log(date + " " + tickerSymbol + " latest quote:\r\n\t" + securityData.getEquitiesQuote());
                    Map<String, OptionsContractData> contracts = securityData.getAllOptionsContractData();
                    intrinio.realtime.options.Client.Log(date + " " + tickerSymbol + " number of contracts: " + contracts.size());
                    OptionsContractData firstContract = contracts.entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(null);
                    intrinio.realtime.options.Client.Log(date + " " + tickerSymbol + " first contract trade:\r\n\t" + firstContract.getTrade());
                    intrinio.realtime.options.Client.Log(date + " " + tickerSymbol + " first contract quote:\r\n\t" + firstContract.getQuote());
                    intrinio.realtime.options.Client.Log(date + " " + tickerSymbol + " first contract refresh:\r\n\t" + firstContract.getRefresh());
                }catch (Exception e){
                    System.out.println("Error in summary timer: " + e.getMessage());
                }
            }
        };
        timer.schedule(task, 30000, 30000);
    }
}
