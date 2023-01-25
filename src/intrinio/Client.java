package intrinio;

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

class WebSocketState {
	
	private WebSocket ws;
	private boolean isReady = false;
	private boolean isReconnecting = false;
	private LocalDateTime lastReset = LocalDateTime.now();
	
	WebSocketState() {}

	WebSocket getWebSocket() {
		return ws;
	}

	void setWebSocket(WebSocket ws) {
		this.ws = ws;
	}

	boolean isReady() {
		return isReady;
	}

	void setReady(boolean isReady) {
		this.isReady = isReady;
	}

	boolean isReconnecting() {
		return isReconnecting;
	}

	void setReconnecting(boolean isReconnecting) {
		this.isReconnecting = isReconnecting;
	}

	LocalDateTime getLastReset() {
		return lastReset;
	}

	void reset() {
		this.lastReset = LocalDateTime.now();
	}
	
}

record Token (String token, LocalDateTime date) {}

record Channel (String symbol, boolean tradesOnly) {}

public class Client implements WebSocket.Listener {
	private final long[] selfHealBackoffs = {1000, 30000, 60000, 300000, 600000};
	private final ReentrantReadWriteLock tLock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock wsLock = new ReentrantReadWriteLock();
	private Config config;
	private final LinkedBlockingDeque<byte[]> data = new LinkedBlockingDeque<>();
	
	private AtomicReference<Token> token = new AtomicReference<Token>(new Token(null, LocalDateTime.now()));
	private WebSocketState wsState = null;
	private AtomicLong dataMsgCount = new AtomicLong(0l);
	private AtomicLong textMsgCount = new AtomicLong(0l);
	private HashSet<Channel> channels = new HashSet<Channel>();
	private LinkedBlockingDeque<Tuple<byte[], Boolean>> dataBucket;
	private Lock dataBucketLock;
	private OnTrade onTrade = (Trade trade) -> {};
	private OnQuote onQuote = (Quote quote) -> {};
	private Thread[] threads;	
	private boolean isCancellationRequested = false;
	
	private class Tuple<X, Y> { 
		  public final X x; 
		  public final Y y; 
		  public Tuple(X x, Y y) { 
		    this.x = x; 
		    this.y = y; 
		  } 
	}
	
	private Thread heartbeatThread = new Thread(() -> {
		while (!this.isCancellationRequested) {
			try {
				Thread.sleep(20000);
				//Client.Log("Sending heartbeat");
				wsLock.readLock().lock();
				try {
					if (wsState.isReady()) {
						wsState.getWebSocket().sendBinary(ByteBuffer.wrap(new byte[] {}), true).join();
					}
				} finally {
					wsLock.readLock().unlock();
				}				
			} catch (InterruptedException e) {}
		}
	});
	
	private byte[] getCompleteData() {
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
	
	private Runnable processData = () -> {
		while (!this.isCancellationRequested) {
			int count, offset, symbolLength;
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
						symbolLength = datum[offset + 1];
						switch (type) {
						case 0:
							offsetBuffer = buffer.slice(offset, 22 + symbolLength);
							Trade trade = Trade.parse(offsetBuffer, symbolLength);
							onTrade.onTrade(trade);
							offset += 22 + symbolLength;
							break;
						case 1:
						case 2:
							offsetBuffer = buffer.slice(offset, 18 + symbolLength);
							Quote quote = Quote.parse(offsetBuffer, symbolLength);
							onQuote.onQuote(quote);
							offset += 18 + symbolLength;
							break;
						default:
							Client.Log("Error parsing multi-part message. Type is %d", type);							
							i = count;
						}
					}
				} 
			} catch (Exception ex) 
			{
				Client.Log("General Exception");
			}			
		}
	};
	
	private void initializeThreads() throws Exception {
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(processData);
		}
	}
	
	private boolean allReady() {
		wsLock.readLock().lock();
		try {
			if (wsState == null) return false;
			boolean allReady = wsState.isReady();
			return allReady;
		} finally {
			wsLock.readLock().unlock();
		}
	}
	
	private String getAuthUrl() throws Exception {
		String authUrl;
		switch (config.getProvider()) {
		case REALTIME: authUrl = "https://realtime-mx.intrinio.com/auth?api_key=" + config.getApiKey();
			break;
		case DELAYED_SIP: authUrl = "https://realtime-delayed-sip.intrinio.com/auth?api_key=" + config.getApiKey();
			break;
		case MANUAL: authUrl = "http://" + config.getIpAddress() + "/auth?api_key=" + config.getApiKey();
			break;
		default: throw new Exception("Provider not specified!");
		}
		return authUrl;
	}
	
	private String getWebSocketUrl (String token) throws Exception {
		String wsUrl;
		switch (config.getProvider()) {
		case REALTIME: wsUrl = "wss://realtime-mx.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		case DELAYED_SIP: wsUrl = "wss://realtime-delayed-sip.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		case MANUAL: wsUrl = "ws://" + config.getIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		default: throw new Exception("Provider not specified!");
		}
		return wsUrl;
	}
	
	private void doBackoff(BooleanSupplier callback) {
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
		}
	}
	
	private BooleanSupplier trySetToken = () -> {
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
			con.setRequestProperty("Client-Information", "IntrinioRealtimeJavaSDKv4.1");
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
		}
	};
		
	private String getToken() {
		tLock.readLock().lock();
		try {
			Token token = this.token.get();
			if (LocalDateTime.now().minusDays(1).compareTo(token.date()) > 0) {
				return token.token();
			} else {
				tLock.readLock().unlock();
				tLock.writeLock().lock();
				try {
					doBackoff(this.trySetToken);
					tLock.readLock().lock();
					return this.token.get().token();
				} finally {
					tLock.writeLock().unlock();
				}
			}
		} finally {
			tLock.readLock().unlock();
		}
	}
		
	private void tryReconnect() {
		BooleanSupplier reconnectFn = () -> { 
			Client.Log("Websocket Reconnecting...");
			if (wsState.isReady()) {
				return true;
			} else {
				this.wsLock.writeLock().lock();
				try {
					wsState.setReconnecting(true);
				} finally {
					this.wsLock.writeLock().unlock();
				}
				if (wsState.getLastReset().plusDays(5).compareTo(LocalDateTime.now()) >= 0) {
					String token = this.getToken();
					resetWebSocket(token);
				} else {
					resetWebSocket(this.token.get().token());
				}
				return false;
			}
		};
		this.doBackoff(reconnectFn);
	}
		
	private void onWebSocketConnected (WebSocket ws, WebSocketState wsState) {
		if (!channels.isEmpty()) {
			String lastOnly;
			byte[] message;
			for (Channel channel : channels) {
				if (channel.tradesOnly()) {
					lastOnly = "true";
				} else {
					lastOnly = "false";
				}
				message = makeJoinMessage(channel.tradesOnly(), channel.symbol());
				Client.Log("Websocket - Joining channel: %s (trades only = %s)",  channel.symbol(), lastOnly);
				wsState.getWebSocket().sendBinary(ByteBuffer.wrap(message), true);
			}
		}
	}
	
	public CompletionStage<Void> onClose(WebSocket ws, int status, String reason) {
		wsLock.readLock().lock();
		try {
			if (!wsState.isReconnecting()) {
				Client.Log("Websocket Closed");
				wsLock.readLock().unlock();
				wsLock.writeLock().lock();
				try {
					wsState.setReady(false);
				} finally {
					wsLock.writeLock().unlock();
				}
				if (!this.isCancellationRequested) {
					new Thread(() -> {
						this.tryReconnect();
					}).start();
				}
			}
		} finally {
			wsLock.readLock().unlock();
		}
		return null;
	}
		
	public void onError(WebSocket ws, Throwable err) {
		Client.Log("Websocket - Error - %s", err.getMessage());
		ws.request(1);
	}
		
	public CompletionStage<Void> onText(WebSocket ws, CharSequence data, boolean isComplete) {
		textMsgCount.addAndGet(1l);
		Client.Log("Error received: %s", data.toString());
		ws.request(1);
		return null;
	}
		
	public CompletionStage<Void> onBinary(WebSocket ws, ByteBuffer data, boolean isComplete) {
		dataMsgCount.addAndGet(1);
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);
		this.dataBucket.add(new Tuple<byte[], Boolean>(bytes, isComplete));
		if (isComplete) {
			this.data.add(getCompleteData());
		}
		ws.request(1);
		return null;
	}
		
	private void resetWebSocket(String token) {
		Client.Log("Websocket Resetting");
		String wsUrl;
		try {
			wsUrl = this.getWebSocketUrl(token);
		} catch (Exception e) {
			Client.Log("Reset Failure. " + e.getMessage());
			return;
		}
		URI uri = null;
		try {
			uri = new URI(wsUrl);
		} catch (URISyntaxException e) {
			Client.Log("Reset Failure. Bad URL (%s). %s", wsUrl, e.getMessage());
			return;
		}
		HttpClient client = HttpClient.newHttpClient();
		CompletableFuture<WebSocket> task = client.newWebSocketBuilder().buildAsync(uri, (WebSocket.Listener) this);
		try {
			WebSocket ws = task.get();
			Client.Log("Websocket Reset");
			wsLock.writeLock().lock();
			try {
				wsState.setWebSocket(ws);
				wsState.reset();
				wsState.setReady(true);
				wsState.setReconnecting(false);
			} finally {
				wsLock.writeLock().unlock();
			}
			this.onWebSocketConnected(ws, wsState);
		} catch (ExecutionException e) {
			Client.Log("Reset Failure. Could not establish connection. %s", e.getMessage());
			return;
		} catch (InterruptedException e) {
			Client.Log("Reset Failure. Thread interrupted. %s", e.getMessage());
			return;
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
			HttpClient client = HttpClient.newHttpClient();
			CompletableFuture<WebSocket> task = client.newWebSocketBuilder().buildAsync(uri, (WebSocket.Listener) this);
			try {
				WebSocket ws = task.get();
				Client.Log("Websocket Connected");
				wsState.setWebSocket(ws);
				this.wsState = wsState;
				wsState.setReady(true);
				wsState.setReconnecting(false);
				if (!heartbeatThread.isAlive()) {
					heartbeatThread.start();
				}
				for (Thread thread : threads) {
					if (!thread.isAlive()) {
						thread.start();
					}
				}
				this.onWebSocketConnected(ws, wsState);
			} catch (ExecutionException e) {
				Client.Log("Initialization Failure. Could not establish connection. %s", e.getMessage());
				return;
			} catch (InterruptedException e) {
				Client.Log("Initialization Failure. Thread interrupted. %s", e.getMessage());
				return;
			}
		} finally {
			wsLock.writeLock().unlock();
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
	
	private void _join(String symbol, boolean tradesOnly) {
		String lastOnly;
		if (tradesOnly) {
			lastOnly = "true";
		} else {
			lastOnly = "false";
		}
		Channel channel = new Channel(symbol, tradesOnly);
		if (channels.add(channel)) {
			byte[] message = makeJoinMessage(tradesOnly, symbol);
			Client.Log("Websocket - Joining channel: %s (trades only = %s)", symbol, lastOnly);
			wsState.getWebSocket().sendBinary(ByteBuffer.wrap(message), true);
		}
	}
		
	private void _leave(String symbol, boolean tradesOnly) {
		String lastOnly;
		if (tradesOnly) {
			lastOnly = "true";
		} else {
			lastOnly = "false";
		}
		Channel channel = new Channel(symbol, tradesOnly);
		if (channels.remove(channel)) {
			byte[] message = makeLeaveMessage(symbol);
			Client.Log("Websocket - Leaving channel: %s (trades only = %s)", symbol, lastOnly);
			wsState.getWebSocket().sendBinary(ByteBuffer.wrap(message), true);
		}
	}
		
	protected void finalize() {
		if (!this.isCancellationRequested) {
			this.stop();
		}
	}
		
	public Client() {
		try {
			config = Config.load();
			threads = new Thread[config.getNumThreads()];
			dataBucketLock = new ReentrantLock();
			dataBucket = new LinkedBlockingDeque<Tuple<byte[], Boolean>>();
			this.initializeThreads();
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());
		}
		String token = this.getToken();
		this.initializeWebSocket(token);
	}
	
	public Client(Config config) {
		try {
			this.config = config;
			threads = new Thread[config.getNumThreads()];
			dataBucketLock = new ReentrantLock();
			dataBucket = new LinkedBlockingDeque<Tuple<byte[], Boolean>>();
			this.initializeThreads();
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());
		}
		String token = this.getToken();
		this.initializeWebSocket(token);
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
		
	public void join() {
		while (!this.allReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		String[] symbols = config.getSymbols();
		boolean tradesOnly = config.isTradesOnly();
		for (String symbol : symbols) {
			if (!this.channels.contains(new Channel(symbol, tradesOnly))) {
				this._join(symbol, tradesOnly);
			}
		}
	}
		
	public void join(String symbol, boolean tradesOnly) {
		boolean t = tradesOnly || config.isTradesOnly();
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
		boolean t = tradesOnly || config.isTradesOnly();
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
		
	public void leave(String[] symbols) {
		for (String symbol : symbols) {
			for (Channel channel : this.channels) {
				if (channel.symbol() == symbol) {
					this._leave(symbol, channel.tradesOnly());
				}
			}
		}
	}
		
	public void stop() {
		this.leave();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {}
		wsLock.writeLock().lock();
		try {
			wsState.setReady(false);
		} finally {
			wsLock.writeLock().unlock();
		}
		this.isCancellationRequested = true;
		Client.Log("Websocket Closing");
		wsState.getWebSocket().sendClose(1000, "Client closed");
		try {
			this.heartbeatThread.join();
			for (Thread thread : threads) {
				thread.join();
			}
		} catch (InterruptedException e) {}
		Client.Log("Stopped");
	}
	
	private int getDataSize() {
		return dataBucket.size();
	}
	
	public String getStats() {
		return String.format("Data Messages = %d, Text Messages = %d, Queue Depth = %d", this.dataMsgCount.get(), this.textMsgCount.get(), getDataSize());
	}
		
	public static void Log(String message) {
		System.out.println(message);
	}
		
	public static void Log(String message, Object... args) {
		System.out.printf(message + "%n", args);
	}
}
