package SampleApp;

import intrinio.realtime.composite.GreekCalculationMethod;
import intrinio.realtime.composite.GreekClient;
import intrinio.realtime.composite.RefreshPeriod;

import java.io.Console;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

public class GreekSampleApp {
    public static void run(String[] args){
        //Greek Client
        intrinio.realtime.composite.OnGreek greekEventHandler = (intrinio.realtime.composite.Greek greek) -> {};
        GreekClient greekClient = new GreekClient(greekEventHandler, GreekCalculationMethod.BLACK_SCHOLES, "api_key", 0.0, RefreshPeriod.SIXTY_MINUTES, RefreshPeriod.SIXTY_MINUTES, false, false, false, 3, 4, 4);

        //Options Client
        intrinio.realtime.options.OnTrade optionsTradeHandler = greekClient::onOptionsTrade;
        intrinio.realtime.options.OnQuote optionsQuoteHandler = greekClient::onOptionsQuote;
        intrinio.realtime.options.OnRefresh optionsRefreshHandler = null;
        intrinio.realtime.options.OnUnusualActivity optionsUnusualActivityHandler = null;
        intrinio.realtime.options.Client optionsClient = new intrinio.realtime.options.Client();
        optionsClient.setOnTrade(optionsTradeHandler);
        optionsClient.setOnQuote(optionsQuoteHandler);

        //Equities Client
        intrinio.realtime.equities.OnTrade equitiesTradeHandler = greekClient::onEquitiesTrade;
        intrinio.realtime.equities.OnQuote equitiesQuoteHandler = null;
        intrinio.realtime.equities.Client equitiesClient = new intrinio.realtime.equities.Client(equitiesTradeHandler, equitiesQuoteHandler);

        Runtime.getRuntime().addShutdownHook(new Thread( new Runnable() {
            public void run() {
                intrinio.realtime.equities.Client.Log("Stopping sample app");
                optionsClient.leave();
                optionsClient.stop();
                equitiesClient.leave();
                equitiesClient.stop();
                greekClient.stop();
            }
        }));

        try{
            greekClient.start();
            equitiesClient.start();
            equitiesClient.join(); //Loads symbols from config
            optionsClient.start();
            optionsClient.join();
        }catch (Exception e) {
            e.printStackTrace();
        }

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();
                String date = dtf.format(now);
                intrinio.realtime.options.Client.Log(date + " " + optionsClient.getStats());
                intrinio.realtime.equities.Client.Log(date + " " + equitiesClient.getStats());
                intrinio.realtime.equities.Client.Log(greekClient.getGreek("AAPL", "AAPL__240510P00165000").toString());
            }
        };
        timer.schedule(task, 30000, 30000);
    }
}
