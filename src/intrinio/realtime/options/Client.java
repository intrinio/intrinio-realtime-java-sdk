package intrinio.realtime.options;

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
	private static final String FIREHOSE_CHANNEL = "$FIREHOSE";
	private static final long[] SELF_HEAL_BACKOFFS = {1_000, 30_000, 60_000, 300_000, 600_000};
	private static final int CONNECT_TIMEOUT_MS = 30_000;
	private static final int THREAD_JOIN_TIMEOUT_MS = 10_000;
	private static final int MESSAGE_TYPE_INDEX = 22;
	private static final String VERSION = "IntrinioRealtimeOptionsJavaSDKv9.1";
	private static final String HEADER_CLIENT_INFORMATION_KEY = "Client-Information";

	private final Set<String> channels = ConcurrentHashMap.newKeySet();
	private final LinkedBlockingDeque<byte[]> inboundData = new LinkedBlockingDeque<>();
	private final OptionsMessageQueue data = new OptionsMessageQueue();
	private final Object fragmentLock = new Object();
	private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();
	private final AtomicLong dataMsgCount = new AtomicLong(0L);
	private final AtomicLong textMsgCount = new AtomicLong(0L);
	private final ClientConnectionManager connection;
	private final int connectTimeoutMs;

	private Config config;
	private volatile Thread[] processDataThreads = new Thread[0];
	private volatile boolean processing;
	private volatile long processingGeneration;
	private volatile Thread prioritizeThread;
	private volatile HttpURLConnection authConnection;
	private OnTrade onTrade = trade -> {};
	private volatile boolean useOnTrade = false;
	private OnQuote onQuote = quote -> {};
	private volatile boolean useOnQuote = false;
	private OnRefresh onRefresh = refresh -> {};
	private volatile boolean useOnRefresh = false;
	private OnUnusualActivity onUnusualActivity = unusualActivity -> {};
	private volatile boolean useOnUnusualActivity = false;

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

	@Override
	protected void finalize() {
		try { this.stop(); } catch (Exception ignored) {}
	}

	public String getStats() {
		return String.format("Data Messages = %d, Text Messages = %d, Queue Depth = %d",
			dataMsgCount.get(), textMsgCount.get(), inboundData.size() + data.size());
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

	public static void Log(String message, Object... args) {
		System.out.printf(message + "%n", args);
	}

	public static void Log(String message) {
		System.out.println(message);
	}

	public void join(String symbol) {
		if (symbol == null || symbol.isBlank()) return;
		connection.awaitReady();
		_join(symbol);
	}

	public void join(String[] symbols) {
		for (String symbol : symbols) join(symbol);
	}

	public void join() {
		String[] symbols = config.getOptionsSymbols();
		if (symbols != null) join(symbols);
	}

	public void joinLobby() {
		connection.awaitReady();
		if (!channels.add(FIREHOSE_CHANNEL)) {
			Log("This client has already joined the lobby channel");
			return;
		}
		try {
			sendJoin(connection.currentSocket(), FIREHOSE_CHANNEL, false);
		} catch (Exception exception) {
			channels.remove(FIREHOSE_CHANNEL);
			Log("Websocket - Failed joining lobby: %s", exception.getMessage());
		}
	}

	public void leave(String symbol) {
		if (symbol != null && !symbol.isBlank()) _leave(symbol);
	}

	public void leave(String[] symbols) {
		for (String symbol : symbols) leave(symbol);
	}

	public void leave() {
		for (String channel : Set.copyOf(channels)) _leave(channel);
	}

	public void leaveLobby() {
		_leave(FIREHOSE_CHANNEL);
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
		switch (config.getOptionsProvider()) {
			case OPRA:
				return "https://realtime-options.intrinio.com/auth?api_key=" + config.getOptionsApiKey();
			case OPTIONS_EDGE:
				return "https://options-edge.intrinio.com/auth?api_key=" + config.getOptionsApiKey();
			case MANUAL:
				return "http://" + config.getOptionsIpAddress() + "/auth?api_key=" + config.getOptionsApiKey();
			default:
				throw new Exception("Provider not specified!");
		}
	}

	private String getWebSocketUrl(String token) throws Exception {
		String delayedPart = config.isDelayed() ? "&delayed=true" : "";
		switch (config.getOptionsProvider()) {
			case OPRA:
				return "wss://realtime-options.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
			case OPTIONS_EDGE:
				return "wss://options-edge.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
			case MANUAL:
				return "ws://" + config.getOptionsIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token + delayedPart;
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
			.header(HEADER_CLIENT_INFORMATION_KEY, VERSION)
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
			connection.setRequestProperty(HEADER_CLIENT_INFORMATION_KEY, VERSION);
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
				Log("Error prioritizing options message: %s", exception.getMessage());
			}
		}
	}

	private void processData(long session) {
		while (processing && processingGeneration == session) {
			try {
				ByteBuffer message = data.poll(250, TimeUnit.MILLISECONDS);
				if (message == null || processingGeneration != session) continue;
				byte type = message.get(MESSAGE_TYPE_INDEX);
				if (type == 0) {
					if (useOnTrade) onTrade.onTrade(Trade.parse(message));
				} else if (type == 1) {
					if (useOnQuote) onQuote.onQuote(Quote.parse(message));
				} else if (type == 2) {
					if (useOnRefresh) onRefresh.onRefresh(Refresh.parse(message));
				} else {
					if (useOnUnusualActivity) onUnusualActivity.onUnusualActivity(UnusualActivity.parse(message));
				}
			} catch (InterruptedException exception) {
				if (!processing) return;
			} catch (Exception exception) {
				Log("Error processing options message: %s", exception.getMessage());
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
		prioritizeThread = new Thread(() -> prioritizeData(session), "intrinio-options-prioritizer");
		prioritizeThread.setDaemon(true);
		prioritizeThread.start();
		int threadCount = config == null ? 1 : Math.max(config.getOptionsNumThreads(), 1);
		processDataThreads = new Thread[threadCount];
		for (int i = 0; i < processDataThreads.length; i++) {
			Thread thread = new Thread(() -> processData(session), "intrinio-options-processor-" + i);
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

	private void _join(String symbol) {
		String translatedSymbol = translateContractToServerFormat(symbol);
		if (!channels.add(translatedSymbol)) return;
		try {
			sendJoin(connection.currentSocket(), translatedSymbol, false);
		} catch (Exception exception) {
			channels.remove(translatedSymbol);
			Log("Websocket - Failed joining channel %s: %s", symbol, exception.getMessage());
		}
	}

	private void _leave(String symbol) {
		String translatedSymbol = translateContractToServerFormat(symbol);
		if (!channels.remove(translatedSymbol)) return;
		WebSocket webSocket = connection.currentSocket();
		if (webSocket == null) return;
		String standardFormatSymbol = translateContractToStandardFormat(translatedSymbol);
		Log("Websocket - leaving channel: %s (Trades: %s, Quotes: %s, Refreshes: %s, Unusual Activity: %s)",
			standardFormatSymbol, useOnTrade, useOnQuote, useOnRefresh, useOnUnusualActivity);
		webSocket.sendBinary(ByteBuffer.wrap(makeChannelMessage((byte) 76, translatedSymbol)), true);
	}

	private void rejoinChannels(WebSocket webSocket) throws Exception {
		for (String channel : Set.copyOf(channels)) sendJoin(webSocket, channel, true);
	}

	private void sendJoin(WebSocket webSocket, String translatedSymbol, boolean waitForSend) throws Exception {
		if (webSocket == null) throw new IOException("Websocket is not connected");
		String standardFormatSymbol = translateContractToStandardFormat(translatedSymbol);
		Log("Websocket - Joining channel: %s (Trades: %s, Quotes: %s, Refreshes: %s, Unusual Activity: %s)",
			standardFormatSymbol, useOnTrade, useOnQuote, useOnRefresh, useOnUnusualActivity);
		CompletableFuture<WebSocket> send = webSocket.sendBinary(
			ByteBuffer.wrap(makeChannelMessage((byte) 74, translatedSymbol)), true);
		if (waitForSend) send.get(connectTimeoutMs, TimeUnit.MILLISECONDS);
	}

	private byte[] makeChannelMessage(byte messageType, String symbol) {
		byte[] symbolBytes = symbol.getBytes(StandardCharsets.US_ASCII);
		byte[] bytes = new byte[symbolBytes.length + 2];
		bytes[0] = messageType;
		bytes[1] = getChannelOptionMask();
		System.arraycopy(symbolBytes, 0, bytes, 2, symbolBytes.length);
		return bytes;
	}

	private byte getChannelOptionMask() {
		int optionMask = 0;
		if (useOnTrade) optionMask |= 0b0001;
		if (useOnQuote) optionMask |= 0b0010;
		if (useOnRefresh) optionMask |= 0b0100;
		if (useOnUnusualActivity) optionMask |= 0b1000;
		return (byte) optionMask;
	}

	private static String trimStart(String value, char character) {
		int index = 0;
		while (index < value.length() && value.charAt(index) == character) index++;
		return index == value.length() ? "" : value.substring(index);
	}

	private static String trimTrailing(String value, char character) {
		int index = value.length() - 1;
		while (index >= 0 && value.charAt(index) == character) index--;
		return index < 0 ? "" : value.substring(0, index + 1);
	}

	private static String translateContractToStandardFormat(String contract) {
		if (contract.length() < 9 || contract.indexOf('.') < 9) return contract;
		char[] contractChars = new char[]{'_','_','_','_','_','_','2','2','0','1','0','1','C','0','0','0','0','0','0','0','0'};
		int underscoreIndex = contract.indexOf('_');
		contract.getChars(0, underscoreIndex, contractChars, 0);
		contract.getChars(underscoreIndex + 1, underscoreIndex + 7, contractChars, 6);
		contract.getChars(underscoreIndex + 7, underscoreIndex + 8, contractChars, 12);
		int decimalIndex = contract.indexOf('.', 9);
		contract.getChars(underscoreIndex + 8, decimalIndex, contractChars, 18 - (decimalIndex - underscoreIndex - 8));
		contract.getChars(decimalIndex + 1, contract.length(), contractChars, 18);
		return new String(contractChars);
	}

	private static String translateContractToServerFormat(String contract) {
		if (contract.length() <= 9 || contract.indexOf('.') >= 9) return contract;
		String symbol = trimTrailing(contract.substring(0, 6), '_');
		String date = contract.substring(6, 12);
		char callPut = contract.charAt(12);
		String wholePrice = trimStart(contract.substring(13, 18), '0');
		if (wholePrice.isEmpty()) wholePrice = "0";
		String decimalPrice = contract.substring(18);
		if (decimalPrice.charAt(2) == '0') decimalPrice = decimalPrice.substring(0, 2);
		return String.format("%s_%s%s%s.%s", symbol, date, callPut, wholePrice, decimalPrice);
	}
}
