package intrinio.realtime.equities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import intrinio.realtime.test.FakeWebSocket;
import java.io.IOException;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientConnectionManagerTest {
	private static final long[] FAST_BACKOFFS = {5L, 5L, 5L};
	private static final int FAST_CONNECT_TIMEOUT_MS = 25;

	@Test
	void failedInitialConnectionRetriesUntilReady() throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		ClientConnectionManager manager = manager((uri, listener) -> {
			if (attempts.incrementAndGet() == 1)
				return CompletableFuture.failedFuture(new IllegalStateException("upgrade failed"));
			return CompletableFuture.completedFuture(new FakeWebSocket().open(listener));
		}, new TestCallbacks());

		manager.start();

		assertTrue(manager.isReady());
		assertEquals(2, attempts.get());
		manager.stop();
	}

	@Test
	void authorizationFailureRetriesUntilReady() throws Exception {
		AtomicInteger authAttempts = new AtomicInteger();
		AtomicInteger connectAttempts = new AtomicInteger();
		ClientConnectionManager manager = new ClientConnectionManager(
			() -> authAttempts.incrementAndGet() == 1 ? null : "token",
			token -> URI.create("ws://localhost/socket?token=" + token),
			(uri, listener) -> {
				connectAttempts.incrementAndGet();
				return CompletableFuture.completedFuture(new FakeWebSocket().open(listener));
			},
			new TestCallbacks(),
			FAST_BACKOFFS,
			FAST_CONNECT_TIMEOUT_MS);

		manager.start();

		assertEquals(2, authAttempts.get());
		assertEquals(1, connectAttempts.get());
		assertTrue(manager.isReady());
		manager.stop();
	}

	@Test
	void hungInitialHandshakeTimesOutAndRetries() throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		ClientConnectionManager manager = manager((uri, listener) -> {
			if (attempts.incrementAndGet() == 1) return new CompletableFuture<>();
			return CompletableFuture.completedFuture(new FakeWebSocket().open(listener));
		}, new TestCallbacks());

		manager.start();

		assertTrue(manager.isReady());
		assertEquals(2, attempts.get());
		manager.stop();
	}

	@Test
	void immediateCloseAndLaterDisconnectBothRecover() throws Exception {
		List<FakeWebSocket> sockets = new ArrayList<>();
		AtomicInteger attempts = new AtomicInteger();
		ClientConnectionManager manager = manager((uri, listener) -> {
			FakeWebSocket socket = new FakeWebSocket().open(listener);
			sockets.add(socket);
			if (attempts.incrementAndGet() == 1) socket.closeFromServer();
			return CompletableFuture.completedFuture(socket);
		}, new TestCallbacks());

		manager.start();
		assertEquals(2, attempts.get());
		FakeWebSocket connected = sockets.get(1);
		connected.closeFromServer();
		waitUntil(() -> attempts.get() == 3 && manager.isReady(), Duration.ofSeconds(1));

		assertTrue(manager.isReady());
		manager.onClose(connected, WebSocket.NORMAL_CLOSURE, "stale close");
		assertTrue(manager.isReady(), "a close from the replaced socket must be ignored");
		manager.stop();
	}

	@Test
	void websocketErrorTriggersRecoveryRegardlessOfMessageText() throws Exception {
		List<FakeWebSocket> sockets = new ArrayList<>();
		AtomicInteger attempts = new AtomicInteger();
		ClientConnectionManager manager = manager((uri, listener) -> {
			attempts.incrementAndGet();
			FakeWebSocket socket = new FakeWebSocket().open(listener);
			sockets.add(socket);
			return CompletableFuture.completedFuture(socket);
		}, new TestCallbacks());

		manager.start();
		sockets.get(0).error(new IOException("arbitrary network failure"));
		waitUntil(() -> attempts.get() == 2 && manager.isReady(), Duration.ofSeconds(1));

		assertTrue(sockets.get(0).wasAborted());
		assertTrue(manager.isReady());
		manager.stop();
	}

	@Test
	void sendFailureWhileRestoringSubscriptionsDoesNotKillReconnectLoop() throws Exception {
		AtomicInteger connectedCallbacks = new AtomicInteger();
		TestCallbacks callbacks = new TestCallbacks() {
			@Override public void onConnected(WebSocket webSocket) {
				if (connectedCallbacks.incrementAndGet() == 1) throw new IllegalStateException("send failed");
			}
		};
		AtomicInteger attempts = new AtomicInteger();
		ClientConnectionManager manager = manager((uri, listener) -> {
			attempts.incrementAndGet();
			return CompletableFuture.completedFuture(new FakeWebSocket().open(listener));
		}, callbacks);

		manager.start();

		assertEquals(2, attempts.get());
		assertTrue(manager.isReady());
		manager.stop();
	}

	@Test
	void stopCancelsHungStartAndHungClose() throws Exception {
		CompletableFuture<WebSocket> hungConnect = new CompletableFuture<>();
		TestCallbacks callbacks = new TestCallbacks();
		ClientConnectionManager manager = manager((uri, listener) -> hungConnect, callbacks);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<?> start = executor.submit(() -> {
			try { manager.start(); }
			catch (Exception exception) { throw new RuntimeException(exception); }
		});

		Thread.sleep(10L);
		manager.stop();
		assertThrows(ExecutionException.class, () -> start.get(1, TimeUnit.SECONDS));
		assertFalse(manager.isReady());
		executor.shutdownNow();

		FakeWebSocket socket = new FakeWebSocket();
		socket.setHangOnClose(true);
		ClientConnectionManager second = manager((uri, listener) ->
			CompletableFuture.completedFuture(socket.open(listener)), new TestCallbacks());
		second.start();
		long startedAt = System.nanoTime();
		second.stop();
		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
		assertTrue(elapsedMs < 1_000L);
		assertTrue(socket.wasAborted());
	}

	@Test
	void stopThenStartCreatesANewSession() throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		TestCallbacks callbacks = new TestCallbacks();
		ClientConnectionManager manager = manager((uri, listener) -> {
			attempts.incrementAndGet();
			return CompletableFuture.completedFuture(new FakeWebSocket().open(listener));
		}, callbacks);

		manager.start();
		manager.start();
		assertEquals(1, attempts.get(), "concurrent/idempotent start must reuse the ready session");
		manager.stop();
		manager.stop();
		manager.start();

		assertEquals(2, attempts.get());
		assertEquals(2, callbacks.starts.get());
		manager.stop();
	}

	private static ClientConnectionManager manager(
		ClientConnectionManager.SocketConnector connector,
		ClientConnectionManager.Callbacks callbacks) {
		return new ClientConnectionManager(
			() -> "token",
			token -> URI.create("ws://localhost/socket?token=" + token),
			connector,
			callbacks,
			FAST_BACKOFFS,
			FAST_CONNECT_TIMEOUT_MS);
	}

	private static void waitUntil(Check check, Duration timeout) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (!check.get() && System.nanoTime() < deadline) Thread.sleep(5L);
		assertTrue(check.get(), "condition did not become true before timeout");
	}

	@FunctionalInterface
	private interface Check { boolean get(); }

	private static class TestCallbacks implements ClientConnectionManager.Callbacks {
		final AtomicInteger starts = new AtomicInteger();
		@Override public void onSessionStarting() { starts.incrementAndGet(); }
		@Override public void onSessionStopping() { }
		@Override public void onConnected(WebSocket webSocket) { }
		@Override public void onBinary(WebSocket webSocket, ByteBuffer data, boolean isComplete) { }
		@Override public void onText(WebSocket webSocket, CharSequence data, boolean isComplete) { }
		@Override public void log(String message, Object... args) { }
	}
}
