package intrinio.realtime.options;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;

public class Client implements WebSocket.Listener {
	//region Final data members
	private final String FIREHOSE_CHANNEL = "$FIREHOSE";
	private final long[] selfHealBackoffs = {1000, 30000, 60000, 300000, 600000};
	private final int TRADE_MESSAGE_SIZE = 72; //61 used + 11 pad
	private final int QUOTE_MESSAGE_SIZE = 52; //48 used + 4 pad
	private final int REFRESH_MESSAGE_SIZE = 52; //44 used + 8 pad
	private final int UNUSUAL_ACTIVITY_MESSAGE_SIZE = 74; //62 used + 12 pad
	private final ReentrantReadWriteLock tLock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock wsLock = new ReentrantReadWriteLock();
	private final LinkedBlockingDeque<byte[]> data = new LinkedBlockingDeque<>();
	private final HashSet<String> channels = new HashSet<String>();
	private final Lock dataBucketLock = new ReentrantLock();
	private final LinkedBlockingDeque<Tuple<byte[], Boolean>> dataBucket = new LinkedBlockingDeque<Tuple<byte[], Boolean>>();
	private final WebSocketState wsState = new WebSocketState();
	private final String Version = "IntrinioRealtimeOptionsJavaSDKv7.1";
	//endregion Final data members

	//region Data Members
	private Config config;
	private Thread[] processDataThreads;
	private boolean isCancellationRequested = false;
	private AtomicReference<Token> token = new AtomicReference<Token>(new Token(null, LocalDateTime.now()));
	private AtomicLong dataMsgCount = new AtomicLong(0l);
	private AtomicLong textMsgCount = new AtomicLong(0l);
	private OnTrade onTrade = (Trade trade) -> {};
	private boolean useOnTrade = false;
	private OnQuote onQuote = (Quote quote) -> {};
	private boolean useOnQuote = false;
	private OnRefresh onRefresh = (Refresh r) -> {};
	private boolean useOnRefresh = false;
	private OnUnusualActivity onUnusualActivity = (UnusualActivity ua) -> {};
	private boolean useOnUnusualActivity = false;
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

	protected void finalize() {
		try { this.stop(); } catch (Exception e){}
	}
	//endregion Constructors

	//region Public Get Set
	public String getStats() {
		return String.format("Data Messages = %d, Text Messages = %d, Queue Depth = %d", this.dataMsgCount.get(), this.textMsgCount.get(), getDataSize());
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
		return dataBucket.size();
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
			case MANUAL: wsUrl = "ws://" + config.getOptionsIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token + (this.config.isDelayed() ? "&delayed=true" : "");
				break;
			default: throw new Exception("Provider not specified!");
		}
		return wsUrl;
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
			if (err.getMessage() == "Connection reset"){
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

	public static void Log(String message, Object... args) {
		System.out.printf(message + "%n", args);
	}

	public static void Log(String message) {System.out.println(message);}

	public void join(String symbol) {
		if (!symbol.isBlank()) {
			while (!this.isAllReady()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
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
		if (channels.contains(FIREHOSE_CHANNEL)) {
			Client.Log("This client has already joined the lobby channel");
		} else {
			while (!this.isAllReady()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
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
		for (String channel : this.channels) {
			this.leave(channel);
		}
	}

	public void leaveLobby() {
		if (channels.contains(FIREHOSE_CHANNEL)) this.leave(FIREHOSE_CHANNEL);
	}

	public void start() throws Exception {
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
			try {
				byte[] datum = data.poll(1, TimeUnit.SECONDS);
				if (datum != null) {
					int count = datum[0];
					int offset = 1;
					ByteBuffer buffer = ByteBuffer.wrap(datum);
					buffer.position(0);
					buffer.limit(datum.length);
					for (long i = 0L; i < count; i++) {
						buffer.position(0);
						byte type = datum[offset + 22];
						ByteBuffer offsetBuffer;
						if (type == 1) {
							offsetBuffer = buffer.slice(offset, QUOTE_MESSAGE_SIZE);
							Quote quote = Quote.parse(offsetBuffer);
							offset += QUOTE_MESSAGE_SIZE;
							if (useOnQuote) onQuote.onQuote(quote);
						}
						else if (type == 0) {
							offsetBuffer = buffer.slice(offset, TRADE_MESSAGE_SIZE);
							Trade trade = Trade.parse(offsetBuffer);
							offset += TRADE_MESSAGE_SIZE;
							if (useOnTrade) onTrade.onTrade(trade);
						}
						else if (type > 2) {
							offsetBuffer = buffer.slice(offset, UNUSUAL_ACTIVITY_MESSAGE_SIZE);
							UnusualActivity ua = UnusualActivity.parse(offsetBuffer);
							offset += UNUSUAL_ACTIVITY_MESSAGE_SIZE;
							if (useOnUnusualActivity) onUnusualActivity.onUnusualActivity(ua);
						}
						else if (type == 2) {
							offsetBuffer = buffer.slice(offset, REFRESH_MESSAGE_SIZE);
							Refresh r = Refresh.parse(offsetBuffer);
							offset += REFRESH_MESSAGE_SIZE;
							if (useOnRefresh) onRefresh.onRefresh(r);
						}
						else {
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
	}

	private void startThreads() throws Exception {
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

	private void _join(String symbol) {
		this._join(symbol, false);
	}

	private void _join(String symbol, boolean forceRejoin) {
		String translatedSymbol = translateContractToServerFormat(symbol);
		String standardFormatSymbol = translateContractToStandardFormat(translatedSymbol);
		if (channels.add(translatedSymbol) || (channels.contains(translatedSymbol) && forceRejoin)) {
			byte optionMask = getChannelOptionMask();
			byte[] bytes = new byte[translatedSymbol.length() + 2];
			bytes[0] = (byte) 74;
			bytes[1] = optionMask;
			translatedSymbol.getBytes(StandardCharsets.US_ASCII);
			System.arraycopy(translatedSymbol.getBytes(StandardCharsets.US_ASCII), 0, bytes, 2, translatedSymbol.length());

			Client.Log("Websocket - Joining channel: %s (Trades: %s, Quotes: %s, Refreshes: %s, Unusual Activity: %s)", standardFormatSymbol, useOnTrade, useOnQuote, useOnRefresh, useOnUnusualActivity);
			ByteBuffer message = ByteBuffer.wrap(bytes);
			wsState.getWebSocket().sendBinary(message, true);
		}
	}

	private void _leave(String symbol) {
		String translatedSymbol = translateContractToServerFormat(symbol);
		String standardFormatSymbol = translateContractToStandardFormat(translatedSymbol);
		if (channels.remove(translatedSymbol)) {
			byte optionMask = getChannelOptionMask();
			byte[] bytes = new byte[translatedSymbol.length() + 2];
			bytes[0] = (byte) 76;
			bytes[1] = optionMask;
			translatedSymbol.getBytes(StandardCharsets.US_ASCII);
			System.arraycopy(translatedSymbol.getBytes(StandardCharsets.US_ASCII), 0, bytes, 2, translatedSymbol.length());

			Client.Log("Websocket - leaving channel: %s (Trades: %s, Quotes: %s, Refreshes: %s, Unusual Activity: %s)", standardFormatSymbol, useOnTrade, useOnQuote, useOnRefresh, useOnUnusualActivity);
			ByteBuffer message = ByteBuffer.wrap(bytes);
			wsState.getWebSocket().sendBinary(message, true);
		}
	}

	private void onWebSocketConnected (WebSocket ws, WebSocketState wsState) {
		for (String channel : channels) {
			_join(channel, true);
		}
	}

	private void initializeWebSocket(String token) {
		wsLock.writeLock().lock();
		try {
			Client.Log("Websocket - Connecting...");
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
			CompletableFuture<WebSocket> task = httpClient.newWebSocketBuilder().buildAsync(uri, (WebSocket.Listener) this);
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

	private String fetchToken() {
		tLock.readLock().lock();
		try {
			Token token = this.token.get();
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
			con.setRequestProperty("Client-Information", Version);
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
