package intrinio.realtime.equities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import intrinio.realtime.test.FakeWebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ClientPriorityIntegrationTest {
	@Test
	void tradeCallbackRunsBeforeEarlierQuoteInTheSameBatch() throws Exception {
		Config config = new Config("key", Provider.MANUAL, "localhost", new String[0], false, 1, false);
		FakeWebSocket socket = new FakeWebSocket();
		List<String> callbacks = new CopyOnWriteArrayList<>();
		CountDownLatch handled = new CountDownLatch(2);
		Client client = new Client(
			trade -> { callbacks.add("trade"); handled.countDown(); },
			quote -> { callbacks.add("quote"); handled.countDown(); },
			config,
			() -> "token",
			(uri, listener) -> CompletableFuture.completedFuture(socket.open(listener)),
			new long[]{5L},
			25);

		client.start();
		socket.emitBinary(batch(equitiesMessage((byte) 1, 24), equitiesMessage((byte) 0, 28)));

		assertTrue(handled.await(1, TimeUnit.SECONDS));
		assertEquals(List.of("trade", "quote"), callbacks);
		client.stop();
	}

	private static byte[] equitiesMessage(byte type, int length) {
		byte[] message = new byte[length];
		message[0] = type;
		message[1] = (byte) length;
		message[2] = 1;
		message[3] = 'A';
		return message;
	}

	private static byte[] batch(byte[]... messages) {
		int size = 1;
		for (byte[] message : messages) size += message.length;
		byte[] batch = new byte[size];
		batch[0] = (byte) messages.length;
		int offset = 1;
		for (byte[] message : messages) {
			System.arraycopy(message, 0, batch, offset, message.length);
			offset += message.length;
		}
		return batch;
	}
}
