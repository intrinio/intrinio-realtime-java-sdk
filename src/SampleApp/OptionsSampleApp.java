package SampleApp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

public class OptionsSampleApp {
    public static void run(String[] args)
    {
        intrinio.realtime.options.Client.Log("Starting sample app");

        // Create only the handlers/callbacks that you need
        // These will get registered below
        OptionsTradeHandler optionsTradeHandler = new OptionsTradeHandler();
        OptionsQuoteHandler optionsQuoteHandler = new OptionsQuoteHandler();
        OptionsRefreshHandler optionsRefreshHandler = new OptionsRefreshHandler();
        OptionsUnusualActivityHandler optionsUnusualActivityHandler = new OptionsUnusualActivityHandler();

        // You can either create a config class or default to using the intrinio/config.json file
        //intrinio.realtime.options.Config config = null;
        //try {config = new intrinio.realtime.options.Config("apiKeyHere", intrinio.realtime.options.Provider.OPRA, null, new String[]{"GOOG", "AAPL__210917C00130000"}, 8, false);} catch (Exception e) {e.printStackTrace();}
        //intrinio.realtime.options.Client client = new intrinio.realtime.options.Client(config);
        intrinio.realtime.options.Client client = new intrinio.realtime.options.Client();

        // Register a callback for a graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread( new Runnable() {
            public void run() {
                client.leave();
                intrinio.realtime.options.Client.Log("Stopping sample app");
                client.stop();
            }
        }));

        try {
            // Register only the callbacks that you want.
            // Take special care when registering the 'OnQuote' handler as it will increase throughput by ~10x
            client.setOnTrade(optionsTradeHandler);
            client.setOnQuote(optionsQuoteHandler);
            client.setOnRefresh(optionsRefreshHandler);
            client.setOnUnusualActivity(optionsUnusualActivityHandler);

            // Start the client
            client.start();

            // Use this to subscribe to a static list of symbols (option contracts) provided in config.json
            client.join();

            // Use this to subscribe to the entire univers of symbols (option contracts). This requires special permission.
            //client.joinLobby();

            // Use this to subscribe, dynamically, to an option chain (all option contracts for a given underlying symbol).
            //client.join("AAPL");

            // Use this to subscribe, dynamically, to a specific option contract.
            //client.join("AAP___230616P00250000");

            // Use this to subscribe, dynamically, a list of specific option contracts or option chains.
            //client.join(new String[] {"GOOG__210917C01040000", "MSFT__210917C00180000", "AAPL__210917C00130000", "TSLA"});

        } catch (Exception e) {
            e.printStackTrace();
        }

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();
                String date = dtf.format(now);
                intrinio.realtime.options.Client.Log(date + " " + client.getStats());
                String appStats = String.format(
                        "%s Messages (Trades = %d, Quotes = %d, Refreshes = %d, Blocks = %d, Sweeps = %d, Larges = %d, UnusualSweeps = %d)",
                        date,
                        optionsTradeHandler.tradeCount.get(),
                        optionsQuoteHandler.quoteCount.get(),
                        optionsRefreshHandler.rCount.get(),
                        optionsUnusualActivityHandler.blockCount.get(),
                        optionsUnusualActivityHandler.sweepCount.get(),
                        optionsUnusualActivityHandler.largeTradeCount.get(),
                        optionsUnusualActivityHandler.unusualSweepCount.get());
                intrinio.realtime.options.Client.Log(appStats);
            }
        };
        timer.schedule(task, 10000, 10000);
    }
}
