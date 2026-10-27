package intrinio.realtime.options;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Client implements WebSocket.Listener {
	//region Final data members
	private static final String FIREHOSE_CHANNEL = "$FIREHOSE";
	private static final int TRADE_MESSAGE_SIZE = 72; //61 used + 11 pad
	private static final int QUOTE_MESSAGE_SIZE = 52; //48 used + 4 pad
	private static final int REFRESH_MESSAGE_SIZE = 52; //44 used + 8 pad
	private static final int UNUSUAL_ACTIVITY_MESSAGE_SIZE = 74; //62 used + 12 pad
	private static final int QUOTE_QUEUE_CAPACITY = 1_000_000;
	private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 30_000L;
	private static final long CLOSE_TIMEOUT_MILLIS = 5_000L;
	private static final long THREAD_JOIN_TIMEOUT_MILLIS = 10_000L;
	private long[] selfHealBackoffs = {1000, 30000, 60000, 300000, 600000};
	private long connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
	private final ReentrantReadWriteLock wsLock = new ReentrantReadWriteLock();
	private final PriorityMessageQueue data = new PriorityMessageQueue(4, 3, QUOTE_QUEUE_CAPACITY);
	private final HashSet<String> channels = new HashSet<String>();
	private final Object channelsLock = new Object();
	private final Object lifecycleLock = new Object();
	private final Lock dataBucketLock = new ReentrantLock();
	private final LinkedBlockingDeque<Tuple<byte[], Boolean>> dataBucket = new LinkedBlockingDeque<Tuple<byte[], Boolean>>();
	private final WebSocketState wsState = new WebSocketState();
	private final String Version = "IntrinioRealtimeOptionsJavaSDKv9.0";
	private final AtomicBoolean reconnectWorkerRunning = new AtomicBoolean(false);
	private final AtomicLong connectionGeneration = new AtomicLong(0L);
	private final Set<WebSocket> failedWebSockets = Collections.newSetFromMap(new ConcurrentHashMap<WebSocket, Boolean>());
	//endregion Final data members

	//region Data Members
	private Config config;
	private Thread[] processDataThreads;
	private volatile boolean isCancellationRequested = true;
	private volatile Lifecycle lifecycle = Lifecycle.STOPPED;
	private volatile Thread reconnectThread;
	private volatile Thread connectingThread;
	private volatile CompletableFuture<WebSocket> connectFuture;
	private volatile HttpURLConnection activeAuthConnection;
	private WebSocketConnector webSocketConnector = this::openWebSocket;
	private final AtomicReference<Token> token = new AtomicReference<Token>(new Token(null, LocalDateTime.now()));
	private final AtomicLong dataMsgCount = new AtomicLong(0L);
	private final AtomicLong textMsgCount = new AtomicLong(0L);
	private OnTrade onTrade = (Trade trade) -> {};
	private boolean useOnTrade = false;
	private OnQuote onQuote = (Quote quote) -> {};
	private boolean useOnQuote = false;
	private OnRefresh onRefresh = (Refresh r) -> {};
	private boolean useOnRefresh = false;
	private OnUnusualActivity onUnusualActivity = (UnusualActivity ua) -> {};
	private boolean useOnUnusualActivity = false;

	private enum Lifecycle { STOPPED, STARTING, STARTED, STOPPING }

	@FunctionalInterface
	interface WebSocketConnector {
		CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener);
	}
	//endregion Data Members

	//region Constructors
	public Client() {
		try {
			this.config = Config.load();
			processDataThreads = new Thread[config.getOptionsNumThreads()];
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());
		}
	}

	public Client(Config config) {
		try {
			this.config = config;
			processDataThreads = new Thread[config.getOptionsNumThreads()];
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());;
		}
	}

	Client(Config config, WebSocketConnector webSocketConnector, long[] selfHealBackoffs, long connectTimeoutMillis) {
		this(config);
		this.webSocketConnector = webSocketConnector;
		this.selfHealBackoffs = Arrays.copyOf(selfHealBackoffs, selfHealBackoffs.length);
		this.connectTimeoutMillis = connectTimeoutMillis;
	}

	protected void finalize() {
		try { this.stop(); } catch (Exception e){}
	}
	//endregion Constructors

	//region Public Get Set
	public String getStats() {
		return String.format("Data Messages = %d, Text Messages = %d, Queue Depth = %d, Local queue drop count = %d", this.dataMsgCount.get(), this.textMsgCount.get(), getDataSize(), data.getDroppedCount());
	}

	public void setOnTrade(OnTrade onTrade) {
		this.onTrade = onTrade;
		this.useOnTrade = true;
	}

	public void setOnQuote(OnQuote onQuote) {
		this.onQuote = onQuote;
		this.useOnQuote = true;
	}

	public void setOnRefresh(OnRefresh onRefresh) {
		this.onRefresh = onRefresh;
		this.useOnRefresh = true;
	}

	public void setOnUnusualActivity(OnUnusualActivity onUnusualActivity) {
		this.onUnusualActivity = onUnusualActivity;
		this.useOnUnusualActivity = true;
	}
	//endregion Public Get Set

	//region Private Get Set
	private int getDataSize() {
		return data.size();
	}

	private boolean isAllReady() {
		wsLock.readLock().lock();
		try {
			return wsState.isReady();
		} finally {
			wsLock.readLock().unlock();
		}
	}

	private String getAuthUrl() throws Exception {
		String authUrl;
		switch (config.getOptionsProvider()) {
			case OPRA: authUrl = "https://realtime-options.intrinio.com/auth?api_key=" + config.getOptionsApiKey();
				break;
			case OPTIONS_EDGE: authUrl = "https://options-edge.intrinio.com/auth?api_key=" + config.getOptionsApiKey();
				break;
			case MANUAL: authUrl = "http://" + config.getOptionsIpAddress() + "/auth?api_key=" + config.getOptionsApiKey();
				break;
			default: throw new Exception("Provider not specified!");
		}
		return authUrl;
	}

	private String getWebSocketUrl (String token) throws Exception {
		String wsUrl;
		switch (config.getOptionsProvider()) {
			case OPRA: wsUrl = "wss://realtime-options.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + (this.config.isDelayed() ? "&delayed=true" : "");
				break;
			case OPTIONS_EDGE: wsUrl = "wss://options-edge.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + (this.config.isDelayed() ? "&delayed=true" : "");
				break;
			case MANUAL: wsUrl = "ws://" + config.getOptionsIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token + (this.config.isDelayed() ? "&delayed=true" : "");
				break;
			default: throw new Exception("Provider not specified!");
		}
		return wsUrl;
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

	public static void Log(String message, Object... args) {
		System.out.printf(message + "%n", args);
	}

	public static void Log(String message) {System.out.println(message);}

	public void join(String symbol) {
		if (!symbol.isBlank()) {
			while (!this.isAllReady()) {
				if (!waitForReady()) return;
			}
			this._join(symbol);
		}
	}

	public void join(String[] symbols) {
		for (String symbol : symbols) {
			this.join(symbol);
		}
	}

	public void join() { this.join(config.getOptionsSymbols()); }

	public void joinLobby() {
		if (containsChannel(FIREHOSE_CHANNEL)) {
			Client.Log("This client has already joined the lobby channel");
		} else {
			while (!this.isAllReady()) {
				if (!waitForReady()) return;
			}
			this._join(FIREHOSE_CHANNEL);
		}
	}

	public void leave(String symbol) {
		if (!symbol.isBlank()) {
			this._leave(symbol);
		}
	}

	public void leave(String[] symbols) {
		for (String symbol : symbols) {
			this.leave(symbol);
		}
	}

	public void leave() {
		for (String channel : snapshotChannels()) {
			this.leave(channel);
		}
	}

	public void leaveLobby() {
		if (containsChannel(FIREHOSE_CHANNEL)) this.leave(FIREHOSE_CHANNEL);
	}

	public void start() throws Exception {
		long generation;
		synchronized (lifecycleLock) {
			while (lifecycle == Lifecycle.STOPPING) lifecycleLock.wait();
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
			connected = isGenerationActive(generation) && isAllReady();
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
				int type = Byte.toUnsignedInt(message[22]);
				if (type == 0 && useOnTrade) onTrade.onTrade(Trade.parse(buffer));
				else if (type == 1 && useOnQuote) onQuote.onQuote(Quote.parse(buffer));
				else if (type == 2 && useOnRefresh) onRefresh.onRefresh(Refresh.parse(buffer));
				else if (type > 2 && useOnUnusualActivity) onUnusualActivity.onUnusualActivity(UnusualActivity.parse(buffer));
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception ex) {
				Client.Log("General Exception: %s", ex.getMessage());
			}
		}
	}

	private void startThreads() throws Exception {
		this.isCancellationRequested = false;
		for (int i = 0; i < processDataThreads.length; i++) {
			processDataThreads[i] = new Thread(this::processData, "intrinio-options-worker-" + i);
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

	private void _join(String symbol) {
		this._join(symbol, false);
	}

	private void _join(String symbol, boolean forceRejoin) {
		String translatedSymbol = translateContractToServerFormat(symbol);
		String standardFormatSymbol = translateContractToStandardFormat(translatedSymbol);
		boolean shouldSend;
		synchronized (channelsLock) {
			shouldSend = channels.add(translatedSymbol) || (channels.contains(translatedSymbol) && forceRejoin);
		}
		if (shouldSend) {
			byte optionMask = getChannelOptionMask();
			byte[] bytes = new byte[translatedSymbol.length() + 2];
			bytes[0] = (byte) 74;
			bytes[1] = optionMask;
			translatedSymbol.getBytes(StandardCharsets.US_ASCII);
			System.arraycopy(translatedSymbol.getBytes(StandardCharsets.US_ASCII), 0, bytes, 2, translatedSymbol.length());

			Client.Log("Websocket - Joining channel: %s (Trades: %s, Quotes: %s, Refreshes: %s, Unusual Activity: %s)", standardFormatSymbol, useOnTrade, useOnQuote, useOnRefresh, useOnUnusualActivity);
			try {
				sendBinary(getCurrentWebSocket(), bytes);
			} catch (Exception e) {
				Client.Log("Websocket - Warning while joining channel: %s", e.getMessage());
				markDisconnectedAndReconnect(getCurrentWebSocket());
			}
		}
	}

	private void _leave(String symbol) {
		String translatedSymbol = translateContractToServerFormat(symbol);
		String standardFormatSymbol = translateContractToStandardFormat(translatedSymbol);
		boolean removed;
		synchronized (channelsLock) {
			removed = channels.remove(translatedSymbol);
		}
		if (removed) {
			byte optionMask = getChannelOptionMask();
			byte[] bytes = new byte[translatedSymbol.length() + 2];
			bytes[0] = (byte) 76;
			bytes[1] = optionMask;
			translatedSymbol.getBytes(StandardCharsets.US_ASCII);
			System.arraycopy(translatedSymbol.getBytes(StandardCharsets.US_ASCII), 0, bytes, 2, translatedSymbol.length());

			Client.Log("Websocket - leaving channel: %s (Trades: %s, Quotes: %s, Refreshes: %s, Unusual Activity: %s)", standardFormatSymbol, useOnTrade, useOnQuote, useOnRefresh, useOnUnusualActivity);
			try {
				sendBinary(getCurrentWebSocket(), bytes);
			} catch (Exception e) {
				Client.Log("Websocket - Warning while leaving channel: %s", e.getMessage());
			}
		}
	}

	private void onWebSocketConnected(WebSocket ws) throws Exception {
		for (String channel : snapshotChannels()) {
			String standardFormatSymbol = translateContractToStandardFormat(channel);
			byte[] bytes = new byte[channel.length() + 2];
			bytes[0] = (byte)74;
			bytes[1] = getChannelOptionMask();
			System.arraycopy(channel.getBytes(StandardCharsets.US_ASCII), 0, bytes, 2, channel.length());
			Client.Log("Websocket - Rejoining channel: %s", standardFormatSymbol);
			sendBinary(ws, bytes);
		}
	}

	private CompletableFuture<WebSocket> openWebSocket(URI uri, WebSocket.Listener listener) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMillis))
			.build();
		return httpClient.newWebSocketBuilder().buildAsync(uri, listener);
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
			if (created != null && (!isAllReady() || !isCurrentWebSocket(created))) {
				abortWebSocket(created);
				failedWebSockets.remove(created);
			}
		}
	}

	private byte getChannelOptionMask() {
		int optionMask = 0b0000;
		if (useOnTrade) {
			optionMask = optionMask | 0b0001;
		}
		if (useOnQuote) {
			optionMask = optionMask | 0b0010;
		}
		if (useOnRefresh) {
			optionMask = optionMask | 0b0100;
		}
		if (useOnUnusualActivity) {
			optionMask = optionMask | 0b1000;
		}
		return (byte) optionMask;
	}

	private boolean tryGetNewToken() {
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
			URI uri = new URI(authUrl);
			url = uri.toURL();
		} catch (URISyntaxException e) {
			Client.Log("Authorization Failure. Bad URI (%s). %s", authUrl, e.getMessage());
			return false;
		} catch (MalformedURLException e) {
			Client.Log("Authorization Failure. Bad URL (%s). %s", authUrl, e.getMessage());
			return false;
		}
		HttpURLConnection con;
		try {
			con = (HttpURLConnection) url.openConnection();
			con.setConnectTimeout((int)Math.min(connectTimeoutMillis, Integer.MAX_VALUE));
			con.setReadTimeout((int)Math.min(connectTimeoutMillis, Integer.MAX_VALUE));
			con.setRequestProperty("Client-Information", Version);
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
			if (offset + 22 >= datum.length) {
				Client.Log("Error parsing multi-part message: incomplete header");
				return;
			}
			int type = Byte.toUnsignedInt(datum[offset + 22]);
			int messageLength;
			int priority;
			if (type == 0) {
				messageLength = TRADE_MESSAGE_SIZE;
				priority = 0;
			} else if (type == 1) {
				messageLength = QUOTE_MESSAGE_SIZE;
				priority = 3;
			} else if (type == 2) {
				messageLength = REFRESH_MESSAGE_SIZE;
				priority = 2;
			} else {
				messageLength = UNUSUAL_ACTIVITY_MESSAGE_SIZE;
				priority = 1;
			}
			if (offset + messageLength > datum.length) {
				Client.Log("Error parsing multi-part message: invalid length %d", messageLength);
				return;
			}
			data.offer(priority, Arrays.copyOfRange(datum, offset, offset + messageLength));
			offset += messageLength;
		}
	}

	private void startReconnectWorker() {
		synchronized (lifecycleLock) {
			if (lifecycle != Lifecycle.STARTED || isCancellationRequested || isAllReady()) return;
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
				if (isGenerationActive(generation) && !isAllReady()) startReconnectWorker();
			}
		}, "intrinio-options-reconnect");
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

	private boolean containsChannel(String channel) {
		synchronized (channelsLock) {
			return channels.contains(channel);
		}
	}

	private String[] snapshotChannels() {
		synchronized (channelsLock) {
			return channels.toArray(new String[0]);
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
				.orTimeout(CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
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
			thread.join(THREAD_JOIN_TIMEOUT_MILLIS);
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
			System.arraycopy(part, 0, bytes, index, part.length);
			index += part.length;
		}
		return bytes;
	}

	//endregion Private Methods

	//region Private Static Helper Methods
	private static String trimStart(String str, char character){
		boolean done = false;
		int i = 0;
		while (!done){
			if (i >= str.length() || str.charAt(i) != character){ //short circuit prevents out of bounds
				done = true;
			}
			else i++;
		}
		if (i == str.length())
			return "";
		else if (i == 0) {
			return str;
		} else
			return str.substring(i);
	}

	private static String trimTrailing(String str, char character){
		boolean done = false;
		int i = str.length()-1;
		while (!done){
			if (i < 0 || str.charAt(i) != character){ //short circuit prevents out of bounds
				done = true;
			}
			else i--;
		}
		if (i == -1)
			return "";
		else if (i == str.length()-1) {
			return str;
		} else
			return str.substring(0, i + 1);
	}

	private static String translateContractToStandardFormat(String contract){
		if ((contract.length() >= 9) && (contract.indexOf(".")>=9)) { //this is of the server format and we need to translate it. ex: from ABC_221216P145.00 to AAPL__220101C00140000
			//Transform from server format to normal format
			//From this: AAPL_201016C100.00 or ABC_201016C100.003
			//To this:   AAPL__201016C00100000 or ABC___201016C00100003
			char[] contractChars = new char[]{'_','_','_','_','_','_','2','2','0','1','0','1','C','0','0','0','0','0','0','0','0'};
			int underscoreIndex = contract.indexOf('_');

			//copy symbol
			contract.getChars(0, underscoreIndex, contractChars, 0);

			//copy date
			contract.getChars(underscoreIndex + 1, underscoreIndex + 7, contractChars, 6);

			//copy put/call
			contract.getChars(underscoreIndex + 7, underscoreIndex + 8, contractChars, 12);

			int decimalIndex = contract.indexOf('.', 9);

			//whole number copy
			contract.getChars(underscoreIndex + 8, decimalIndex, contractChars, 18 - (decimalIndex - underscoreIndex - 8));

			//decimal number copy
			contract.getChars(decimalIndex + 1, contract.length(), contractChars, 18);

			return new String(contractChars);
		}
		else { //this is of the standard format already: AAPL__220101C00140000, TSLA__221111P00195000
			return contract;
		}
	}

	private static String translateContractToServerFormat(String contract){
		if ((contract.length() <= 9) || (contract.indexOf(".")>=9)) {
			return contract;
		}
		else { //this is of the standard format, and we need to translate it. ex from AAPL__220101C00140000, TSLA__221111P00195000 to ABC_221216P145.00
			String symbol = trimTrailing(contract.substring(0, 6), '_');
			String date = contract.substring(6, 12);
			char callPut = contract.charAt(12);
			String wholePrice = trimStart(contract.substring(13, 18), '0');
			if (wholePrice.isEmpty())
				wholePrice = "0";
			String decimalPrice = contract.substring(18);
			if (decimalPrice.charAt(2) == '0')
				decimalPrice = decimalPrice.substring(0, 2);
			return String.format("%s_%s%s%s.%s", symbol, date, callPut, wholePrice, decimalPrice);
		}
	}
	//endregion Private Static Helper Methods

	private class Tuple<X, Y> {
		  public final X x; 
		  public final Y y; 
		  public Tuple(X x, Y y) { 
		    this.x = x; 
		    this.y = y; 
		  } 
	}
		

		



		



	

}
