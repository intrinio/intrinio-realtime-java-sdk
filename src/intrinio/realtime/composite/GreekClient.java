package intrinio.realtime.composite;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Calculates realtime option Greeks from a stream of equities and options trades/quotes,
 * combined with REST-fetched risk-free rates and dividend yields.
 * <p>
 * This client is intentionally <strong>non-transactional</strong>: cache reads and writes
 * use concurrent maps and dirty timestamp checks (see {@link CurrentDataCache}). Under load,
 * a Greek may be computed from a mix of slightly stale and fresh fields. That matches the
 * C# SDK design and prioritizes throughput over strict snapshot consistency.
 * </p>
 * <p>
 * Wire this client into your equities/options WebSocket handlers by calling
 * {@link #onEquityTrade}, {@link #onEquityQuote}, {@link #onOptionsTrade}, and
 * {@link #onOptionsQuote}. When an external {@link DataCache} is supplied, market data
 * is expected to be written to that cache by the caller (or a parallel handler); this
 * client still tracks seen tickers for dividend refresh. When no cache is supplied,
 * this client owns an internal cache and writes trades/quotes into it.
 * </p>
 * <p>
 * REST calls use {@link HttpURLConnection} and Gson only (no additional dependencies).
 * </p>
 */
public class GreekClient {

    //region Constants

    /** Supplemental datum key for trailing dividend yield on a security. */
    public static final String DIVIDEND_YIELD_KEY_NAME = "DividendYield";

    /** Top-level supplemental datum key for the risk-free interest rate. */
    public static final String RISK_FREE_INTEREST_RATE_KEY_NAME = "RiskFreeInterestRate";

    /** Greek cache key used by the built-in Black–Scholes calculator. */
    public static final String BLACK_SCHOLES_KEY_NAME = "IntrinioBlackScholes";

    /** Intrinio API v2 base URL. */
    private static final String API_BASE = "https://api-v2.intrinio.com";

    //endregion Constants

    //region Data Members

    /** Shared (or self-owned) non-transactional market-data cache. */
    private final DataCache cache;

    /** Named Greek calculators registered via {@link #tryAddOrUpdateGreekCalculation}. */
    private final ConcurrentHashMap<String, CalculateNewGreek> calcLookup;

    /** Replace-style Greek cache update: always keep the newly computed value. */
    private final GreekDataUpdate updateFuncGreek =
            (String key, Greek oldValue, Greek newValue) -> newValue;

    /** Replace-style numeric supplemental update: always keep the new value. */
    private final SupplementalDatumUpdate updateFuncNumber =
            (String key, Double oldValue, Double newValue) -> newValue;

    /** Tracks tickers observed on the wire or from REST, and last dividend-refresh time. */
    private final ConcurrentHashMap<String, Instant> seenTickers;

    /** Periodic risk-free rate fetch. */
    private Timer riskFreeInterestRateFetchTimer;

    /** Periodic dividend-yield refresh. */
    private Timer dividendFetchTimer;

    /** Background work for bulk REST seeding. */
    private final ExecutorService startupExecutor;

    /** Intrinio API key used for REST dividend / rate / universe calls. */
    private final String apiKey;

    /** Hours between dividend-yield refreshes for a given ticker. */
    private volatile int dividendYieldUpdatePeriodHours = 4;

    /** Minimum spacing between REST calls to avoid rate limiting (milliseconds). */
    private volatile int apiCallSpacerMilliseconds = 1100;

    /** Guards concurrent dividend bulk/refresh work. */
    private final AtomicBoolean dividendYieldWorking = new AtomicBoolean(false);

    /**
     * {@code true} when this client created {@link #cache} itself and therefore must
     * write equities/options events into the cache from {@code on*} handlers.
     */
    private final boolean selfCache;

    /** Optional user callback invoked when Greek data is written to the cache. */
    private volatile OnOptionsContractGreekDataUpdated onGreekValueUpdated;

    //endregion Data Members

    //region Constructors

    /**
     * Creates a {@code GreekClient} that calculates realtime Greeks from equities and options streams.
     *
     * @param greekUpdateFrequency flags controlling when Greeks are recalculated
     * @param onGreekValueUpdated  callback invoked when a contract's Greek data is updated; may be {@code null}
     * @param apiKey               Intrinio API key for REST fetches (rates, dividends, tickers)
     * @param cache                optional external {@link DataCache}; if {@code null}, an internal cache is created
     */
    public GreekClient(EnumSet<GreekUpdateFrequency> greekUpdateFrequency,
                       OnOptionsContractGreekDataUpdated onGreekValueUpdated,
                       String apiKey,
                       DataCache cache) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must be provided");
        }
        if (greekUpdateFrequency == null || greekUpdateFrequency.isEmpty()) {
            throw new IllegalArgumentException("greekUpdateFrequency must contain at least one flag");
        }

        this.apiKey = apiKey;
        this.apiCallSpacerMilliseconds = 1100;
        this.dividendYieldUpdatePeriodHours = 4;
        this.selfCache = (cache == null);
        this.cache = (cache != null) ? cache : DataCacheFactory.create();
        this.seenTickers = new ConcurrentHashMap<>();
        this.calcLookup = new ConcurrentHashMap<>();
        this.startupExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "GreekClient-startup");
            t.setDaemon(true);
            return t;
        });

        setOnGreekValueUpdated(onGreekValueUpdated);
        registerUpdateFrequencyCallbacks(greekUpdateFrequency);
    }

    /**
     * Creates a {@code GreekClient} with an internally owned {@link DataCache}.
     *
     * @param greekUpdateFrequency flags controlling when Greeks are recalculated
     * @param onGreekValueUpdated  callback invoked when a contract's Greek data is updated
     * @param apiKey               Intrinio API key
     */
    public GreekClient(EnumSet<GreekUpdateFrequency> greekUpdateFrequency,
                       OnOptionsContractGreekDataUpdated onGreekValueUpdated,
                       String apiKey) {
        this(greekUpdateFrequency, onGreekValueUpdated, apiKey, null);
    }

    //endregion Constructors

    //region Public Properties

    /**
     * @return the cache used by this client (shared or self-owned)
     */
    public DataCache getCache() {
        return cache;
    }

    /**
     * @return hours between dividend-yield refreshes per ticker
     */
    public int getDividendYieldUpdatePeriodHours() {
        return dividendYieldUpdatePeriodHours;
    }

    /**
     * @param dividendYieldUpdatePeriodHours hours between dividend-yield refreshes per ticker
     */
    public void setDividendYieldUpdatePeriodHours(int dividendYieldUpdatePeriodHours) {
        this.dividendYieldUpdatePeriodHours = dividendYieldUpdatePeriodHours;
    }

    /**
     * @return milliseconds to sleep between REST calls
     */
    public int getApiCallSpacerMilliseconds() {
        return apiCallSpacerMilliseconds;
    }

    /**
     * @param apiCallSpacerMilliseconds milliseconds to sleep between REST calls
     */
    public void setApiCallSpacerMilliseconds(int apiCallSpacerMilliseconds) {
        this.apiCallSpacerMilliseconds = apiCallSpacerMilliseconds;
    }

    /**
     * Registers (or chains) a callback for Greek cache updates on the underlying {@link DataCache}.
     *
     * @param onGreekValueUpdated callback to invoke when option Greek data is updated; may be {@code null}
     */
    public void setOnGreekValueUpdated(OnOptionsContractGreekDataUpdated onGreekValueUpdated) {
        this.onGreekValueUpdated = onGreekValueUpdated;
        if (onGreekValueUpdated == null) {
            return;
        }
        OnOptionsContractGreekDataUpdated existing = cache.getOptionsContractGreekDataUpdatedCallback();
        if (existing == null) {
            cache.setOptionsContractGreekDataUpdatedCallback(onGreekValueUpdated);
        } else if (existing != onGreekValueUpdated) {
            cache.setOptionsContractGreekDataUpdatedCallback(
                    (key, datum, optionsContractData, securityData, dataCache) -> {
                        existing.onOptionsContractGreekDataUpdated(key, datum, optionsContractData, securityData, dataCache);
                        onGreekValueUpdated.onOptionsContractGreekDataUpdated(key, datum, optionsContractData, securityData, dataCache);
                    });
        }
    }

    //endregion Public Properties

    //region Public Methods

    /**
     * Starts background REST seeding (optionable tickers, securities, historical dividend metrics)
     * and periodic timers for risk-free rate and dividend-yield refresh.
     */
    public void start() {
        startupExecutor.execute(() -> {
            log("Fetching company daily metrics in bulk");
            for (int i = 365; i >= 0; i--) {
                fetchInitialCompanyDividends(i);
            }
        });
        startupExecutor.execute(() -> {
            log("Fetching list of tickers with options associated");
            cacheListOfOptionableTickers();
        });
        startupExecutor.execute(() -> {
            log("Fetching list of all securities.");
            cacheAllSecurities();
        });

        log("Fetching risk free interest rate and periodically additional new dividend yields");
        riskFreeInterestRateFetchTimer = new Timer("GreekClient-riskFreeRate", true);
        riskFreeInterestRateFetchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchRiskFreeInterestRate();
            }
        }, 0L, 11L * 60L * 60L * 1000L);

        dividendFetchTimer = new Timer("GreekClient-dividends", true);
        dividendFetchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshDividendYields();
            }
        }, 60_000L, 30_000L);
    }

    /**
     * Stops periodic timers and shuts down startup background work.
     */
    public void stop() {
        if (riskFreeInterestRateFetchTimer != null) {
            try {
                riskFreeInterestRateFetchTimer.cancel();
            } catch (Exception ignored) {
            }
            riskFreeInterestRateFetchTimer = null;
        }
        if (dividendFetchTimer != null) {
            try {
                dividendFetchTimer.cancel();
            } catch (Exception ignored) {
            }
            dividendFetchTimer = null;
        }
        startupExecutor.shutdownNow();
    }

    /**
     * Equities trade handler. Tracks the ticker and, in self-cache mode, writes the trade into the cache.
     *
     * @param trade equities trade event
     */
    public void onEquityTrade(intrinio.realtime.equities.Trade trade) {
        try {
            if (trade == null) {
                return;
            }
            seenTickers.putIfAbsent(trade.symbol().intern(), Instant.EPOCH);
            if (selfCache) {
                cache.setEquityTrade(trade);
            }
        } catch (Exception e) {
            log("Error on handling equity trade in GreekClient: " + e.getMessage());
        }
    }

    /**
     * Equities quote handler. Tracks the ticker and, in self-cache mode, writes the quote into the cache.
     *
     * @param quote equities quote event
     */
    public void onEquityQuote(intrinio.realtime.equities.Quote quote) {
        try {
            if (quote == null) {
                return;
            }
            seenTickers.putIfAbsent(quote.symbol().intern(), Instant.EPOCH);
            if (selfCache) {
                cache.setEquityQuote(quote);
            }
        } catch (Exception e) {
            log("Error on handling equity quote in GreekClient: " + e.getMessage());
        }
    }

    /**
     * Options trade handler. Tracks the underlying and, in self-cache mode, writes the trade into the cache.
     *
     * @param trade options trade event
     */
    public void onOptionsTrade(intrinio.realtime.options.Trade trade) {
        try {
            if (trade == null) {
                return;
            }
            seenTickers.putIfAbsent(trade.getUnderlyingSymbol().intern(), Instant.EPOCH);
            if (selfCache) {
                cache.setOptionsTrade(trade);
            }
        } catch (Exception e) {
            log("Error on handling option trade in GreekClient: " + e.getMessage());
        }
    }

    /**
     * Options quote handler. Tracks the underlying and, in self-cache mode, writes the quote into the cache.
     *
     * @param quote options quote event
     */
    public void onOptionsQuote(intrinio.realtime.options.Quote quote) {
        try {
            if (quote == null) {
                return;
            }
            seenTickers.putIfAbsent(quote.getUnderlyingSymbol().intern(), Instant.EPOCH);
            if (selfCache) {
                cache.setOptionsQuote(quote);
            }
        } catch (Exception e) {
            log("Error on handling option quote in GreekClient: " + e.getMessage());
        }
    }

    /**
     * Options refresh handler (no-op; provided for symmetry with the C# plug-in interface).
     *
     * @param refresh options refresh event
     */
    public void onOptionsRefresh(intrinio.realtime.options.Refresh refresh) {
        // intentionally empty
    }

    /**
     * Options unusual-activity handler (no-op; provided for symmetry with the C# plug-in interface).
     *
     * @param unusualActivity unusual activity event
     */
    public void onOptionsUnusualActivity(intrinio.realtime.options.UnusualActivity unusualActivity) {
        // intentionally empty
    }

    /**
     * Registers or replaces a named Greek calculation strategy.
     *
     * @param name calculator name (also used as the Greek cache key by built-in strategies)
     * @param calc calculation delegate; must not be {@code null}
     * @return {@code true} if the calculator was stored
     */
    public boolean tryAddOrUpdateGreekCalculation(String name, CalculateNewGreek calc) {
        if (name == null || name.isBlank() || calc == null) {
            return false;
        }
        calcLookup.put(name, calc);
        return true;
    }

    /**
     * Registers the built-in Black–Scholes calculator appropriate for the given options provider.
     * <ul>
     *   <li>{@link intrinio.realtime.options.Provider#OPTIONS_EDGE} — uses option trade mid (trade price)</li>
     *   <li>All other providers (including OPRA) — use option quote mid and ask/bid IVs</li>
     * </ul>
     *
     * @param provider options provider; {@code null} defaults to OPRA-style quote-based calculation
     */
    public void addBlackScholes(intrinio.realtime.options.Provider provider) {
        if (provider == null) {
            provider = intrinio.realtime.options.Provider.OPRA;
        }
        switch (provider) {
            case OPTIONS_EDGE:
                tryAddOrUpdateGreekCalculation(BLACK_SCHOLES_KEY_NAME, this::blackScholesCalcOptionsEdge);
                break;
            case OPRA:
            case MANUAL:
            case NONE:
            default:
                tryAddOrUpdateGreekCalculation(BLACK_SCHOLES_KEY_NAME, this::blackScholesCalc);
                break;
        }
    }

    /**
     * Registers the built-in Black–Scholes calculator using OPRA-style quote-based inputs.
     */
    public void addBlackScholes() {
        addBlackScholes(intrinio.realtime.options.Provider.OPRA);
    }

    //endregion Public Methods

    //region Private REST / Startup

    /**
     * Fetches the universe of optionable tickers into {@link #seenTickers}.
     */
    private void cacheListOfOptionableTickers() {
        try {
            String body = httpGet("/options/tickers");
            if (body == null) {
                return;
            }
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray tickers = root.has("tickers") && root.get("tickers").isJsonArray()
                    ? root.getAsJsonArray("tickers")
                    : null;
            if (tickers == null) {
                return;
            }
            int count = 0;
            for (JsonElement el : tickers) {
                if (el != null && el.isJsonPrimitive()) {
                    String ticker = el.getAsString();
                    if (ticker != null && !ticker.isBlank()) {
                        seenTickers.putIfAbsent(ticker.intern(), Instant.EPOCH);
                        count++;
                    }
                }
            }
            log("Found " + count + " optionable tickers.");
        } catch (Exception e) {
            log("Error in cacheListOfOptionableTickers - " + e + ", " + e.getMessage());
        }
    }

    /**
     * Pages through active primary US listings and records tickers for dividend refresh.
     */
    private void cacheAllSecurities() {
        try {
            String nextPage = null;
            do {
                try {
                    StringBuilder path = new StringBuilder("/securities?active=true&delisted=false&primary_listing=true&composite_mic=USCOMP&page_size=9999");
                    if (nextPage != null && !nextPage.isBlank()) {
                        path.append("&next_page=").append(urlEncode(nextPage));
                    }
                    String body = httpGet(path.toString());
                    if (body == null) {
                        break;
                    }
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonArray securities = root.has("securities") && root.get("securities").isJsonArray()
                            ? root.getAsJsonArray("securities")
                            : null;
                    if (securities != null) {
                        for (JsonElement el : securities) {
                            if (el == null || !el.isJsonObject()) {
                                continue;
                            }
                            JsonObject sec = el.getAsJsonObject();
                            if (sec.has("ticker") && !sec.get("ticker").isJsonNull()) {
                                String ticker = sec.get("ticker").getAsString();
                                if (ticker != null && !ticker.isBlank()) {
                                    seenTickers.putIfAbsent(ticker.intern(), Instant.EPOCH);
                                }
                            }
                        }
                    }
                    nextPage = (root.has("next_page") && !root.get("next_page").isJsonNull())
                            ? root.get("next_page").getAsString()
                            : null;
                    sleepQuietly(apiCallSpacerMilliseconds);
                } catch (Exception e) {
                    log("Error: " + e + "; " + e.getMessage());
                    sleepQuietly(apiCallSpacerMilliseconds);
                    break;
                }
            } while (nextPage != null && !nextPage.isBlank());
        } catch (Exception e) {
            log("Error: " + e + "; " + e.getMessage());
        }
    }

    /**
     * Loads company daily metrics for a historical day offset and seeds dividend yields.
     *
     * @param daysAgo number of days before today
     */
    private void fetchInitialCompanyDividends(int daysAgo) {
        if (!dividendYieldWorking.compareAndSet(false, true)) {
            return;
        }
        try {
            String nextPage = null;
            LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo);
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            do {
                StringBuilder path = new StringBuilder("/companies/daily_metrics?on_date=")
                        .append(urlEncode(dateStr))
                        .append("&page_size=1000");
                if (nextPage != null && !nextPage.isBlank()) {
                    path.append("&next_page=").append(urlEncode(nextPage));
                }
                String body = httpGet(path.toString());
                if (body == null) {
                    break;
                }
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonArray dailyMetrics = root.has("daily_metrics") && root.get("daily_metrics").isJsonArray()
                        ? root.getAsJsonArray("daily_metrics")
                        : null;
                if (dailyMetrics != null) {
                    for (JsonElement el : dailyMetrics) {
                        if (el == null || !el.isJsonObject()) {
                            continue;
                        }
                        JsonObject metric = el.getAsJsonObject();
                        JsonObject company = (metric.has("company") && metric.get("company").isJsonObject())
                                ? metric.getAsJsonObject("company")
                                : null;
                        if (company == null || !company.has("ticker") || company.get("ticker").isJsonNull()) {
                            continue;
                        }
                        String ticker = company.get("ticker").getAsString();
                        if (ticker == null || ticker.isBlank()) {
                            continue;
                        }
                        if (metric.has("dividend_yield") && !metric.get("dividend_yield").isJsonNull()) {
                            double yield = metric.get("dividend_yield").getAsDouble();
                            cache.setSecuritySupplementalDatum(ticker, DIVIDEND_YIELD_KEY_NAME, yield, updateFuncNumber);
                            seenTickers.put(ticker.intern(), Instant.now());
                        }
                    }
                }
                nextPage = (root.has("next_page") && !root.get("next_page").isJsonNull())
                        ? root.get("next_page").getAsString()
                        : null;
                sleepQuietly(apiCallSpacerMilliseconds);
            } while (nextPage != null && !nextPage.isBlank());
        } catch (Exception e) {
            log("Error: " + e + "; " + e.getMessage());
        } finally {
            dividendYieldWorking.set(false);
        }
    }

    /**
     * Refreshes trailing dividend yield for a single ticker via security data-point REST calls.
     *
     * @param ticker equity ticker symbol
     */
    private void refreshDividendYield(String ticker) {
        final String dividendYieldTag = "trailing_dividend_yield";
        try {
            String securityBody = httpGet("/securities/" + urlEncode(ticker + ":US"));
            sleepQuietly(apiCallSpacerMilliseconds);
            if (securityBody == null) {
                throw new IllegalStateException("No security body for " + ticker);
            }
            JsonObject security = JsonParser.parseString(securityBody).getAsJsonObject();
            if (!security.has("id") || security.get("id").isJsonNull()) {
                throw new IllegalStateException("No security id for " + ticker);
            }
            String securityId = security.get("id").getAsString();
            String yieldBody = httpGet("/securities/" + urlEncode(securityId)
                    + "/data_point/" + urlEncode(dividendYieldTag) + "/number");
            double yield = 0.0D;
            if (yieldBody != null && !yieldBody.isBlank()) {
                // Endpoint returns a bare JSON number
                yield = JsonParser.parseString(yieldBody.trim()).getAsDouble();
            }
            cache.setSecuritySupplementalDatum(ticker, DIVIDEND_YIELD_KEY_NAME, yield, updateFuncNumber);
            seenTickers.put(ticker.intern(), Instant.now());
            sleepQuietly(apiCallSpacerMilliseconds);
        } catch (Exception e) {
            cache.setSecuritySupplementalDatum(ticker, DIVIDEND_YIELD_KEY_NAME, 0.0D, updateFuncNumber);
            seenTickers.put(ticker.intern(), Instant.now());
            sleepQuietly(apiCallSpacerMilliseconds);
        }
    }

    /**
     * Refreshes dividend yields for major index underlyings and any seen ticker older than the refresh period.
     */
    private void refreshDividendYields() {
        if (!dividendYieldWorking.compareAndSet(false, true)) {
            return;
        }
        try {
            refreshDividendYield("SPY");
            refreshDividendYield("SPX");
            refreshDividendYield("SPXW");
            refreshDividendYield("RUT");
            refreshDividendYield("VIX");
            log("Refreshing dividend yields for " + seenTickers.size() + " tickers...");
            Instant cutoff = Instant.now().minusSeconds(dividendYieldUpdatePeriodHours * 3600L);
            for (Map.Entry<String, Instant> entry : seenTickers.entrySet()) {
                Instant last = entry.getValue();
                if (last == null || last.isBefore(cutoff)) {
                    refreshDividendYield(entry.getKey());
                }
            }
        } catch (Exception e) {
            log("Error: " + e + "; " + e.getMessage());
        } finally {
            dividendYieldWorking.set(false);
        }
    }

    /**
     * Fetches the 3-month Treasury bill rate ({@code $DTB3}) and stores it as a top-level supplemental datum.
     */
    private void fetchRiskFreeInterestRate() {
        boolean success = false;
        int tryCount = 0;
        do {
            tryCount++;
            try {
                String body = httpGet("/indices/economic/" + urlEncode("$DTB3") + "/data_point/level/number");
                if (body != null && !body.isBlank()) {
                    double level = JsonParser.parseString(body.trim()).getAsDouble();
                    cache.setSupplementaryDatum(RISK_FREE_INTEREST_RATE_KEY_NAME, level / 100.0D, updateFuncNumber);
                    success = true;
                }
                if (!success) {
                    sleepQuietly(10_000);
                }
            } catch (Exception e) {
                log("Error: " + e + "; " + e.getMessage());
            }
        } while (!success && tryCount < 10);
    }

    /**
     * Performs a GET against Intrinio API v2 with the configured API key.
     *
     * @param pathAndQuery path beginning with {@code /}, optionally including query string
     * @return response body, or {@code null} on non-success / error
     */
    private String httpGet(String pathAndQuery) {
        HttpURLConnection connection = null;
        try {
            String separator = pathAndQuery.contains("?") ? "&" : "?";
            String full = API_BASE + pathAndQuery + separator + "api_key=" + urlEncode(apiKey);
            URL url = URI.create(full).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(60_000);
            int code = connection.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(),
                    StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            if (code < 200 || code >= 300) {
                log("HTTP " + code + " for " + pathAndQuery + ": " + sb);
                return null;
            }
            return sb.toString();
        } catch (Exception e) {
            log("HTTP error for " + pathAndQuery + ": " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        System.out.println(message);
    }

    //endregion Private REST / Startup

    //region Private Greek Updates

    /**
     * Chains this client's Greek recalculation onto the data-cache callbacks selected by frequency flags.
     * Existing user callbacks (if any) are preserved and invoked first.
     */
    private void registerUpdateFrequencyCallbacks(EnumSet<GreekUpdateFrequency> frequencies) {
        if (frequencies.contains(GreekUpdateFrequency.EVERY_OPTIONS_TRADE_UPDATE)) {
            OnOptionsTradeUpdated previous = cache.getOptionsTradeUpdatedCallback();
            cache.setOptionsTradeUpdatedCallback((optionsContractData, dataCache, securityData, trade) -> {
                if (previous != null) {
                    previous.onOptionsTradeUpdated(optionsContractData, dataCache, securityData, trade);
                }
                updateGreeks(optionsContractData, dataCache, securityData);
            });
        }

        if (frequencies.contains(GreekUpdateFrequency.EVERY_OPTIONS_QUOTE_UPDATE)) {
            OnOptionsQuoteUpdated previous = cache.getOptionsQuoteUpdatedCallback();
            cache.setOptionsQuoteUpdatedCallback((optionsContractData, dataCache, securityData, quote) -> {
                if (previous != null) {
                    previous.onOptionsQuoteUpdated(optionsContractData, dataCache, securityData, quote);
                }
                updateGreeks(optionsContractData, dataCache, securityData);
            });
        }

        if (frequencies.contains(GreekUpdateFrequency.EVERY_DIVIDEND_YIELD_UPDATE)) {
            OnSecuritySupplementalDatumUpdated previous = cache.getSecuritySupplementalDatumUpdatedCallback();
            cache.setSecuritySupplementalDatumUpdatedCallback((key, datum, securityData, dataCache) -> {
                if (previous != null) {
                    previous.onSecuritySupplementalDatumUpdated(key, datum, securityData, dataCache);
                }
                updateGreeksFromSecuritySupplemental(key, datum, securityData, dataCache);
            });
        }

        if (frequencies.contains(GreekUpdateFrequency.EVERY_RISK_FREE_INTEREST_RATE_UPDATE)) {
            OnSupplementalDatumUpdated previous = cache.getSupplementalDatumUpdatedCallback();
            cache.setSupplementalDatumUpdatedCallback((key, datum, dataCache) -> {
                if (previous != null) {
                    previous.onSupplementalDatumUpdated(key, datum, dataCache);
                }
                updateGreeksFromTopLevelSupplemental(key, datum, dataCache);
            });
        }

        if (frequencies.contains(GreekUpdateFrequency.EVERY_EQUITY_TRADE_UPDATE)) {
            OnEquitiesTradeUpdated previous = cache.getEquitiesTradeUpdatedCallback();
            cache.setEquitiesTradeUpdatedCallback((securityData, dataCache, trade) -> {
                if (previous != null) {
                    previous.onEquitiesTradeUpdated(securityData, dataCache, trade);
                }
                updateGreeks(securityData, dataCache);
            });
        }

        if (frequencies.contains(GreekUpdateFrequency.EVERY_EQUITY_QUOTE_UPDATE)) {
            OnEquitiesQuoteUpdated previous = cache.getEquitiesQuoteUpdatedCallback();
            cache.setEquitiesQuoteUpdatedCallback((securityData, dataCache, quote) -> {
                if (previous != null) {
                    previous.onEquitiesQuoteUpdated(securityData, dataCache, quote);
                }
                updateGreeks(securityData, dataCache);
            });
        }
    }

    /**
     * Recalculates Greeks for every cached contract when the risk-free rate changes.
     */
    private void updateGreeksFromTopLevelSupplemental(String key, Double datum, DataCache dataCache) {
        if (RISK_FREE_INTEREST_RATE_KEY_NAME.equals(key)) {
            for (SecurityData securityData : dataCache.getAllSecurityData().values()) {
                for (OptionsContractData optionsContractData : securityData.getAllOptionsContractData().values()) {
                    updateGreeks(optionsContractData, dataCache, securityData);
                }
            }
        }
    }

    /**
     * Recalculates Greeks for every contract under a security when its dividend yield changes.
     */
    private void updateGreeksFromSecuritySupplemental(String key, Double datum, SecurityData securityData, DataCache dataCache) {
        if (DIVIDEND_YIELD_KEY_NAME.equals(key) && securityData != null) {
            for (OptionsContractData optionsContractData : securityData.getAllOptionsContractData().values()) {
                updateGreeks(optionsContractData, dataCache, securityData);
            }
        }
    }

    /**
     * Recalculates Greeks for every contract under the given security.
     */
    private void updateGreeks(SecurityData securityData, DataCache dataCache) {
        if (securityData == null) {
            return;
        }
        for (OptionsContractData optionsContractData : securityData.getAllOptionsContractData().values()) {
            updateGreeks(optionsContractData, dataCache, securityData);
        }
    }

    /**
     * Invokes every registered {@link CalculateNewGreek} strategy for a single contract.
     * <p>
     * No locking: calculators read volatile/concurrent cache state and may observe torn snapshots.
     * </p>
     */
    private void updateGreeks(OptionsContractData optionsContractData, DataCache dataCache, SecurityData securityData) {
        if (optionsContractData == null || securityData == null || dataCache == null) {
            return;
        }
        for (CalculateNewGreek calculateNewGreek : calcLookup.values()) {
            try {
                calculateNewGreek.calculateNewGreek(optionsContractData, securityData, dataCache);
            } catch (Exception e) {
                log("Error in CalculateNewGreek: " + e.getMessage());
            }
        }
    }

    /**
     * Built-in Black–Scholes path for quote-driven feeds (OPRA, etc.).
     * Uses equity last trade, option quote mid, ask, and bid.
     */
    private void blackScholesCalc(OptionsContractData optionsContractData, SecurityData securityData, DataCache dataCache) {
        Double riskFreeInterestRate = dataCache.getSupplementaryDatum(RISK_FREE_INTEREST_RATE_KEY_NAME);
        Double dividendYield = securityData.getSupplementaryDatum(DIVIDEND_YIELD_KEY_NAME);
        intrinio.realtime.equities.Trade equitiesTrade = securityData.getLatestEquitiesTrade();
        intrinio.realtime.options.Quote optionsQuote = optionsContractData.getLatestQuote();

        if (riskFreeInterestRate == null
                || dividendYield == null
                || equitiesTrade == null
                || optionsQuote == null
                || optionsQuote.askPrice() <= 0D
                || optionsQuote.bidPrice() <= 0D) {
            return;
        }

        double mid = (optionsQuote.askPrice() + optionsQuote.bidPrice()) / 2.0D;
        Greek result = BlackScholesGreekCalculator.calculate(
                riskFreeInterestRate,
                dividendYield,
                equitiesTrade.price(),
                optionsQuote.timestamp(),
                mid,
                optionsQuote.askPrice(),
                optionsQuote.bidPrice(),
                optionsQuote.isPut(),
                optionsQuote.getStrikePrice(),
                optionsQuote.getExpirationDate());

        if (result.isValid()) {
            dataCache.setOptionGreekData(
                    securityData.getTickerSymbol(),
                    optionsContractData.getContract(),
                    BLACK_SCHOLES_KEY_NAME,
                    result,
                    updateFuncGreek);
        }
    }

    /**
     * Built-in Black–Scholes path for trade-driven feeds (Options Edge).
     * Uses equity last trade and option last trade price as market price.
     */
    private void blackScholesCalcOptionsEdge(OptionsContractData optionsContractData, SecurityData securityData, DataCache dataCache) {
        Double riskFreeInterestRate = dataCache.getSupplementaryDatum(RISK_FREE_INTEREST_RATE_KEY_NAME);
        Double dividendYield = securityData.getSupplementaryDatum(DIVIDEND_YIELD_KEY_NAME);
        intrinio.realtime.equities.Trade equitiesTrade = securityData.getLatestEquitiesTrade();
        intrinio.realtime.options.Trade optionsTrade = optionsContractData.getLatestTrade();

        if (riskFreeInterestRate == null
                || dividendYield == null
                || equitiesTrade == null
                || equitiesTrade.price() <= 0.0D
                || optionsTrade == null
                || optionsTrade.price() <= 0D) {
            return;
        }

        Greek result = BlackScholesGreekCalculator.calculate(
                riskFreeInterestRate,
                dividendYield,
                equitiesTrade.price(),
                optionsTrade.timestamp(),
                optionsTrade.price(),
                optionsTrade.price(),
                optionsTrade.price(),
                optionsTrade.isPut(),
                optionsTrade.getStrikePrice(),
                optionsTrade.getExpirationDate());

        if (result.isValid()) {
            dataCache.setOptionGreekData(
                    securityData.getTickerSymbol(),
                    optionsContractData.getContract(),
                    BLACK_SCHOLES_KEY_NAME,
                    result,
                    updateFuncGreek);
        }
    }

    //endregion Private Greek Updates
}
