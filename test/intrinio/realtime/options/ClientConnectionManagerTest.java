package intrinio.realtime.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import intrinio.realtime.test.FakeWebSocket;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientConnectionManagerTest {
	@Test
	void optionsClientRetriesConnectionFailure() throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		ClientConnectionManager manager = new ClientConnectionManager(
			() -> "token",
			token -> URI.create("ws://localhost/socket?token=" + token),
			(uri, listener) -> {
				if (attempts.incrementAndGet() == 1)
					return CompletableFuture.failedFuture(new IllegalStateException("unavailable"));
				return CompletableFuture.completedFuture(new FakeWebSocket().open(listener));
			},
			new ClientConnectionManager.Callbacks() {
				@Override public void onSessionStarting() { }
				@Override public void onSessionStopping() { }
				@Override public void onConnected(WebSocket webSocket) { }
				@Override public void onBinary(WebSocket webSocket, ByteBuffer data, boolean isComplete) { }
				@Override public void onText(WebSocket webSocket, CharSequence data, boolean isComplete) { }
				@Override public void log(String message, Object... args) { }
			},
			new long[]{5L},
			25);

		manager.start();

		assertEquals(2, attempts.get());
		assertTrue(manager.isReady());
		manager.stop();
	}
}
