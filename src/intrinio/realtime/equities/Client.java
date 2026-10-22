package intrinio.realtime.equities;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Client implements WebSocket.Listener {
	private static final String FIREHOSE_CHANNEL_NAME = "lobby";
	private static final long[] SELF_HEAL_BACKOFFS = {1_000, 30_000, 60_000, 300_000, 600_000};
	private static final int CONNECT_TIMEOUT_MS = 30_000;
	private static final int THREAD_JOIN_TIMEOUT_MS = 10_000;
	private static final String HEADER_CLIENT_INFORMATION_KEY = "Client-Information";
	private static final String HEADER_CLIENT_INFORMATION_VALUE = "IntrinioRealtimeJavaSDKv9.1";
	private static final String HEADER_MESSAGE_VERSION_KEY = "UseNewEquitiesFormat";
	private static final String HEADER_MESSAGE_VERSION_VALUE = "v2";

	private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
	private final LinkedBlockingDeque<byte[]> inboundData = new LinkedBlockingDeque<>();
	private final EquitiesMessageQueue data = new EquitiesMessageQueue();
	private final Object fragmentLock = new Object();
	private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();
	private final AtomicLong dataMsgCount = new AtomicLong(0L);
	private final AtomicLong textMsgCount = new AtomicLong(0L);
	private final ClientConnectionManager connection;
	private final int connectTimeoutMs;

	private Config config;
	private OnTrade onTrade = trade -> {};
	private OnQuote onQuote = quote -> {};
	private volatile boolean processing;
	private volatile long processingGeneration;
	private volatile Thread[] processDataThreads = new Thread[0];
	private volatile Thread prioritizeThread;
	private volatile HttpURLConnection authConnection;

	public Client() {
		this(Config.load());
	}

	public Client(Config config) {
		this(config, null, null, SELF_HEAL_BACKOFFS, CONNECT_TIMEOUT_MS);
	}

	Client(
		Config config,
		ClientConnectionManager.TokenFetcher tokenFetcher,
		ClientConnectionManager.SocketConnector socketConnector,
		long[] backoffs,
		int connectTimeoutMs) {
		this.config = config;
		this.connectTimeoutMs = connectTimeoutMs;
		this.connection = new ClientConnectionManager(
			tokenFetcher == null ? this::tryGetNewToken : tokenFetcher,
			token -> new URI(getWebSocketUrl(token)),
			socketConnector == null ? this::connectWebSocket : socketConnector,
			new ClientConnectionManager.Callbacks() {
				@Override public void onSessionStarting() { startThreads(); }
				@Override public void onSessionStopping() { stopThreads(); }
				@Override public void onDisconnected() { clearFragments(); }
				@Override public void onConnected(WebSocket webSocket) throws Exception { rejoinChannels(webSocket); }
				@Override public void onBinary(WebSocket webSocket, ByteBuffer bytes, boolean isComplete) {
					handleBinary(bytes, isComplete);
				}
				@Override public void onText(WebSocket webSocket, CharSequence text, boolean isComplete) {
					handleText(text);
				}
				@Override public void log(String message, Object... args) { Client.Log(message, args); }
			},
			backoffs,
			connectTimeoutMs);
	}

	Client(
		OnTrade onTrade,
		OnQuote onQuote,
		Config config,
		ClientConnectionManager.TokenFetcher tokenFetcher,
		ClientConnectionManager.SocketConnector socketConnector,
		long[] backoffs,
		int connectTimeoutMs) {
		this(config, tokenFetcher, socketConnector, backoffs, connectTimeoutMs);
		this.onTrade = onTrade;
		this.onQuote = onQuote;
	}

	public Client(OnTrade onTrade) {
		this();
		this.onTrade = onTrade;
	}

	public Client(OnTrade onTrade, OnQuote onQuote) {
		this();
		this.onTrade = onTrade;
		this.onQuote = onQuote;
	}

	public Client(OnTrade onTrade, Config config) {
		this(config);
		this.onTrade = onTrade;
	}

	public Client(OnTrade onTrade, OnQuote onQuote, Config config) {
		this(config);
		this.onTrade = onTrade;
		this.onQuote = onQuote;
	}

	@Override
	protected void finalize() {
		try { this.stop(); } catch (Exception ignored) {}
	}

	public String getStats() {
		return String.format("Data Messages = %d, Text Messages = %d, Queue Depth = %d",
			dataMsgCount.get(), textMsgCount.get(), inboundData.size() + data.size());
	}

	@Override
	public CompletionStage<Void> onClose(WebSocket webSocket, int status, String reason) {
		return connection.onClose(webSocket, status, reason);
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		connection.onError(webSocket, error);
	}

	@Override
	public CompletionStage<Void> onText(WebSocket webSocket, CharSequence text, boolean isComplete) {
		if (connection.isCurrent(webSocket)) handleText(text);
		if (connection.isCurrent(webSocket)) webSocket.request(1);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletionStage<Void> onBinary(WebSocket webSocket, ByteBuffer bytes, boolean isComplete) {
		if (connection.isCurrent(webSocket)) handleBinary(bytes, isComplete);
		if (connection.isCurrent(webSocket)) webSocket.request(1);
		return CompletableFuture.completedFuture(null);
	}

	public static void Log(String message) {
		System.out.println(message);
	}

	public static void Log(String message, Object... args) {
		System.out.printf(message + "%n", args);
	}

	public void join() {
		connection.awaitReady();
		String[] symbols = config.getEquitiesSymbols();
		if (symbols == null) return;
		boolean tradesOnly = config.isEquitiesTradesOnly();
		for (String symbol : symbols) _join(symbol, tradesOnly);
	}

	public void join(String symbol, boolean tradesOnly) {
		connection.awaitReady();
		_join(symbol, tradesOnly || config.isEquitiesTradesOnly());
	}

	public void join(String symbol) {
		join(symbol, false);
	}

	public void join(String[] symbols, boolean tradesOnly) {
		connection.awaitReady();
		boolean effectiveTradesOnly = tradesOnly || config.isEquitiesTradesOnly();
		for (String symbol : symbols) _join(symbol, effectiveTradesOnly);
	}

	public void join(String[] symbols) {
		join(symbols, false);
	}

	public void joinLobby() {
		joinLobby(false);
	}

	public void joinLobby(boolean tradesOnly) {
		connection.awaitReady();
		_join(FIREHOSE_CHANNEL_NAME, tradesOnly || config.isEquitiesTradesOnly());
	}

	public void leave() {
		for (Channel channel : Set.copyOf(channels)) _leave(channel.symbol(), channel.tradesOnly());
	}

	public void leave(String symbol) {
		for (Channel channel : Set.copyOf(channels)) {
			if (channel.symbol().equals(symbol)) _leave(symbol, channel.tradesOnly());
		}
	}

	public void leaveLobby() {
		leave(FIREHOSE_CHANNEL_NAME);
	}

	public void leave(String[] symbols) {
		for (String symbol : symbols) leave(symbol);
	}

	public void start() throws Exception {
		connection.start();
	}

	public void stop() {
		Log("Websocket - Closing");
		connection.stop();
		Log("Stopped");
	}

	private String getAuthUrl() throws Exception {
		switch (config.getEquitiesProvider()) {
			case REALTIME:
			case IEX:
				return "https://realtime-mx.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
			case DELAYED_SIP:
				return "https://realtime-delayed-sip.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
			case NASDAQ_BASIC:
				return "https://realtime-nasdaq-basic.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
			case CBOE_ONE:
				return "https://cboe-one.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
			case EQUITIES_EDGE:
				return "https://equities-edge.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
			case MANUAL:
				return "http://" + config.getEquitiesIpAddress() + "/auth?api_key=" + config.getEquitiesApiKey();
			default:
				throw new Exception("Provider not specified!");
		}
	}

	private String getWebSocketUrl(String token) throws Exception {
		String delayedPart = config.isEquitiesDelayed() ? "&delayed=true" : "";
		switch (config.getEquitiesProvider()) {
			case REALTIME:
			case IEX:
				return "wss://realtime-mx.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
			case DELAYED_SIP:
				return "wss://realtime-delayed-sip.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
			case NASDAQ_BASIC:
				return "wss://realtime-nasdaq-basic.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
			case CBOE_ONE:
				return "wss://cboe-one.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
			case EQUITIES_EDGE:
				return "wss://equities-edge.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
			case MANUAL:
				return "ws://" + config.getEquitiesIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
			default:
				throw new Exception("Provider not specified!");
		}
	}

	private CompletableFuture<WebSocket> connectWebSocket(URI uri, WebSocket.Listener listener) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		return httpClient.newWebSocketBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.header(HEADER_MESSAGE_VERSION_KEY, HEADER_MESSAGE_VERSION_VALUE)
			.header(HEADER_CLIENT_INFORMATION_KEY, HEADER_CLIENT_INFORMATION_VALUE)
			.buildAsync(uri, listener);
	}

	private String tryGetNewToken() {
		HttpURLConnection connection = null;
		try {
			URL url = new URI(getAuthUrl()).toURL();
			connection = (HttpURLConnection) url.openConnection();
			authConnection = connection;
			connection.setConnectTimeout(connectTimeoutMs);
			connection.setReadTimeout(connectTimeoutMs);
			connection.setRequestProperty(HEADER_CLIENT_INFORMATION_KEY, HEADER_CLIENT_INFORMATION_VALUE);
			connection.setRequestMethod("GET");
			int status = connection.getResponseCode();
			if (status != 200) {
				Log("Authorization Failure (%d). The authorization key you provided is likely incorrect.", status);
				return null;
			}
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				String token = reader.readLine();
				Log("Authorization successful");
				return token;
			}
		} catch (Exception exception) {
			Log("Authorization Failure. %s", exception.getMessage());
			return null;
		} finally {
			if (authConnection == connection) authConnection = null;
			if (connection != null) connection.disconnect();
		}
	}

	private void handleText(CharSequence text) {
		textMsgCount.incrementAndGet();
		if (text != null && text.length() > 0) Log("Error received: %s", text.toString());
	}

	private void handleBinary(ByteBuffer source, boolean isComplete) {
		dataMsgCount.incrementAndGet();
		byte[] bytes = new byte[source.remaining()];
		source.get(bytes);
		byte[] complete = null;
		synchronized (fragmentLock) {
			if (isComplete && fragments.size() == 0) {
				complete = bytes;
			} else {
				fragments.write(bytes, 0, bytes.length);
			}
			if (isComplete && complete == null) {
				complete = fragments.toByteArray();
				fragments.reset();
			}
		}
		if (complete != null) inboundData.addLast(complete);
	}

	private void clearFragments() {
		synchronized (fragmentLock) { fragments.reset(); }
	}

	private void prioritizeData(long session) {
		while (processing && processingGeneration == session) {
			try {
				byte[] batch = inboundData.pollFirst(250, TimeUnit.MILLISECONDS);
				if (batch != null && processingGeneration == session) data.enqueueBatch(batch);
			} catch (InterruptedException exception) {
				if (!processing || processingGeneration != session) return;
			} catch (Exception exception) {
				Log("Error prioritizing equities message: %s", exception.getMessage());
			}
		}
	}

	private void processData(long session) {
		while (processing && processingGeneration == session) {
			try {
				ByteBuffer message = data.poll(250, TimeUnit.MILLISECONDS);
				if (message == null || processingGeneration != session) continue;
				switch (message.get(0)) {
					case 0:
						onTrade.onTrade(Trade.parse(message));
						break;
					case 1:
					case 2:
						onQuote.onQuote(Quote.parse(message));
						break;
					default:
						Log("Error parsing message. Type is %d", message.get(0));
				}
			} catch (InterruptedException exception) {
				if (!processing) return;
			} catch (Exception exception) {
				Log("Error processing equities message: %s", exception.getMessage());
			}
		}
	}

	private void startThreads() {
		inboundData.clear();
		data.clear();
		clearFragments();
		dataMsgCount.set(0L);
		textMsgCount.set(0L);
		processing = true;
		long session = ++processingGeneration;
		prioritizeThread = new Thread(() -> prioritizeData(session), "intrinio-equities-prioritizer");
		prioritizeThread.setDaemon(true);
		prioritizeThread.start();
		int threadCount = config == null ? 1 : Math.max(config.getEquitiesNumThreads(), 1);
		processDataThreads = new Thread[threadCount];
		for (int i = 0; i < processDataThreads.length; i++) {
			Thread thread = new Thread(() -> processData(session), "intrinio-equities-processor-" + i);
			thread.setDaemon(true);
			processDataThreads[i] = thread;
			thread.start();
		}
	}

	private void stopThreads() {
		HttpURLConnection activeAuth = authConnection;
		if (activeAuth != null) activeAuth.disconnect();
		processing = false;
		processingGeneration++;
		Thread prioritizer = prioritizeThread;
		if (prioritizer != null) prioritizer.interrupt();
		Thread[] threads = processDataThreads;
		for (Thread thread : threads) if (thread != null) thread.interrupt();
		for (Thread thread : threads) {
			if (thread == null || thread == Thread.currentThread()) continue;
			try { thread.join(THREAD_JOIN_TIMEOUT_MS); }
			catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
		}
		if (prioritizer != null && prioritizer != Thread.currentThread()) {
			try { prioritizer.join(THREAD_JOIN_TIMEOUT_MS); }
			catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
		}
		inboundData.clear();
		data.clear();
		clearFragments();
	}

	private void _join(String symbol, boolean tradesOnly) {
		if (symbol == null || symbol.isBlank()) return;
		Channel channel = new Channel(symbol, tradesOnly);
		if (!channels.add(channel)) return;
		try {
			sendJoin(connection.currentSocket(), channel, false);
		} catch (Exception exception) {
			channels.remove(channel);
			Log("Websocket - Failed joining channel %s: %s", symbol, exception.getMessage());
		}
	}

	private void _leave(String symbol, boolean tradesOnly) {
		Channel channel = new Channel(symbol, tradesOnly);
		if (!channels.remove(channel)) return;
		WebSocket webSocket = connection.currentSocket();
		if (webSocket == null) return;
		Log("Websocket - Leaving channel: %s (trades only = %s)", symbol, Boolean.toString(tradesOnly));
		webSocket.sendBinary(ByteBuffer.wrap(makeLeaveMessage(symbol)), true);
	}

	private void rejoinChannels(WebSocket webSocket) throws Exception {
		for (Channel channel : Set.copyOf(channels)) sendJoin(webSocket, channel, true);
	}

	private void sendJoin(WebSocket webSocket, Channel channel, boolean waitForSend) throws Exception {
		if (webSocket == null) throw new IOException("Websocket is not connected");
		Log("Websocket - Joining channel: %s (trades only = %s)",
			channel.symbol(), Boolean.toString(channel.tradesOnly()));
		CompletableFuture<WebSocket> send = webSocket.sendBinary(
			ByteBuffer.wrap(makeJoinMessage(channel.tradesOnly(), channel.symbol())), true);
		if (waitForSend) send.get(connectTimeoutMs, TimeUnit.MILLISECONDS);
	}

	private byte[] makeJoinMessage(boolean tradesOnly, String symbol) {
		byte[] symbolBytes = FIREHOSE_CHANNEL_NAME.equals(symbol)
			? "$FIREHOSE".getBytes(StandardCharsets.US_ASCII)
			: symbol.getBytes(StandardCharsets.US_ASCII);
		byte[] message = new byte[2 + symbolBytes.length];
		message[0] = (byte) 74;
		message[1] = tradesOnly ? (byte) 1 : (byte) 0;
		System.arraycopy(symbolBytes, 0, message, 2, symbolBytes.length);
		return message;
	}

	private byte[] makeLeaveMessage(String symbol) {
		byte[] symbolBytes = FIREHOSE_CHANNEL_NAME.equals(symbol)
			? "$FIREHOSE".getBytes(StandardCharsets.US_ASCII)
			: symbol.getBytes(StandardCharsets.US_ASCII);
		byte[] message = new byte[1 + symbolBytes.length];
		message[0] = (byte) 76;
		System.arraycopy(symbolBytes, 0, message, 1, symbolBytes.length);
		return message;
	}
}
