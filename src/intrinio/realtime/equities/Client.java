package intrinio.realtime.equities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Client implements WebSocket.Listener {
	//region Final Data Members
	private static final String FirehoseChannelName = "lobby";
	private static final int QuoteQueueCapacity = 1_000_000;
	private static final long DefaultConnectTimeoutMillis = 30_000L;
	private static final long CloseTimeoutMillis = 5_000L;
	private static final long ThreadJoinTimeoutMillis = 10_000L;
	private long[] selfHealBackoffs = {1000, 30000, 60000, 300000, 600000};
	private long connectTimeoutMillis = DefaultConnectTimeoutMillis;
	private final ReentrantReadWriteLock wsLock = new ReentrantReadWriteLock();
	private final PriorityMessageQueue data = new PriorityMessageQueue(2, 1, QuoteQueueCapacity);
	private final HashSet<Channel> channels = new HashSet<Channel>();
	private final Object channelsLock = new Object();
	private final Object lifecycleLock = new Object();
	private final WebSocketState wsState = new WebSocketState();
	private final LinkedBlockingDeque<Tuple<byte[], Boolean>> dataBucket = new LinkedBlockingDeque<Tuple<byte[], Boolean>>();
	private final Lock dataBucketLock = new ReentrantLock();
	private final AtomicBoolean reconnectWorkerRunning = new AtomicBoolean(false);
	private final AtomicLong connectionGeneration = new AtomicLong(0L);
	private final Set<WebSocket> failedWebSockets = Collections.newSetFromMap(new ConcurrentHashMap<WebSocket, Boolean>());
	//endregion Final Data Members

	//region Data Members
	private Config config;
	private final AtomicReference<Token> token = new AtomicReference<Token>(new Token(null, LocalDateTime.now()));
	private final AtomicLong dataMsgCount = new AtomicLong(0L);
	private final AtomicLong textMsgCount = new AtomicLong(0L);
	private OnTrade onTrade = (Trade trade) -> {};
	private OnQuote onQuote = (Quote quote) -> {};
	private Thread[] processDataThreads;
	private volatile boolean isCancellationRequested = true;
	private volatile Lifecycle lifecycle = Lifecycle.STOPPED;
	private volatile Thread reconnectThread;
	private volatile Thread connectingThread;
	private volatile CompletableFuture<WebSocket> connectFuture;
	private volatile HttpURLConnection activeAuthConnection;
	private WebSocketConnector webSocketConnector = this::openWebSocket;
	private static final String HeaderClientInformationKey = "Client-Information";
	private static final String HeaderClientInformationValue = "IntrinioRealtimeJavaSDKv9.0";
	private static final String HeaderMessageVersionKey = "UseNewEquitiesFormat";
	private static final String HeaderMessageVersionValue = "v2";

	private enum Lifecycle { STOPPED, STARTING, STARTED, STOPPING }

	@FunctionalInterface
	interface WebSocketConnector {
		CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener);
	}
	//endregion Data Members

	//region Constructors
	public Client() {
		try {
			config = Config.load();
			processDataThreads = new Thread[config.getEquitiesNumThreads()];
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());
		}
	}

	public Client(Config config) {
		try {
			this.config = config;
			processDataThreads = new Thread[config.getEquitiesNumThreads()];
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());
		}
	}

	Client(Config config, WebSocketConnector webSocketConnector, long[] selfHealBackoffs, long connectTimeoutMillis) {
		this(config);
		this.webSocketConnector = webSocketConnector;
		this.selfHealBackoffs = Arrays.copyOf(selfHealBackoffs, selfHealBackoffs.length);
		this.connectTimeoutMillis = connectTimeoutMillis;
	}

	public Client(OnTrade onTrade){
		this();
		this.onTrade = onTrade;
	}

	public Client(OnTrade onTrade, OnQuote onQuote){
		this();
		this.onTrade = onTrade;
		this.onQuote = onQuote;
	}

	public Client(OnTrade onTrade, Config config){
		this(config);
		this.onTrade = onTrade;
	}

	public Client(OnTrade onTrade, OnQuote onQuote, Config config){
		this(config);
		this.onTrade = onTrade;
		this.onQuote = onQuote;
	}

	protected void finalize() {
		try { this.stop(); } catch (Exception e){}
	}
	//endregion Constructors

	//region Public Get Set
	public String getStats() {
		return String.format("Data Messages = %d, Text Messages = %d, Queue Depth = %d, Local queue drop count = %d", this.dataMsgCount.get(), this.textMsgCount.get(), getDataSize(), data.getDroppedCount());
	}
	//endregion Public Get Set

	//region Private Get Set
	private int getDataSize() {
		return data.size();
	}

	private String getAuthUrl() throws Exception {
		String authUrl;
		switch (config.getEquitiesProvider()) {
			case REALTIME: authUrl = "https://realtime-mx.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case IEX: authUrl = "https://realtime-mx.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case DELAYED_SIP: authUrl = "https://realtime-delayed-sip.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case NASDAQ_BASIC: authUrl = "https://realtime-nasdaq-basic.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case CBOE_ONE: authUrl = "https://cboe-one.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case EQUITIES_EDGE: authUrl = "https://equities-edge.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case MANUAL: authUrl = "http://" + config.getEquitiesIpAddress() + "/auth?api_key=" + config.getEquitiesApiKey();
				break;
			default: throw new Exception("Provider not specified!");
		}
		return authUrl;
	}

	private String getWebSocketUrl (String token) throws Exception {
		String wsUrl;
		String delayedPart = config.isEquitiesDelayed() ? "&delayed=true" : "";
		switch (config.getEquitiesProvider()) {
			case REALTIME: wsUrl = "wss://realtime-mx.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
				break;
			case IEX: wsUrl = "wss://realtime-mx.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
				break;
			case DELAYED_SIP: wsUrl = "wss://realtime-delayed-sip.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
				break;
			case NASDAQ_BASIC: wsUrl = "wss://realtime-nasdaq-basic.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
				break;
			case CBOE_ONE: wsUrl = "wss://cboe-one.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
				break;
			case EQUITIES_EDGE: wsUrl = "wss://equities-edge.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
				break;
			case MANUAL: wsUrl = "ws://" + config.getEquitiesIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
				break;
			default: throw new Exception("Provider not specified!");
		}
		return wsUrl;
	}

	private boolean allReady() {
		wsLock.readLock().lock();
		try {
			return wsState.isReady();
		} finally {
			wsLock.readLock().unlock();
		}
	}
	//endregion Private Get Set

	//region Public Methods
	public CompletionStage<Void> onClose(WebSocket ws, int status, String reason) {
		if (!noteFailedWebSocket(ws)) {
			return CompletableFuture.completedFuture(null);
		}
		Client.Log("Websocket - Closed%s", reason == null || reason.isBlank() ? "" : ". " + reason);
		startReconnectWorker();
		return CompletableFuture.completedFuture(null);
	}

	public void onError(WebSocket ws, Throwable err) {
		Client.Log("Websocket - Error - %s", err == null ? "Unknown error" : err.getMessage());
		if (!noteFailedWebSocket(ws)) return;
		abortWebSocket(ws);
		startReconnectWorker();
	}

	public CompletionStage<Void> onText(WebSocket ws, CharSequence data, boolean isComplete) {
		if (!isCurrentWebSocket(ws)) {
			return CompletableFuture.completedFuture(null);
		}
		textMsgCount.addAndGet(1l);
		if (data != null && data.length() > 0) {
			try {
				Client.Log("Error received: %s", data.toString());
			}
			catch (Exception e) {
				Client.Log("Failure parsing error from server in onText(). " + e.getMessage());
			}
		}
		try {
			ws.request(1);
		} catch (Exception e) {
			onError(ws, e);
		}
		return CompletableFuture.completedFuture(null);
	}

	public CompletionStage<Void> onBinary(WebSocket ws, ByteBuffer data, boolean isComplete) {
		if (!isCurrentWebSocket(ws)) {
			return CompletableFuture.completedFuture(null);
		}
		dataMsgCount.addAndGet(1);
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);
		this.dataBucket.add(new Tuple<byte[], Boolean>(bytes, isComplete));
		if (isComplete) {
			enqueueCompleteDataMessage(assembleCompleteDataMessage());
		}
		try {
			ws.request(1);
		} catch (Exception e) {
			onError(ws, e);
		}
		return CompletableFuture.completedFuture(null);
	}

	public static void Log(String message) {
		System.out.println(message);
	}

	public static void Log(String message, Object... args) {
		System.out.printf(message + "%n", args);
	}

	public void join() {
		while (!this.allReady()) {
			if (!waitForReady()) return;
		}
		String[] symbols = config.getEquitiesSymbols();
		boolean tradesOnly = config.isEquitiesTradesOnly();
		for (String symbol : symbols) {
			this._join(symbol, tradesOnly);
		}
	}

	public void join(String symbol, boolean tradesOnly) {
		boolean t = tradesOnly || config.isEquitiesTradesOnly();
		while (!this.allReady()) {
			if (!waitForReady()) return;
		}
		this._join(symbol, t);
	}

	public void join(String symbol) {
		this.join(symbol, false);
	}

	public void join(String[] symbols, boolean tradesOnly) {
		boolean t = tradesOnly || config.isEquitiesTradesOnly();
		while (!this.allReady()) {
			if (!waitForReady()) return;
		}
		for (String symbol : symbols) {
			this._join(symbol, t);
		}
	}

	public void join(String[] symbols) {
		this.join(symbols, false);
	}

	public void joinLobby(){
		joinLobby(false);
	}

	public void joinLobby(boolean tradesOnly){
		boolean t = tradesOnly || config.isEquitiesTradesOnly();
		while (!this.allReady()) {
			if (!waitForReady()) return;
		}
		this._join(FirehoseChannelName, t);
	}

	public void leave() {
		for (Channel channel : snapshotChannels()) {
			this._leave(channel.symbol(), channel.tradesOnly());
		}
	}

	public void leave(String symbol) {
		for (Channel channel : snapshotChannels()) {
			if (channel.symbol().equals(symbol)) {
				this._leave(symbol, channel.tradesOnly());
			}
		}
	}

	public void leaveLobby(){
		for (Channel channel : snapshotChannels()) {
			if (channel.symbol().equals(FirehoseChannelName)) {
				this._leave(FirehoseChannelName, channel.tradesOnly());
			}
		}
	}

	public void leave(String[] symbols) {
		for (String symbol : symbols) {
			for (Channel channel : snapshotChannels()) {
				if (channel.symbol().equals(symbol)) {
					this._leave(symbol, channel.tradesOnly());
				}
			}
		}
	}

	public void start() throws Exception{
		long generation;
		synchronized (lifecycleLock) {
			while (lifecycle == Lifecycle.STOPPING) {
				lifecycleLock.wait();
			}
			if (lifecycle == Lifecycle.STARTED) return;
			if (lifecycle == Lifecycle.STARTING) {
				while (lifecycle == Lifecycle.STARTING) lifecycleLock.wait();
				if (lifecycle == Lifecycle.STARTED) return;
			}
			lifecycle = Lifecycle.STARTING;
			isCancellationRequested = false;
			generation = connectionGeneration.incrementAndGet();
			clearSessionData();
			startThreads();
			connectingThread = Thread.currentThread();
		}

		boolean connected = false;
		try {
			connectWithRetry(generation);
			connected = isGenerationActive(generation) && allReady();
			if (!connected) throw new InterruptedException("Client start was cancelled");
			synchronized (lifecycleLock) {
				if (lifecycle == Lifecycle.STARTING) lifecycle = Lifecycle.STARTED;
				lifecycleLock.notifyAll();
			}
		} finally {
			connectingThread = null;
			if (!connected) {
				synchronized (lifecycleLock) {
					if (lifecycle == Lifecycle.STARTING) {
						lifecycle = Lifecycle.STOPPED;
						isCancellationRequested = true;
						connectionGeneration.incrementAndGet();
					}
					lifecycleLock.notifyAll();
				}
				stopThreads();
			}
		}
	}

	public void stop() {
		synchronized (lifecycleLock) {
			if (lifecycle == Lifecycle.STOPPED) return;
			if (lifecycle == Lifecycle.STOPPING) {
				while (lifecycle == Lifecycle.STOPPING) {
					try { lifecycleLock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
				}
				return;
			}
			lifecycle = Lifecycle.STOPPING;
			isCancellationRequested = true;
			connectionGeneration.incrementAndGet();
		}

		CompletableFuture<WebSocket> pendingConnect = connectFuture;
		if (pendingConnect != null) pendingConnect.cancel(true);
		HttpURLConnection pendingAuth = activeAuthConnection;
		if (pendingAuth != null) pendingAuth.disconnect();
		Thread pendingConnectingThread = connectingThread;
		if (pendingConnectingThread != null && pendingConnectingThread != Thread.currentThread()) pendingConnectingThread.interrupt();
		Thread pendingReconnectThread = reconnectThread;
		if (pendingReconnectThread != null && pendingReconnectThread != Thread.currentThread()) pendingReconnectThread.interrupt();

		WebSocket ws;
		wsLock.writeLock().lock();
		try {
			wsState.setReady(false);
			wsState.setReconnecting(false);
			ws = wsState.getWebSocket();
		} finally {
			wsLock.writeLock().unlock();
		}
		Client.Log("Websocket - Closing");
		closeWebSocket(ws);
		joinThread(pendingConnectingThread);
		joinThread(pendingReconnectThread);
		stopThreads();
		clearSessionData();
		failedWebSockets.clear();
		reconnectWorkerRunning.set(false);
		synchronized (lifecycleLock) {
			lifecycle = Lifecycle.STOPPED;
			lifecycleLock.notifyAll();
		}
		Client.Log("Stopped");
	}
	//endregion Public Methods

	//region Private Methods
	private void processData(){
		while (!this.isCancellationRequested) {
			try {
				byte[] message = data.take();
				ByteBuffer buffer = ByteBuffer.wrap(message);
				switch (message[0]) {
					case 0:
						onTrade.onTrade(Trade.parse(buffer));
						break;
					case 1:
					case 2:
						onQuote.onQuote(Quote.parse(buffer));
						break;
					default:
						Client.Log("Error parsing message. Type is %d", message[0]);
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception ex) {
				Client.Log("General Exception: %s", ex.getMessage());
			}
		}
	}

	private void startThreads() {
		this.isCancellationRequested = false;
		for (int i = 0; i < processDataThreads.length; i++) {
			processDataThreads[i] = new Thread(this::processData, "intrinio-equities-worker-" + i);
			processDataThreads[i].setDaemon(true);
		}
		for (Thread thread : processDataThreads) {
			thread.start();
		}
	}

	private void stopThreads(){
		this.isCancellationRequested = true;
		for (Thread thread : processDataThreads) {
			if (thread != null) thread.interrupt();
		}
		for (Thread thread : processDataThreads) {
			joinThread(thread);
		}
	}

	private void _join(String symbol, boolean tradesOnly) {
		_join(symbol, tradesOnly, false);
	}

	private void _join(String symbol, boolean tradesOnly, boolean forceRejoin){
		Channel channel = new Channel(symbol, tradesOnly);
		boolean shouldSend;
		synchronized (channelsLock) {
			shouldSend = channels.add(channel) || (channels.contains(channel) && forceRejoin);
		}
		if (shouldSend) {
			byte[] message = makeJoinMessage(tradesOnly, symbol);
			Client.Log("Websocket - Joining channel: %s (trades only = %s)", symbol, Boolean.toString(tradesOnly));
			try {
				sendBinary(getCurrentWebSocket(), message);
			} catch (Exception e) {
				Client.Log("Websocket - Warning while joining channel: %s", e.getMessage());
				markDisconnectedAndReconnect(getCurrentWebSocket());
			}
		}
	}

	private void _leave(String symbol, boolean tradesOnly) {
		Channel channel = new Channel(symbol, tradesOnly);
		boolean removed;
		synchronized (channelsLock) {
			removed = channels.remove(channel);
		}
		if (removed) {
			byte[] message = makeLeaveMessage(symbol);
			Client.Log("Websocket - Leaving channel: %s (trades only = %s)", symbol, Boolean.toString(tradesOnly));
			try {
				sendBinary(getCurrentWebSocket(), message);
			} catch (Exception e) {
				Client.Log("Websocket - Warning while leaving channel: %s", e.getMessage());
			}
		}
	}

	private void onWebSocketConnected(WebSocket ws) throws Exception {
		for (Channel channel : snapshotChannels()) {
			Client.Log("Websocket - Rejoining channel: %s (trades only = %s)", channel.symbol(), Boolean.toString(channel.tradesOnly()));
			sendBinary(ws, makeJoinMessage(channel.tradesOnly(), channel.symbol()));
		}
	}

	private CompletableFuture<WebSocket> openWebSocket(URI uri, WebSocket.Listener listener) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMillis))
			.build();
		return httpClient.newWebSocketBuilder()
			.header(HeaderMessageVersionKey, HeaderMessageVersionValue)
			.header(HeaderClientInformationKey, HeaderClientInformationValue)
			.buildAsync(uri, listener);
	}

	private void connectWithRetry(long generation) throws Exception {
		int backoffIndex = 0;
		while (isGenerationActive(generation)) {
			try {
				Client.Log("Websocket - Reconnecting...");
				if (tryGetNewToken() && connectOnce(token.get().token(), generation)) return;
			} catch (InterruptedException | CancellationException e) {
				throw e;
			} catch (Exception e) {
				Client.Log("Websocket - Connection attempt failed: %s", rootMessage(e));
			}
			if (!isGenerationActive(generation)) break;
			long backoff = selfHealBackoffs[Math.min(backoffIndex, selfHealBackoffs.length - 1)];
			backoffIndex = Math.min(backoffIndex + 1, selfHealBackoffs.length - 1);
			Thread.sleep(backoff);
		}
		throw new InterruptedException("Connection attempt cancelled");
	}

	private boolean connectOnce(String authToken, long generation) throws Exception {
		URI uri = new URI(getWebSocketUrl(authToken));
		Client.Log("Websocket - Connecting...");
		CompletableFuture<WebSocket> task = webSocketConnector.connect(uri, this);
		connectFuture = task;
		task.whenComplete((created, error) -> {
			if (created != null && !isGenerationActive(generation)) abortWebSocket(created);
		});
		WebSocket created = null;
		try {
			created = task.orTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS).get();
			if (!isGenerationActive(generation)) throw new CancellationException("Connection cancelled");

			WebSocket previous;
			wsLock.writeLock().lock();
			try {
				if (!isGenerationActive(generation)) throw new CancellationException("Connection cancelled");
				previous = wsState.getWebSocket();
				wsState.setWebSocket(created);
				wsState.setReady(false);
				wsState.setReconnecting(true);
				if (previous != null && previous != created) failedWebSockets.remove(previous);
			} finally {
				wsLock.writeLock().unlock();
			}
			if (previous != null && previous != created) abortWebSocket(previous);

			onWebSocketConnected(created);
			if (failedWebSockets.contains(created) || created.isInputClosed() || created.isOutputClosed()) {
				throw new IOException("Websocket closed during connection setup");
			}

			wsLock.writeLock().lock();
			try {
				if (!isGenerationActive(generation) || wsState.getWebSocket() != created || failedWebSockets.contains(created)) {
					throw new CancellationException("Connection was superseded");
				}
				wsState.setReady(true);
				wsState.setReconnecting(false);
			} finally {
				wsLock.writeLock().unlock();
			}
			Client.Log("Websocket - Connected");
			return true;
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof TimeoutException) throw new TimeoutException("Websocket connect timed out after " + connectTimeoutMillis + "ms");
			if (cause instanceof Exception) throw (Exception) cause;
			throw e;
		} finally {
			if (connectFuture == task) connectFuture = null;
			if (created != null && (!allReady() || !isCurrentWebSocket(created))) {
				abortWebSocket(created);
				failedWebSockets.remove(created);
			}
		}
	}

	private boolean tryGetNewToken(){
		Client.Log("Authorizing...");
		String authUrl = null;
		try {
			authUrl = this.getAuthUrl();
		} catch (Exception e) {
			Client.Log("Authorization Failure. " + e.getMessage());
			return false;
		}
		URL url = null;
		try {
			url = new URL(authUrl);
		} catch (MalformedURLException e) {
			Client.Log("Authorization Failure. Bad URL (%s). %s", authUrl, e.getMessage());
			return false;
		}
		HttpURLConnection con;
		try {
			con = (HttpURLConnection) url.openConnection();
			con.setConnectTimeout((int)Math.min(connectTimeoutMillis, Integer.MAX_VALUE));
			con.setReadTimeout((int)Math.min(connectTimeoutMillis, Integer.MAX_VALUE));
			con.setRequestProperty(HeaderClientInformationKey, HeaderClientInformationValue);
			activeAuthConnection = con;
		} catch (IOException e) {
			Client.Log("Authorization Failure. Please check your network connection. " + e.getMessage());
			return false;
		}
		try {
			con.setRequestMethod("GET");
			int status = con.getResponseCode();
			if (status == 200) {
				String newToken;
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
					newToken = reader.readLine();
				}
				this.token.set(new Token(newToken, LocalDateTime.now()));
				Client.Log("Authorization successful");
				return true;
			}
			else
				Client.Log("Authorization Failure (%d). The authorization key you provided is likely incorrect.", status);
			return false;
		} catch (ProtocolException e) {
			Client.Log("Authorization Failure. Bad request type. " + e.getMessage());
			return false;
		} catch (IOException e) {
			Client.Log("Authorization Failure. The authorization server is likely offline. " + e.getMessage());
			return false;
		} catch (Exception e) {
			Client.Log("Authorization Failure. " + e.getMessage());
			return false;
		} finally {
			if (activeAuthConnection == con) activeAuthConnection = null;
			con.disconnect();
		}
	}

	private void enqueueCompleteDataMessage(byte[] datum) {
		if (datum.length == 0) return;
		int count = Byte.toUnsignedInt(datum[0]);
		int offset = 1;
		for (int i = 0; i < count; i++) {
			if (offset + 1 >= datum.length) {
				Client.Log("Error parsing multi-part message: incomplete header");
				return;
			}
			int type = Byte.toUnsignedInt(datum[offset]);
			int messageLength = Byte.toUnsignedInt(datum[offset + 1]);
			if (messageLength < 2 || offset + messageLength > datum.length) {
				Client.Log("Error parsing multi-part message: invalid length %d", messageLength);
				return;
			}
			byte[] message = Arrays.copyOfRange(datum, offset, offset + messageLength);
			if (type == 0) {
				data.offer(0, message);
			} else if (type == 1 || type == 2) {
				data.offer(1, message);
			} else {
				Client.Log("Error parsing multi-part message. Type is %d", type);
				return;
			}
			offset += messageLength;
		}
	}

	private void startReconnectWorker() {
		synchronized (lifecycleLock) {
			if (lifecycle != Lifecycle.STARTED || isCancellationRequested || allReady()) return;
		}
		if (!reconnectWorkerRunning.compareAndSet(false, true)) return;
		long generation = connectionGeneration.get();
		Thread worker = new Thread(() -> {
			try {
				connectWithRetry(generation);
			} catch (InterruptedException | CancellationException ignored) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				Client.Log("Websocket - Reconnect worker failed: %s", rootMessage(e));
			} finally {
				reconnectWorkerRunning.set(false);
				if (isGenerationActive(generation) && !allReady()) startReconnectWorker();
			}
		}, "intrinio-equities-reconnect");
		worker.setDaemon(true);
		reconnectThread = worker;
		worker.start();
	}

	private void markDisconnectedAndReconnect(WebSocket ws) {
		if (!noteFailedWebSocket(ws)) return;
		abortWebSocket(ws);
		startReconnectWorker();
	}

	private boolean noteFailedWebSocket(WebSocket ws) {
		wsLock.writeLock().lock();
		try {
			if (ws == null || wsState.getWebSocket() != ws) return false;
			failedWebSockets.add(ws);
			failedWebSockets.removeIf(existing -> existing != ws);
			wsState.setReady(false);
			wsState.setReconnecting(!isCancellationRequested);
			return true;
		} finally {
			wsLock.writeLock().unlock();
		}
	}

	private boolean isCurrentWebSocket(WebSocket ws) {
		wsLock.readLock().lock();
		try {
			return ws != null && wsState.getWebSocket() == ws;
		} finally {
			wsLock.readLock().unlock();
		}
	}

	private WebSocket getCurrentWebSocket() {
		wsLock.readLock().lock();
		try {
			return wsState.getWebSocket();
		} finally {
			wsLock.readLock().unlock();
		}
	}

	private void sendBinary(WebSocket ws, byte[] message) throws Exception {
		if (ws == null) throw new IOException("Websocket is not connected");
		try {
			ws.sendBinary(ByteBuffer.wrap(message), true)
				.orTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
				.get();
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) throw (Exception) cause;
			throw e;
		}
	}

	private boolean waitForReady() {
		if (isCancellationRequested) return false;
		try {
			Thread.sleep(20L);
			return !isCancellationRequested;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private Channel[] snapshotChannels() {
		synchronized (channelsLock) {
			return channels.toArray(new Channel[0]);
		}
	}

	private void clearSessionData() {
		data.clear();
		dataBucketLock.lock();
		try {
			dataBucket.clear();
		} finally {
			dataBucketLock.unlock();
		}
	}

	private boolean isGenerationActive(long generation) {
		return !isCancellationRequested && connectionGeneration.get() == generation;
	}

	private void closeWebSocket(WebSocket ws) {
		if (ws == null) return;
		try {
			ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client requested close")
				.orTimeout(CloseTimeoutMillis, TimeUnit.MILLISECONDS)
				.get();
		} catch (Exception e) {
			abortWebSocket(ws);
		}
	}

	private void abortWebSocket(WebSocket ws) {
		if (ws == null) return;
		try { ws.abort(); } catch (Exception ignored) {}
	}

	private void joinThread(Thread thread) {
		if (thread == null || thread == Thread.currentThread()) return;
		try {
			thread.join(ThreadJoinTimeoutMillis);
			if (thread.isAlive()) Client.Log("Thread timed out while stopping: %s", thread.getName());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	private byte[] makeJoinMessage(boolean tradesOnly, String symbol) {
		byte[] message, symbolBytes;
		switch (symbol) {
			case "lobby":
				message = new byte[11];
				symbolBytes = "$FIREHOSE".getBytes(StandardCharsets.US_ASCII);
				break;
			default:
				message = new byte[(2 + symbol.length())];
				symbolBytes = symbol.getBytes(StandardCharsets.US_ASCII);
				break;
		}
		message[0] = (byte)74; //type: join (74) or leave (76)
		message[1] = tradesOnly ? (byte)1 : (byte)0;
		System.arraycopy(symbolBytes, 0, message, 2, symbolBytes.length);
		return message;
	}

	private byte[] makeLeaveMessage(String symbol) {
		byte[] message, symbolBytes;
		switch (symbol) {
			case "lobby":
				message = new byte[10];
				symbolBytes = "$FIREHOSE".getBytes(StandardCharsets.US_ASCII);
				break;
			default:
				message = new byte[(1 + symbol.length())];
				symbolBytes = symbol.getBytes(StandardCharsets.US_ASCII);
				break;
		}
		message[0] = (byte)76; //type: join (74) or leave (76)
		System.arraycopy(symbolBytes, 0, message, 1, symbolBytes.length);
		return message;
	}

	private byte[] assembleCompleteDataMessage() {
		Queue<byte[]> parts = new LinkedList<>();
		int length = 0;
		boolean done = false;
		dataBucketLock.lock();
		try {
			while (!done) {
				try {
					Tuple<byte[], Boolean> datum = dataBucket.poll(1, TimeUnit.SECONDS);
					if (datum != null) {
						parts.add(datum.x);
						done = datum.y;
						length += datum.x.length;
					}
				} catch(InterruptedException e) {
					Client.Log("process data interrupted");
				}
			}
		} finally {dataBucketLock.unlock();}

		//reassemble into one byte array
		byte[] bytes = new byte[length];
		int index = 0;
		while (!parts.isEmpty()) {
			byte[] part = parts.remove();
			java.lang.System.arraycopy(part, 0, bytes, index, part.length);
			index += part.length;
		}
		return bytes;
	}

	//endregion Private Methods

	private class Tuple<X, Y> {
		  public final X x; 
		  public final Y y; 
		  public Tuple(X x, Y y) { 
		    this.x = x; 
		    this.y = y; 
		  } 
	}
}
