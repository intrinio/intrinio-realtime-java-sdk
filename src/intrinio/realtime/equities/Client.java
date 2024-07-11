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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Client implements WebSocket.Listener {
	//region Final Data Members
	private final String FirehoseChannelName = "lobby";
	private final long[] selfHealBackoffs = {1000, 30000, 60000, 300000, 600000};
	private final ReentrantReadWriteLock tLock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock wsLock = new ReentrantReadWriteLock();
	private final LinkedBlockingDeque<byte[]> data = new LinkedBlockingDeque<>();
	private final HashSet<Channel> channels = new HashSet<Channel>();
	private final WebSocketState wsState = new WebSocketState();
	private final LinkedBlockingDeque<Tuple<byte[], Boolean>> dataBucket = new LinkedBlockingDeque<Tuple<byte[], Boolean>>();
	private final Lock dataBucketLock = new ReentrantLock();;
	//endregion Final Data Members

	//region Data Members
	private Config config;
	private AtomicReference<Token> token = new AtomicReference<Token>(new Token(null, LocalDateTime.now()));
	private AtomicLong dataMsgCount = new AtomicLong(0l);
	private AtomicLong textMsgCount = new AtomicLong(0l);
	private OnTrade onTrade = (Trade trade) -> {};
	private OnQuote onQuote = (Quote quote) -> {};
	private Thread[] processDataThreads;
	private boolean isCancellationRequested = false;
	private String HeaderClientInformationKey = "Client-Information";
	private String HeaderClientInformationValue = "IntrinioRealtimeJavaSDKv6.1";
	private String HeaderMessageVersionKey = "UseNewEquitiesFormat";
	private String HeaderMessageVersionValue = "v2";
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
		return String.format("Data Messages = %d, Text Messages = %d, Queue Depth = %d", this.dataMsgCount.get(), this.textMsgCount.get(), getDataSize());
	}
	//endregion Public Get Set

	//region Private Get Set
	private int getDataSize() {
		return dataBucket.size();
	}

	private String getAuthUrl() throws Exception {
		String authUrl;
		switch (config.getEquitiesProvider()) {
			case REALTIME: authUrl = "https://realtime-mx.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case DELAYED_SIP: authUrl = "https://realtime-delayed-sip.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case NASDAQ_BASIC: authUrl = "https://realtime-nasdaq-basic.intrinio.com/auth?api_key=" + config.getEquitiesApiKey();
				break;
			case MANUAL: authUrl = "http://" + config.getEquitiesIpAddress() + "/auth?api_key=" + config.getEquitiesApiKey();
				break;
			default: throw new Exception("Provider not specified!");
		}
		return authUrl;
	}

	private String getWebSocketUrl (String token) throws Exception {
		String wsUrl;
		switch (config.getEquitiesProvider()) {
			case REALTIME: wsUrl = "wss://realtime-mx.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token;
				break;
			case DELAYED_SIP: wsUrl = "wss://realtime-delayed-sip.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token;
				break;
			case NASDAQ_BASIC: wsUrl = "wss://realtime-nasdaq-basic.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token;
				break;
			case MANUAL: wsUrl = "ws://" + config.getEquitiesIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token;
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
		wsLock.writeLock().lock();
		try {
			try {
				this.wsState.getWebSocket().sendClose(1000, "Client closed");
			}catch (Exception ex){}

			if (!wsState.isReconnecting()) {
				Client.Log("Websocket - Closed");
				wsState.setReady(false);
				if (!isCancellationRequested){
					this.wsState.setReconnecting(true);
					try {
						new Thread(() -> {
							try { Thread.sleep(1000); } catch (Exception e){}
							this.doWithRetryBackoff(() -> reconnect());
						}).start();
					}catch (Exception e){}
				}
			}
		} finally {
			wsLock.writeLock().unlock();
		}
		return null;
	}

	public void onError(WebSocket ws, Throwable err) {
		try {
			Client.Log("Websocket - Error - %s", err.getMessage());
			ws.request(1);
			if (err.getMessage() == "Operation timed out"){
				onClose(ws, 1000, "Websocket - Error");
			}
		}
		catch (Exception e){
			Client.Log("Websocket - Error - %s", e.getMessage());
		}
	}

	public CompletionStage<Void> onText(WebSocket ws, CharSequence data, boolean isComplete) {
		textMsgCount.addAndGet(1l);
		if (data != null && data.length() > 0) {
			try {
				Client.Log("Error received: %s", data.toString());
				ws.request(1);
			}
			catch (Exception e) {
				Client.Log("Failure parsing error from server in onText(). " + e.getMessage());
				ws.request(1);
			}
		}
		else
			ws.request(1);
		return null;
	}

	public CompletionStage<Void> onBinary(WebSocket ws, ByteBuffer data, boolean isComplete) {
		dataMsgCount.addAndGet(1);
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);
		this.dataBucket.add(new Tuple<byte[], Boolean>(bytes, isComplete));
		if (isComplete) {
			this.data.add(assembleCompleteDataMessage());
		}
		ws.request(1);
		return null;
	}

	public static void Log(String message) {
		System.out.println(message);
	}

	public static void Log(String message, Object... args) {
		System.out.printf(message + "%n", args);
	}

	public void join() {
		while (!this.allReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		String[] symbols = config.getEquitiesSymbols();
		boolean tradesOnly = config.isEquitiesTradesOnly();
		for (String symbol : symbols) {
			if (!this.channels.contains(new Channel(symbol, tradesOnly))) {
				this._join(symbol, tradesOnly);
			}
		}
	}

	public void join(String symbol, boolean tradesOnly) {
		boolean t = tradesOnly || config.isEquitiesTradesOnly();
		while (!this.allReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		if (!this.channels.contains(new Channel(symbol, tradesOnly))) {
			this._join(symbol, t);
		}
	}

	public void join(String symbol) {
		this.join(symbol, false);
	}

	public void join(String[] symbols, boolean tradesOnly) {
		boolean t = tradesOnly || config.isEquitiesTradesOnly();
		while (!this.allReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		for (String symbol : symbols) {
			if (!this.channels.contains(new Channel(symbol, t))) {
				this._join(symbol, t);
			}
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
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		if (!this.channels.contains(new Channel(FirehoseChannelName, tradesOnly))) {
			this._join(FirehoseChannelName, t);
		}
	}

	public void leave() {
		for (Channel channel : this.channels) {
			this._leave(channel.symbol(), channel.tradesOnly());
		}
	}

	public void leave(String symbol) {
		for (Channel channel : this.channels) {
			if (channel.symbol() == symbol) {
				this._leave(symbol, channel.tradesOnly());
			}
		}
	}

	public void leaveLobby(){
		for (Channel channel : this.channels) {
			if (channel.symbol() == FirehoseChannelName) {
				this._leave(FirehoseChannelName, channel.tradesOnly());
			}
		}
	}

	public void leave(String[] symbols) {
		for (String symbol : symbols) {
			for (Channel channel : this.channels) {
				if (channel.symbol() == symbol) {
					this._leave(symbol, channel.tradesOnly());
				}
			}
		}
	}

	public void start() throws Exception{
		String token = this.fetchToken();
		this.initializeWebSocket(token);
		this.startThreads();
	}

	public void stop() {
		this.leave();
		try {
			Thread.sleep(1000);
		} catch (Exception e) {}
		wsLock.writeLock().lock();
		try {
			wsState.setReady(false);
		} catch (Exception e) {}
		finally {
			wsLock.writeLock().unlock();
		}
		Client.Log("Websocket - Closing");
		stopThreads(); //this sets isCancellationRequested = true so the following close event doesn't try to reconnect
		onClose(this.wsState.getWebSocket(), 1000, "Websocket - Error");
		Client.Log("Stopped");
	}
	//endregion Public Methods

	//region Private Methods
	private void processData(){
		while (!this.isCancellationRequested) {
			int count, offset, messageLength;
			byte type;
			ByteBuffer buffer, offsetBuffer;
			try {
				byte[] datum = data.poll(1, TimeUnit.SECONDS);
				if (datum != null) {
					count = datum[0];
					offset = 1;
					buffer = ByteBuffer.wrap(datum);
					buffer.position(0);
					buffer.limit(datum.length);
					for (long i = 0L; i < count; i++) {
						buffer.position(0);
						type = datum[offset];
						messageLength = datum[offset + 1];
						offsetBuffer = buffer.slice(offset, messageLength);
						switch (type) {
							case 0:
								Trade trade = Trade.parse(offsetBuffer);
								onTrade.onTrade(trade);
								break;
							case 1:
							case 2:
								Quote quote = Quote.parse(offsetBuffer);
								onQuote.onQuote(quote);
								break;
							default:
								Client.Log("Error parsing multi-part message. Type is %d", type);
								i = count;
						}
						offset += messageLength;
					}
				}
			} catch (Exception ex)
			{
				Client.Log("General Exception");
			}
		}
	}

	private void startThreads() throws Exception{
		this.isCancellationRequested = false;
		for (int i = 0; i < processDataThreads.length; i++) {
			processDataThreads[i] = new Thread(()->processData());
		}
		for (Thread thread : processDataThreads) {
			thread.start();
		}
	}

	private void stopThreads(){
		this.isCancellationRequested = true;
		try {
			Thread.sleep(1000);
		}catch (Exception e){}
		for (Thread thread : processDataThreads) {
			try {
				thread.join();
			}catch (Exception e){}
		}
	}

	private void _join(String symbol, boolean tradesOnly) {
		_join(symbol, tradesOnly, false);
	}

	private void _join(String symbol, boolean tradesOnly, boolean forceRejoin){
		Channel channel = new Channel(symbol, tradesOnly);
		if (channels.add(channel) || (channels.contains(channel) && forceRejoin)) {
			byte[] message = makeJoinMessage(tradesOnly, symbol);
			Client.Log("Websocket - Joining channel: %s (trades only = %s)", symbol, Boolean.toString(tradesOnly));
			wsState.getWebSocket().sendBinary(ByteBuffer.wrap(message), true);
		}
	}

	private void _leave(String symbol, boolean tradesOnly) {
		Channel channel = new Channel(symbol, tradesOnly);
		if (channels.remove(channel)) {
			byte[] message = makeLeaveMessage(symbol);
			Client.Log("Websocket - Leaving channel: %s (trades only = %s)", symbol, Boolean.toString(tradesOnly));
			wsState.getWebSocket().sendBinary(ByteBuffer.wrap(message), true);
		}
	}

	private void onWebSocketConnected (WebSocket ws, WebSocketState wsState) {
		for (Channel channel : channels) {
			_join(channel.symbol(), channel.tradesOnly(), true);
		}
	}

	private void initializeWebSocket(String token) {
		wsLock.writeLock().lock();
		try {
			Client.Log("Websocket - Connecting...");
			WebSocketState wsState = new WebSocketState();
			String wsUrl;
			try {
				wsUrl = this.getWebSocketUrl(token);
			} catch (Exception e) {
				Client.Log("Initialization Failure. " + e.getMessage());
				return;
			}
			URI uri = null;
			try {
				uri = new URI(wsUrl);
			} catch (URISyntaxException e) {
				Client.Log("Initialization Failure. Bad URL (%s). %s", wsUrl, e.getMessage());
				return;
			}
			HttpClient httpClient = HttpClient.newHttpClient();
			CompletableFuture<WebSocket> task =
				httpClient.newWebSocketBuilder()
				.header(HeaderMessageVersionKey, HeaderMessageVersionValue)
				.header(HeaderClientInformationKey, HeaderClientInformationValue)
				.buildAsync(uri, (WebSocket.Listener) this);
			try {
				WebSocket ws = task.get();
				this.wsState.setWebSocket(ws);
				Client.Log("Websocket - Connected");
				this.onWebSocketConnected(ws, this.wsState);
			} catch (ExecutionException e) {
				Client.Log("Initialization Failure. Could not establish connection. %s", e.getMessage());
			} catch (InterruptedException e) {
				Client.Log("Initialization Failure. Thread interrupted. %s", e.getMessage());
			}
		} finally {
			wsLock.writeLock().unlock();
		}
	}

	private boolean reconnect(){
		Client.Log("Websocket - Reconnecting...");
		if (this.wsState.isReady()) {
			return true;
		} else {
			this.wsLock.writeLock().lock();
			try {
				this.wsState.setReconnecting(true);
			} finally {
				this.wsLock.writeLock().unlock();
			}
			String token = this.fetchToken();
			initializeWebSocket(token);
			return false;
		}
	}

	private String fetchToken() {
		tLock.readLock().lock();
		try {
			tLock.readLock().unlock();
			tLock.writeLock().lock();
			try {
				doWithRetryBackoff(() -> tryGetNewToken());
				tLock.readLock().lock();
				return this.token.get().token();
			} finally {
				tLock.writeLock().unlock();
			}
		} finally {
			tLock.readLock().unlock();
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
			con.setRequestProperty(HeaderClientInformationKey, HeaderClientInformationValue);
		} catch (IOException e) {
			Client.Log("Authorization Failure. Please check your network connection. " + e.getMessage());
			return false;
		}
		try {
			con.setRequestMethod("GET");
			int status = con.getResponseCode();
			if (status == 200) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
				String token = reader.readLine();
				this.token.set(new Token(token, LocalDateTime.now()));
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
		}
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

	private void doWithRetryBackoff(BooleanSupplier callback) {
		int i = 0;
		long backoff = this.selfHealBackoffs[i];
		boolean success = callback.getAsBoolean();
		while (!success) {
			try {
				Thread.sleep(backoff);
				i = Math.min(i + 1, this.selfHealBackoffs.length - 1);
				backoff = this.selfHealBackoffs[i];
				success = callback.getAsBoolean();
			} catch (InterruptedException e) {}
			catch (Exception e) {
				//Client.Log("Error.  Retrying...");
			}
		}
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
