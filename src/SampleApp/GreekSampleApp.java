package SampleApp;

import intrinio.realtime.composite.DataCache;
import intrinio.realtime.composite.DataCacheFactory;
import intrinio.realtime.composite.Greek;
import intrinio.realtime.composite.GreekClient;
import intrinio.realtime.composite.GreekUpdateFrequency;
import intrinio.realtime.composite.OptionsContractData;
import intrinio.realtime.composite.SecurityData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sample application that mirrors the C# {@code GreekSampleApp}:
 * streams equities and options into a shared {@link DataCache}, runs {@link GreekClient}
 * for Black–Scholes Greeks, and periodically logs socket / Greek statistics.
 * <p>
 * Configure credentials and symbols via {@code intrinio/config.json} (loaded by the
 * equities and options {@code Config.load()} helpers), or construct configs inline.
 * </p>
 */
public class GreekSampleApp {

    private static Timer timer;
    private static GreekClient greekClient;
    private static DataCache dataCache;
    private static final ConcurrentHashMap<String, String> seenGreekTickers = new ConcurrentHashMap<>();

    private static intrinio.realtime.options.Client optionsClient;
    private static intrinio.realtime.options.Config optionsConfig;
    private static final AtomicLong optionsTradeEventCount = new AtomicLong(0L);
    private static final AtomicLong optionsQuoteEventCount = new AtomicLong(0L);
    private static final AtomicLong greekUpdatedEventCount = new AtomicLong(0L);

    private static intrinio.realtime.equities.Client equitiesClient;
    private static intrinio.realtime.equities.Config equitiesConfig;
    private static final AtomicLong equitiesTradeEventCount = new AtomicLong(0L);
    private static final AtomicLong equitiesQuoteEventCount = new AtomicLong(0L);
    private static final AtomicBoolean stopped = new AtomicBoolean(false);

    private static void onOptionsQuote(intrinio.realtime.options.Quote quote) {
        optionsQuoteEventCount.incrementAndGet();
    }

    private static void onOptionsTrade(intrinio.realtime.options.Trade trade) {
        optionsTradeEventCount.incrementAndGet();
    }

    private static void onEquitiesQuote(intrinio.realtime.equities.Quote quote) {
        equitiesQuoteEventCount.incrementAndGet();
    }

    private static void onEquitiesTrade(intrinio.realtime.equities.Trade trade) {
        equitiesTradeEventCount.incrementAndGet();
    }

    /**
     * Invoked by {@link GreekClient} / the data cache when a contract's Greek value is updated.
     */
    private static void onGreek(String key,
                                Greek datum,
                                OptionsContractData optionsContractData,
                                SecurityData securityData,
                                DataCache cache) {
        greekUpdatedEventCount.incrementAndGet();
        // Log("Greek: " + optionsContractData.getContract() + "\t\t" + key + "\t\t" + (datum != null ? datum.toString() : ""));
        if (securityData != null && optionsContractData != null) {
            seenGreekTickers.putIfAbsent(securityData.getTickerSymbol(), optionsContractData.getContract());
        }
    }

    private static void timerCallback() {
        try {
            if (optionsClient != null) {
                Log("Options Socket Stats - " + optionsClient.getStats()
                        + ", App trades: " + optionsTradeEventCount.get()
                        + ", App quotes: " + optionsQuoteEventCount.get());
            }
            if (equitiesClient != null) {
                Log("Equities Socket Stats - " + equitiesClient.getStats()
                        + ", App trades: " + equitiesTradeEventCount.get()
                        + ", App quotes: " + equitiesQuoteEventCount.get());
            }

            Log("Greek updates: " + greekUpdatedEventCount.get());
            Log("Data Cache Security Count: " + dataCache.getAllSecurityData().size());

            long dividendYieldCount = 0L;
            for (Map.Entry<String, SecurityData> entry : dataCache.getAllSecurityData().entrySet()) {
                if (entry.getValue() != null
                        && entry.getValue().getSupplementaryDatum(GreekClient.DIVIDEND_YIELD_KEY_NAME) != null) {
                    dividendYieldCount++;
                }
            }
            Log("Dividend Yield Count: " + dividendYieldCount);
            Log("Unique Securities with Greeks Count: " + seenGreekTickers.size());
        } catch (Exception e) {
            Log("Error in timer callback: " + e.getMessage());
        }
    }

    private static void shutdown() {
        Log("Stopping sample app");
        try {
            if (timer != null) {
                timer.cancel();
            }
        } catch (Exception ignored) {
        }
        try {
            if (optionsClient != null) {
                optionsClient.leave();
                optionsClient.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            if (equitiesClient != null) {
                equitiesClient.leave();
                equitiesClient.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            if (greekClient != null) {
                greekClient.stop();
            }
        } catch (Exception ignored) {
        }
        stopped.set(true);
    }

    private static void Log(String message) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println(dtf.format(LocalDateTime.now()) + " " + message);
    }

    /**
     * Entry point used by {@link SampleApp}.
     *
     * @param args unused
     */
    public static void run(String[] args) {
        Log("Starting Greek sample app");

        dataCache = DataCacheFactory.create();

        EnumSet<GreekUpdateFrequency> updateFrequency = EnumSet.of(
                GreekUpdateFrequency.EVERY_DIVIDEND_YIELD_UPDATE,
                GreekUpdateFrequency.EVERY_RISK_FREE_INTEREST_RATE_UPDATE,
                GreekUpdateFrequency.EVERY_OPTIONS_TRADE_UPDATE,
                GreekUpdateFrequency.EVERY_EQUITY_TRADE_UPDATE);

        // You can either automatically load config.json by doing nothing, or you can specify your own config and pass it in.
        // optionsConfig = new intrinio.realtime.options.Config("API_KEY_HERE", intrinio.realtime.options.Provider.OPRA, null, new String[]{}, 8, false);
        optionsConfig = intrinio.realtime.options.Config.load();
        if (optionsConfig == null) {
            Log("Failed to load options config from intrinio/config.json");
            return;
        }

        greekClient = new GreekClient(updateFrequency, GreekSampleApp::onGreek, optionsConfig.getOptionsApiKey(), dataCache);
        greekClient.addBlackScholes(optionsConfig.getOptionsProvider());
        // greekClient.tryAddOrUpdateGreekCalculation("MyGreekCalculation", MyCalculateNewGreekDelegate);
        // Hint: Use dataCache.setOptionSupplementalDatum inside your delegate to save values.
        greekClient.start();

        optionsClient = new intrinio.realtime.options.Client(optionsConfig);
        // Fan-out: app counters, data cache (drives Greek callbacks), and GreekClient ticker tracking
        optionsClient.setOnTrade(trade -> {
            onOptionsTrade(trade);
            dataCache.setOptionsTrade(trade);
            greekClient.onOptionsTrade(trade);
        });
        optionsClient.setOnQuote(quote -> {
            onOptionsQuote(quote);
            dataCache.setOptionsQuote(quote);
            greekClient.onOptionsQuote(quote);
        });

        try {
            optionsClient.start();
            optionsClient.join();
            // optionsClient.joinLobby(); // Firehose
            // optionsClient.join(new String[] { "AAPL", "GOOG", "MSFT" }); // Specify symbols at runtime
        } catch (Exception e) {
            Log("Error starting options client: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // equitiesConfig = new intrinio.realtime.equities.Config("API_KEY_HERE", intrinio.realtime.equities.Provider.NASDAQ_BASIC, null, new String[]{}, false, 4, false);
        equitiesConfig = intrinio.realtime.equities.Config.load();
        if (equitiesConfig == null) {
            Log("Failed to load equities config from intrinio/config.json");
            return;
        }

        equitiesClient = new intrinio.realtime.equities.Client(
                trade -> {
                    onEquitiesTrade(trade);
                    dataCache.setEquityTrade(trade);
                    greekClient.onEquityTrade(trade);
                },
                quote -> {
                    onEquitiesQuote(quote);
                    dataCache.setEquityQuote(quote);
                    greekClient.onEquityQuote(quote);
                },
                equitiesConfig);

        try {
            equitiesClient.start();
            equitiesClient.join();
            // equitiesClient.joinLobby(); // Firehose
            // equitiesClient.join(new String[] { "AAPL", "GOOG", "MSFT" }); // Specify symbols at runtime
        } catch (Exception e) {
            Log("Error starting equities client: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(GreekSampleApp::shutdown));

        timer = new Timer("GreekSampleApp-stats", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                timerCallback();
            }
        }, 60_000L, 60_000L);

        // Keep the main thread alive until shutdown
        while (!stopped.get()) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
