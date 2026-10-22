package intrinio.realtime.options;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Package-private lifecycle support so the public Client API remains unchanged. */
final class ClientConnectionManager {
	@FunctionalInterface
	interface TokenFetcher { String fetch() throws Exception; }

	@FunctionalInterface
	interface SocketUrlFactory { URI create(String token) throws Exception; }

	@FunctionalInterface
	interface SocketConnector { CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener); }

	interface Callbacks {
		void onSessionStarting() throws Exception;
		void onSessionStopping();
		default void onDisconnected() { }
		void onConnected(WebSocket webSocket) throws Exception;
		void onBinary(WebSocket webSocket, ByteBuffer data, boolean isComplete);
		void onText(WebSocket webSocket, CharSequence data, boolean isComplete);
		void log(String message, Object... args);
	}

	private enum Lifecycle { STOPPED, STARTING, STARTED, STOPPING }
	private static final int MAX_CLOSE_TIMEOUT_MS = 5_000;
	private static final int WORKER_JOIN_TIMEOUT_MS = 10_000;

	private final Object lifecycleLock = new Object();
	private final Object socketLock = new Object();
	private final TokenFetcher tokenFetcher;
	private final SocketUrlFactory socketUrlFactory;
	private final SocketConnector socketConnector;
	private final Callbacks callbacks;
	private final long[] backoffs;
	private final int connectTimeoutMs;
	private Lifecycle lifecycle = Lifecycle.STOPPED;
	private long generation = 0;
	private boolean ready = false;
	private WebSocket currentSocket;
	private volatile Thread reconnectThread;
	private volatile CompletableFuture<WebSocket> connectFuture;

	ClientConnectionManager(TokenFetcher tokenFetcher, SocketUrlFactory socketUrlFactory,
		SocketConnector socketConnector, Callbacks callbacks, long[] backoffs, int connectTimeoutMs) {
		this.tokenFetcher = tokenFetcher;
		this.socketUrlFactory = socketUrlFactory;
		this.socketConnector = socketConnector;
		this.callbacks = callbacks;
		this.backoffs = Arrays.copyOf(backoffs, backoffs.length);
		this.connectTimeoutMs = connectTimeoutMs;
	}

	void start() throws Exception {
		long session;
		boolean beginSession = false;
		synchronized (lifecycleLock) {
			while (lifecycle == Lifecycle.STOPPING) lifecycleLock.wait();
			if (lifecycle == Lifecycle.STOPPED) {
				generation++;
				ready = false;
				lifecycle = Lifecycle.STARTING;
				beginSession = true;
			}
			session = generation;
		}
		if (beginSession) {
			try {
				callbacks.onSessionStarting();
			} catch (Exception exception) {
				synchronized (lifecycleLock) {
					if (generation == session) lifecycle = Lifecycle.STOPPED;
					lifecycleLock.notifyAll();
				}
				throw exception;
			}
			startReconnectWorker(session);
		}
		synchronized (lifecycleLock) {
			while (generation == session && lifecycle != Lifecycle.STARTED) {
				if (lifecycle == Lifecycle.STOPPED || lifecycle == Lifecycle.STOPPING)
					throw new InterruptedException("Client stopped before a connection became ready");
				lifecycleLock.wait();
			}
			if (generation != session || lifecycle != Lifecycle.STARTED)
				throw new InterruptedException("Client session was replaced before a connection became ready");
		}
	}

	void stop() {
		WebSocket socket;
		Thread worker;
		CompletableFuture<WebSocket> pending;
		synchronized (lifecycleLock) {
			while (lifecycle == Lifecycle.STOPPING) {
				try { lifecycleLock.wait(); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
			}
			if (lifecycle == Lifecycle.STOPPED) return;
			lifecycle = Lifecycle.STOPPING;
			ready = false;
			generation++;
			pending = connectFuture;
			worker = reconnectThread;
			lifecycleLock.notifyAll();
		}
		try {
			try { callbacks.onSessionStopping(); }
			catch (Exception exception) { callbacks.log("Websocket - Error stopping processors: %s", exception.getMessage()); }
			if (pending != null) pending.cancel(true);
			if (worker != null && worker != Thread.currentThread()) worker.interrupt();
			synchronized (socketLock) { socket = currentSocket; currentSocket = null; }
			closeOrAbort(socket);
			if (worker != null && worker != Thread.currentThread()) {
				try { worker.join(WORKER_JOIN_TIMEOUT_MS); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
			}
		} finally {
			synchronized (lifecycleLock) {
				reconnectThread = null;
				connectFuture = null;
				lifecycle = Lifecycle.STOPPED;
				lifecycleLock.notifyAll();
			}
		}
	}

	boolean isReady() {
		synchronized (lifecycleLock) { return ready && lifecycle == Lifecycle.STARTED; }
	}

	void awaitReady() {
		synchronized (lifecycleLock) {
			while (!ready && lifecycle != Lifecycle.STOPPING) {
				try { lifecycleLock.wait(1_000L); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
			}
		}
	}

	WebSocket currentSocket() { synchronized (socketLock) { return currentSocket; } }
	boolean isCurrent(WebSocket webSocket) { synchronized (socketLock) { return webSocket != null && currentSocket == webSocket; } }
	CompletionStage<Void> onClose(WebSocket webSocket, int statusCode, String reason) {
		handleDisconnect(webSocket, false);
		return CompletableFuture.completedFuture(null);
	}
	void onError(WebSocket webSocket, Throwable error) {
		callbacks.log("Websocket - Error - %s", error == null ? "Unknown error" : error.getMessage());
		handleDisconnect(webSocket, true);
	}

	private void startReconnectWorker(long session) {
		Thread worker;
		synchronized (lifecycleLock) {
			if (!isSessionActiveLocked(session) || ready) return;
			if (reconnectThread != null && reconnectThread.isAlive()) return;
			worker = new Thread(() -> runReconnectLoop(session), "intrinio-options-reconnect");
			worker.setDaemon(true);
			reconnectThread = worker;
		}
		worker.start();
	}

	private void runReconnectLoop(long session) {
		int backoffIndex = 0;
		try {
			while (isSessionActive(session) && !isReady()) {
				boolean connected = false;
				try {
					callbacks.log("Websocket - Authorizing...");
					String token = tokenFetcher.fetch();
					if (token != null && !token.isBlank() && isSessionActive(session))
						connected = connectOnce(session, socketUrlFactory.create(token));
				} catch (Exception exception) {
					callbacks.log("Websocket - Connection attempt failed: %s", exception.getMessage());
				}
				if (connected || !isSessionActive(session)) return;
				long backoff = backoffs[Math.min(backoffIndex, backoffs.length - 1)];
				backoffIndex = Math.min(backoffIndex + 1, backoffs.length - 1);
				if (!waitForRetry(session, backoff)) return;
			}
		} finally {
			boolean restart;
			synchronized (lifecycleLock) {
				if (reconnectThread == Thread.currentThread()) reconnectThread = null;
				restart = isSessionActiveLocked(session) && !ready;
			}
			if (restart) startReconnectWorker(session);
		}
	}

	private boolean connectOnce(long session, URI uri) {
		callbacks.log("Websocket - Connecting...");
		AttemptListener listener = new AttemptListener(session);
		CompletableFuture<WebSocket> task;
		try { task = socketConnector.connect(uri, listener); }
		catch (Exception exception) {
			callbacks.log("Initialization Failure. Could not establish connection. %s", exception.getMessage());
			return false;
		}
		synchronized (lifecycleLock) {
			if (!isSessionActiveLocked(session)) { task.cancel(true); return false; }
			connectFuture = task;
		}
		task.whenComplete((webSocket, error) -> {
			if (webSocket != null && (!isSessionActive(session) || task.isCancelled())) abortSocket(webSocket);
		});
		WebSocket webSocket;
		try { webSocket = task.get(connectTimeoutMs, TimeUnit.MILLISECONDS); }
		catch (TimeoutException exception) {
			callbacks.log("Initialization Failure. Websocket handshake timed out"); task.cancel(true); return false;
		} catch (ExecutionException exception) {
			Throwable cause = exception.getCause() == null ? exception : exception.getCause();
			callbacks.log("Initialization Failure. Could not establish connection. %s", cause.getMessage()); return false;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt(); task.cancel(true); return false;
		} finally {
			if (connectFuture == task) connectFuture = null;
		}
		if (!isSessionActive(session) || listener.closed.get() || webSocket.isInputClosed() || webSocket.isOutputClosed()) {
			abortSocket(webSocket); return false;
		}
		if (!isSessionActive(session)) { abortSocket(webSocket); return false; }
		synchronized (socketLock) {
			currentSocket = webSocket;
		}
		try { callbacks.onConnected(webSocket); webSocket.request(1); }
		catch (Exception exception) {
			callbacks.log("Websocket - Failed while restoring subscriptions: %s", exception.getMessage());
			clearAndAbort(webSocket); return false;
		}
		synchronized (lifecycleLock) {
			if (!isSessionActiveLocked(session) || listener.closed.get() || !isCurrent(webSocket)) {
				clearAndAbort(webSocket); return false;
			}
			ready = true;
			lifecycle = Lifecycle.STARTED;
			lifecycleLock.notifyAll();
		}
		callbacks.log("Websocket - Connected");
		return true;
	}

	private void handleDisconnect(WebSocket webSocket, boolean abort) {
		long session;
		synchronized (lifecycleLock) {
			if (lifecycle == Lifecycle.STOPPED || lifecycle == Lifecycle.STOPPING) return;
			session = generation;
		}
		synchronized (socketLock) {
			if (currentSocket != webSocket) return;
			currentSocket = null;
		}
		if (abort && webSocket != null) abortSocket(webSocket);
		try { callbacks.onDisconnected(); }
		catch (Exception exception) { callbacks.log("Websocket - Error handling disconnect: %s", exception.getMessage()); }
		synchronized (lifecycleLock) {
			if (!isSessionActiveLocked(session)) return;
			ready = false;
			lifecycle = Lifecycle.STARTING;
			lifecycleLock.notifyAll();
		}
		callbacks.log("Websocket - Closed");
		startReconnectWorker(session);
	}

	private boolean waitForRetry(long session, long milliseconds) {
		synchronized (lifecycleLock) {
			if (!isSessionActiveLocked(session)) return false;
			try { lifecycleLock.wait(milliseconds); }
			catch (InterruptedException exception) { Thread.currentThread().interrupt(); return false; }
			return isSessionActiveLocked(session);
		}
	}

	private boolean isSessionActive(long session) { synchronized (lifecycleLock) { return isSessionActiveLocked(session); } }
	private boolean isSessionActiveLocked(long session) {
		return generation == session && lifecycle != Lifecycle.STOPPED && lifecycle != Lifecycle.STOPPING;
	}
	private void clearAndAbort(WebSocket webSocket) {
		synchronized (socketLock) { if (currentSocket == webSocket) currentSocket = null; }
		abortSocket(webSocket);
	}
	private void abortSocket(WebSocket webSocket) {
		try { webSocket.abort(); }
		catch (Exception exception) { callbacks.log("Websocket - Error aborting socket: %s", exception.getMessage()); }
	}
	private void closeOrAbort(WebSocket webSocket) {
		if (webSocket == null) return;
		try {
			if (!webSocket.isOutputClosed())
				webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client requested close")
					.get(Math.min(connectTimeoutMs, MAX_CLOSE_TIMEOUT_MS), TimeUnit.MILLISECONDS);
		} catch (Exception exception) { abortSocket(webSocket); }
	}

	private final class AttemptListener implements WebSocket.Listener {
		private final long session;
		private final AtomicBoolean closed = new AtomicBoolean(false);
		private AttemptListener(long session) { this.session = session; }
		@Override public void onOpen(WebSocket webSocket) { }
		@Override public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean isComplete) {
			if (isCurrent(webSocket) && isSessionActive(session)) callbacks.onBinary(webSocket, data, isComplete);
			if (isCurrent(webSocket)) webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}
		@Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean isComplete) {
			if (isCurrent(webSocket) && isSessionActive(session)) callbacks.onText(webSocket, data, isComplete);
			if (isCurrent(webSocket)) webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}
		@Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			closed.set(true); handleDisconnect(webSocket, false); return CompletableFuture.completedFuture(null);
		}
		@Override public void onError(WebSocket webSocket, Throwable error) {
			closed.set(true); ClientConnectionManager.this.onError(webSocket, error);
		}
	}
}
