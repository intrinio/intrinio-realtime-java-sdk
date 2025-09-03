package SampleApp;

import intrinio.realtime.composite.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

public class CompositeSampleApp {
    public static void run(String[] args){
        String apiKey = "API_KEY_HERE";

        intrinio.realtime.options.Config optionsConfig = null;
        try{
            optionsConfig = new intrinio.realtime.options.Config(apiKey, intrinio.realtime.options.Provider.OPRA, null, new String[]{"MSFT", "NVDA", "AAPL"}, 8, false);
        }catch (Exception e){
            System.out.println("Error parsing options config: " + e.getMessage());
            return;
        }

        intrinio.realtime.equities.Config equitiesConfig = null;
        try{
            equitiesConfig = new intrinio.realtime.equities.Config(apiKey, intrinio.realtime.equities.Provider.NASDAQ_BASIC, null, new String[]{"MSFT", "NVDA", "AAPL"}, true, 4, false);
        }catch (Exception e){
            System.out.println("Error parsing equities config: " + e.getMessage());
            return;
        }

        //store the most recent values in a simple non-transactional cache that gives contextual information with the event.
        intrinio.realtime.composite.DataCache currentDataCache = DataCacheFactory.create();

        //Initialize Options Client and wire it to the cache
        //intrinio.realtime.options.OnUnusualActivity optionsUnusualActivityHandler = null;
        intrinio.realtime.options.Client optionsClient = new intrinio.realtime.options.Client(optionsConfig);
        optionsClient.setOnTrade(currentDataCache::setOptionsTrade);
        optionsClient.setOnQuote(currentDataCache::setOptionsQuote);
        optionsClient.setOnRefresh(currentDataCache::setOptionsRefresh);
        //optionsClient.setOnUnusualActivity(optionsUnusualActivityHandler);

        //Initialize Equities Client and wire it to the cache
        intrinio.realtime.equities.OnTrade equitiesTradeHandler = currentDataCache::setEquityTrade;
        intrinio.realtime.equities.OnQuote equitiesQuoteHandler = currentDataCache::setEquityQuote;
        intrinio.realtime.equities.Client equitiesClient = new intrinio.realtime.equities.Client(equitiesTradeHandler, equitiesQuoteHandler, equitiesConfig);

        //Display trade events with context
        currentDataCache.setEquitiesTradeUpdatedCallback((SecurityData securityData, DataCache dataCache, intrinio.realtime.equities.Trade trade) -> {
            intrinio.realtime.equities.Client.Log(securityData.getTickerSymbol() + " had a trade and also has " + securityData.getAllOptionsContractData().size() + " active contracts");
        });

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
                }catch (Exception e){
                    System.out.println("Error in summary timer: " + e.getMessage());
                }
            }
        };
        timer.schedule(task, 30000, 30000);
    }
}
