package SampleApp;

import intrinio.realtime.composite.GreekCalculationMethod;
import intrinio.realtime.composite.GreekClient;
import intrinio.realtime.composite.RefreshPeriod;
import intrinio.realtime.options.Config;
import intrinio.realtime.options.Provider;

import java.io.Console;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

public class GreekSampleApp {
    public static void run(String[] args){
        String apiKey = "API_KEY_HERE";

        intrinio.realtime.options.Config optionsConfig = null;
        try{
            optionsConfig = new intrinio.realtime.options.Config(apiKey, intrinio.realtime.options.Provider.OPRA, null, new String[0], 8);
        }catch (Exception e){
            System.out.println("Error parsing options config: " + e.getMessage());
            return;
        }

        intrinio.realtime.equities.Config equitiesConfig = null;
        try{
            equitiesConfig = new intrinio.realtime.equities.Config(apiKey, intrinio.realtime.equities.Provider.NASDAQ_BASIC, null, new String[0], true, 4);
        }catch (Exception e){
            System.out.println("Error parsing equities config: " + e.getMessage());
            return;
        }

        //Greek Client
        intrinio.realtime.composite.OnGreek greekEventHandler = (intrinio.realtime.composite.Greek greek) -> {};
        GreekClient greekClient = new GreekClient(greekEventHandler,
                GreekCalculationMethod.BLACK_SCHOLES,
                apiKey,
                0.0,
                RefreshPeriod.SIXTY_MINUTES,
                RefreshPeriod.SIXTY_MINUTES,
                false,
                false,
                false,
                3,
                4,
                4);

        //Options Client
        intrinio.realtime.options.OnTrade optionsTradeHandler = greekClient::onOptionsTrade;
        intrinio.realtime.options.OnQuote optionsQuoteHandler = greekClient::onOptionsQuote;
        intrinio.realtime.options.OnRefresh optionsRefreshHandler = null;
        intrinio.realtime.options.OnUnusualActivity optionsUnusualActivityHandler = null;
        intrinio.realtime.options.Client optionsClient = new intrinio.realtime.options.Client(optionsConfig);
        optionsClient.setOnTrade(optionsTradeHandler);
        optionsClient.setOnQuote(optionsQuoteHandler);

        //Equities Client
        intrinio.realtime.equities.OnTrade equitiesTradeHandler = greekClient::onEquitiesTrade;
        intrinio.realtime.equities.OnQuote equitiesQuoteHandler = null;
        intrinio.realtime.equities.Client equitiesClient = new intrinio.realtime.equities.Client(equitiesTradeHandler, equitiesQuoteHandler, equitiesConfig);

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
            equitiesClient.joinLobby();
            //equitiesClient.join();
            optionsClient.start();
            optionsClient.joinLobby();
            //optionsClient.join();
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
                    String randomContract = greekClient.getContracts("NVDA").getFirst();
                    intrinio.realtime.composite.Greek greek = greekClient.getGreek("NVDA", randomContract);
                    intrinio.realtime.equities.Client.Log(greek == null ? "reporting greek not found" : greek.toString());
                }catch (Exception e){
                    System.out.println("Error in summary timer: " + e.getMessage());
                }
            }
        };
        timer.schedule(task, 30000, 30000);
    }
}
