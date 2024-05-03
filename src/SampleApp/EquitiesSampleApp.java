package SampleApp;

import java.util.Timer;
import java.util.TimerTask;

public class EquitiesSampleApp {
    public static void run(String[] args)
    {
        intrinio.realtime.equities.Client.Log("Starting sample app");
        EquitiesTradeHandler equitiesTradeHandler = new EquitiesTradeHandler();
        EquitiesQuoteHandler equitiesQuoteHandler = new EquitiesQuoteHandler();
        //intrinio.realtime.equities.Config config = null; //You can either create a config class, or load it from the intrinio/config.json file
        //try { config = new Config("apiKeyHere", intrinio.realtime.equities.Provider.REALTIME, null, null, false, 2); } catch (Exception e) {e.printStackTrace();}
        //intrinio.realtime.equities.Client client = new Client(equitiesTradeHandler, equitiesQuoteHandler, config);
        intrinio.realtime.equities.Client client = new intrinio.realtime.equities.Client(equitiesTradeHandler, equitiesQuoteHandler);

        Runtime.getRuntime().addShutdownHook(new Thread( new Runnable() {
            public void run() {
                client.leave();
                intrinio.realtime.equities.Client.Log("Stopping sample app");
                client.stop();
            }
        }));

        try{
            client.start();
            client.join(); //Loads symbols from config
            //client.join(new String[] {"AAPL", "GOOG", "MSFT"}, false); //specify symbols at runtime
        }catch (Exception e) {
            e.printStackTrace();
        }

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                intrinio.realtime.equities.Client.Log(client.getStats());
                equitiesTradeHandler.tryLog();
                equitiesQuoteHandler.tryLog();
            }
        };
        timer.schedule(task, 30000, 30000);
    }
}
