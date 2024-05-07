package intrinio.realtime.composite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class GreekClient
{
    //region Data Members
    private OnGreek onGreek = (Greek greek) -> {};
    private boolean notifyOnEquityTrade;
    private boolean notifyOnOptionTrade;
    private boolean notifyOnOptionQuote;
    private GreekCalculationMethod greekCalculationMethod = GreekCalculationMethod.BLACK_SCHOLES;
    private GreekCalculator greekCalculator;
    private String apiKey = "";
    private volatile double riskFreeInterestRate = 0.0d;
    private RefreshPeriod riskFreeInterestRateRefreshPeriod = RefreshPeriod.NEVER;
    private RefreshPeriod dividendYieldRefreshPeriod = RefreshPeriod.NEVER;
    private Timer apiFetchTimer;
    private TimerTask riskFreeInterestRateTimerTask;
    private volatile boolean isStopped = true;
    private final String riskFreeInterestRateUrlFormat = "https://api-v2.intrinio.com/...?api_key=%s";
    private final String dividendYieldUrlFormat = "https://api-v2.intrinio.com/...?api_key=%s";
    private final ReentrantLock dividendYieldTimerTasksLock;
    private final HashMap<String, TimerTask> dividendYieldTimerTasks;
    private final ReentrantLock dataLock;
    private final ConcurrentHashMap<String, GreekCalculationData> data;
    private int numEquityTradeProcessingThreads;
    private int numOptionTradeProcessingThreads;
    private int numOptionQuoteProcessingThreads;
    private Thread[] processEquityTradeThreads;
    private Thread[] processOptionTradeThreads;
    private Thread[] processOptionQuoteThreads;
    private ConcurrentLinkedQueue<intrinio.realtime.equities.Trade> equitiesTradeQueue;
    private ConcurrentLinkedQueue<intrinio.realtime.options.Trade> optionsTradeQueue;
    private ConcurrentLinkedQueue<intrinio.realtime.options.Quote> optionsQuoteQueue;
    //endregion Data Members

    //region Constructors
    private GreekClient(){
        dividendYieldTimerTasksLock = new ReentrantLock();
        dataLock = new ReentrantLock();
        this.dividendYieldTimerTasks = new HashMap<String, TimerTask>();
        this.data = new ConcurrentHashMap<String, GreekCalculationData>();
    }

    public GreekClient(OnGreek onGreek,
                       GreekCalculationMethod greekCalculationMethod,
                       String apiKey,
                       Double initialRiskFreeInterestRate,
                       RefreshPeriod riskFreeInterestRateRefreshPeriod,
                       RefreshPeriod dividendYieldRefreshPeriod,
                       boolean notifyOnEquityTrade,
                       boolean notifyOnOptionTrade,
                       boolean notifyOnOptionQuote,
                       int numEquityTradeProcessingThreads,
                       int numOptionTradeProcessingThreads,
                       int numOptionQuoteProcessingThreads){
        this();
        this.onGreek = onGreek;
        this.greekCalculationMethod = greekCalculationMethod;
        this.apiKey = apiKey;
        this.notifyOnEquityTrade = notifyOnEquityTrade;
        this.notifyOnOptionTrade = notifyOnOptionTrade;
        this.notifyOnOptionQuote = notifyOnOptionQuote;
        GreekCalculatorFactory calculatorFactory = new GreekCalculatorFactory();
        greekCalculator = calculatorFactory.GetGreekCalculator(this.greekCalculationMethod);
        if (initialRiskFreeInterestRate != null && !Double.isNaN(initialRiskFreeInterestRate) && !Double.isInfinite(initialRiskFreeInterestRate))
            this.riskFreeInterestRate = initialRiskFreeInterestRate;
        this.riskFreeInterestRateRefreshPeriod = riskFreeInterestRateRefreshPeriod;
        this.apiFetchTimer = new Timer();
        this.riskFreeInterestRateTimerTask = CreateRiskFreeInterestRateFetcherTimerTask();
        this.numEquityTradeProcessingThreads = numEquityTradeProcessingThreads;
        this.numOptionTradeProcessingThreads = numOptionTradeProcessingThreads;
        this.numOptionQuoteProcessingThreads = numOptionQuoteProcessingThreads;
        this.processEquityTradeThreads = new Thread[this.numEquityTradeProcessingThreads];
        this.processOptionTradeThreads = new Thread[this.numOptionTradeProcessingThreads];
        this.processOptionQuoteThreads = new Thread[this.numOptionQuoteProcessingThreads];
        equitiesTradeQueue = new ConcurrentLinkedQueue<intrinio.realtime.equities.Trade>();
        optionsTradeQueue = new ConcurrentLinkedQueue<intrinio.realtime.options.Trade>();
        optionsQuoteQueue = new ConcurrentLinkedQueue<intrinio.realtime.options.Quote>();
    }
    //endregion Constructors

    //region Public Methods
    public void Start(){
        if (isStopped){
            if (apiFetchTimer == null){
                apiFetchTimer = new Timer();
            }
            SafeCancelTimerTask(riskFreeInterestRateTimerTask);
            riskFreeInterestRateTimerTask = null;
            if (riskFreeInterestRateRefreshPeriod != RefreshPeriod.NEVER){
                riskFreeInterestRateTimerTask = CreateRiskFreeInterestRateFetcherTimerTask();
                apiFetchTimer.schedule(riskFreeInterestRateTimerTask, 0L, GetRefreshMilliseconds(riskFreeInterestRateRefreshPeriod));
            }
        }
        isStopped = false;
        startThreads();
    }

    public void Stop(){
        isStopped = true;
        try{
            if (this.apiFetchTimer != null)
            {
                if (this.riskFreeInterestRateTimerTask != null){
                    this.riskFreeInterestRateTimerTask.cancel();
                }
                clearDividendYieldTimerTasks();
                this.apiFetchTimer.purge();
                this.apiFetchTimer.cancel();
            }
            this.riskFreeInterestRateTimerTask = null;
            this.apiFetchTimer = null;
            stopThreads();
            data.clear();
        }
        catch (Exception e){
            Log("Error while stopping risk free interest rate Timer " + e.getMessage());
        }
    }

    public void OnEquitiesTrade(intrinio.realtime.equities.Trade trade){
        equitiesTradeQueue.offer(trade);
    }

    public void OnOptionsTrade(intrinio.realtime.options.Trade trade){
        optionsTradeQueue.offer(trade);
    }

    public void OnOptionsQuote(intrinio.realtime.options.Quote quote){
        optionsQuoteQueue.offer(quote);
    }

    public Greek GetGreek(String ticker, String contract){
        GreekCalculationData calcData = getGreekCalculationData(ticker);
        calcData.setRiskFreeInterestRate(riskFreeInterestRate);
        Greek greek = greekCalculator.Calculate(calcData, contract);
        return greek;
    }
    //endregion Public Methods

    //region Private Methods
    private static void Log(String message) {
        System.out.println(message);
    }

    private void addDividendYieldTimerTask(String ticker){
        if ((dividendYieldRefreshPeriod != RefreshPeriod.NEVER) && (!dividendYieldTimerTasks.containsKey(ticker))){
            dividendYieldTimerTasksLock.lock();
            try
            {
                if (!dividendYieldTimerTasks.containsKey(ticker)){
                    TimerTask newTask = CreateDividendYieldFetcherTimerTask(ticker);
                    if ((newTask != null) && apiFetchTimer != null){
                        apiFetchTimer.schedule(newTask, 0L, GetRefreshMilliseconds(dividendYieldRefreshPeriod));
                        dividendYieldTimerTasks.put(ticker, newTask);
                    }
                }
            }
            finally{
                dividendYieldTimerTasksLock.unlock();
            }
        }
    }

    private void SafeCancelTimerTask(TimerTask timerTask){
        if (timerTask != null){
            timerTask.cancel();
        }
    }

    private void clearDividendYieldTimerTasks(){
        dividendYieldTimerTasksLock.lock();
        try
        {
            for(String ticker : dividendYieldTimerTasks.keySet()){
                try{
                    TimerTask timerTask = dividendYieldTimerTasks.get(ticker);
                    timerTask.cancel();
                    dividendYieldTimerTasks.remove(ticker);
                }
                catch (Exception e){
                    Log("Failed to stop dividend yield fetch task for ticker " + ticker);
                }
            }
            dividendYieldTimerTasks.clear();
        }
        finally{
            dividendYieldTimerTasksLock.unlock();
        }
    }

    private long GetRefreshMilliseconds(RefreshPeriod refreshPeriod){
        return switch (refreshPeriod)
        {
            case NEVER -> Long.MAX_VALUE;
            case ONE_MINUTES -> 60_000L;
            case FIVE_MINUTES -> 300_000L;
            case FIFTEEN_MINUTES -> 900_000L;
            case THIRTY_MINUTES -> 1_800_000L;
            case SIXTY_MINUTES -> 3_600_000L;
        };
    }

    private double getRiskFreeInterestRate() throws IOException {
        Log("Refreshing risk free interest rate...");
        String apiUrl = String.format(this.riskFreeInterestRateUrlFormat, this.apiKey);
        URL url = new URL(apiUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        int status = con.getResponseCode();
        if (status == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            String newRiskFreeInterestRateResult = reader.readLine();
            double rate = Double.parseDouble(newRiskFreeInterestRateResult);
            Log("Refreshing risk free interest rate complete.");
            return rate;
        }
        throw new IOException("Bad status code: " + status);
    }

    private double getDividendYield(String ticker) throws IOException {
        Log(String.format("Refreshing dividend yield for %s...", ticker));
        String apiUrl = String.format(this.dividendYieldUrlFormat, this.apiKey);
        URL url = new URL(apiUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        int status = con.getResponseCode();
        if (status == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            String newRiskFreeInterestRateResult = reader.readLine();
            double rate = Double.parseDouble(newRiskFreeInterestRateResult);
            Log("Refreshing dividend yield for %s complete.");
            return rate;
        }
        throw new IOException("Bad status code: " + status);
    }

    private GreekCalculationData getGreekCalculationData(String ticker){
        if (data.containsKey(ticker))
            return data.get(ticker);
        dataLock.lock();
        try{
            if (data.containsKey(ticker))
                return data.get(ticker);
            else{
                GreekCalculationData newData = new GreekCalculationData(null, -1.0F, riskFreeInterestRate);
                data.put(ticker, newData);
                return newData;
            }
        }finally{
            dataLock.unlock();
        }
    }

    private TimerTask CreateRiskFreeInterestRateFetcherTimerTask(){
        if (this.riskFreeInterestRateRefreshPeriod == RefreshPeriod.NEVER)
            return null;
        return new TimerTask() {
            public void run() {
                try
                {
                    riskFreeInterestRate = getRiskFreeInterestRate();
                }
                catch (Exception ex){
                    Log("Error fetching risk free interest rate: " + ex.getMessage());
                }
            }
        };
    }

    private TimerTask CreateDividendYieldFetcherTimerTask(String ticker){
        if (this.dividendYieldRefreshPeriod == RefreshPeriod.NEVER)
            return null;
        return new TimerTask() {
            public void run() {
                try
                {
                    double dividendYield = getDividendYield(ticker);
                    GreekCalculationData data = getGreekCalculationData(ticker);
                    data.setDividendYield(dividendYield);
                }
                catch (Exception ex){
                    Log(String.format("Error fetching dividend yield rate for ticker %s: %s", ticker, ex.getMessage()));
                }
            }
        };
    }

    private void FireGreek(GreekCalculationData calcData, String contract){
        Greek greek = greekCalculator.Calculate(calcData, contract);
        try{
            if (greek != null){
                onGreek.onGreek(greek);
            }
        }catch (Exception ex){
            Log("Error in onGreek Callback: " + ex.getMessage());
        }
    }

    private void processEquitiesTradeQueue(){
        while (!isStopped){
            try{
                intrinio.realtime.equities.Trade trade = equitiesTradeQueue.poll();
                if (trade != null){
                    String symbol = trade.symbol();
                    addDividendYieldTimerTask(symbol);
                    GreekCalculationData calcData = getGreekCalculationData(symbol);
                    calcData.setUnderlyingTrade(trade);
                    calcData.setRiskFreeInterestRate(riskFreeInterestRate);
                    if (notifyOnEquityTrade){
                        for(OptionsContractData contractData : calcData.getOptionsContracts()){
                            FireGreek(calcData, contractData.getContract());
                        }
                    }
                }
                else
                    Thread.sleep(50);
            }catch (Exception e){
                Log("GreekClient: Error processing equities trade. " + e.getMessage());
            }
        }
    }

    private void processOptionsTradeQueue(){
        while (!isStopped){
            try{
                intrinio.realtime.options.Trade trade = optionsTradeQueue.poll();
                if (trade != null){
                    String symbol = trade.getUnderlyingSymbol();
                    addDividendYieldTimerTask(symbol);
                    GreekCalculationData calcData = getGreekCalculationData(symbol);
                    calcData.setOptionsTrade(trade);
                    calcData.setRiskFreeInterestRate(riskFreeInterestRate);
                    if (notifyOnOptionTrade){
                        FireGreek(calcData, trade.contract());
                    }
                }
                else
                    Thread.sleep(50);
            }catch (Exception e){
                Log("GreekClient: Error processing options trade. " + e.getMessage());
            }
        }
    }

    private void processOptionsQuoteQueue(){
        while (!isStopped){
            try{
                intrinio.realtime.options.Quote quote = optionsQuoteQueue.poll();
                if (quote != null){
                    String symbol = quote.getUnderlyingSymbol();
                    addDividendYieldTimerTask(symbol);
                    GreekCalculationData calcData = getGreekCalculationData(symbol);
                    calcData.setOptionsQuote(quote);
                    calcData.setRiskFreeInterestRate(riskFreeInterestRate);
                    if (notifyOnOptionQuote){
                        FireGreek(calcData, quote.contract());
                    }
                }
                else
                    Thread.sleep(50);
            }catch (Exception e){
                Log("GreekClient: Error processing options quote. " + e.getMessage());
            }
        }
    }

    private void startThreads() {
        try{
            for (int i = 0; i < processEquityTradeThreads.length; i++) {
                processEquityTradeThreads[i] = new Thread(this::processEquitiesTradeQueue);
            }
            for (Thread thread : processEquityTradeThreads) {
                thread.start();
            }

            for (int i = 0; i < processOptionTradeThreads.length; i++) {
                processOptionTradeThreads[i] = new Thread(this::processOptionsTradeQueue);
            }
            for (Thread thread : processOptionTradeThreads) {
                thread.start();
            }

            for (int i = 0; i < processOptionQuoteThreads.length; i++) {
                processOptionQuoteThreads[i] = new Thread(this::processOptionsQuoteQueue);
            }
            for (Thread thread : processOptionQuoteThreads) {
                thread.start();
            }
        }catch (Exception e){
            Log("Error starting GreekClient worker threads: " + e.getMessage());
        }
    }

    private void stopThreads(){
        try{
            Thread.sleep(1000);

            for (Thread thread : processEquityTradeThreads) {
                thread.join();
            }

            for (Thread thread : processOptionTradeThreads) {
                thread.join();
            }

            for (Thread thread : processOptionQuoteThreads) {
                thread.join();
            }
        }catch (Exception e){
            Log("Error stopping GreekClient worker threads: " + e.getMessage());
        }
    }
    //endregion Private Methods
}