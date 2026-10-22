package intrinio.realtime.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import intrinio.realtime.test.FakeWebSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ClientPriorityIntegrationTest {
	@Test
	void tradeCallbackRunsBeforeEarlierQuoteInTheSameBatch() throws Exception {
		Config config = new Config("key", Provider.MANUAL, "localhost", new String[0], 1, false);
		FakeWebSocket socket = new FakeWebSocket();
		List<String> callbacks = new CopyOnWriteArrayList<>();
		CountDownLatch handled = new CountDownLatch(2);
		Client client = new Client(config, () -> "token", (uri, listener) ->
			CompletableFuture.completedFuture(socket.open(listener)), new long[]{5L}, 25);
		client.setOnTrade(trade -> { callbacks.add("trade"); handled.countDown(); });
		client.setOnQuote(quote -> { callbacks.add("quote"); handled.countDown(); });

		client.start();
		socket.emitBinary(batch(optionsMessage((byte) 1, 52), optionsMessage((byte) 0, 72)));

		assertTrue(handled.await(1, TimeUnit.SECONDS));
		assertEquals(List.of("trade", "quote"), callbacks);
		client.stop();
	}

	private static byte[] optionsMessage(byte type, int length) {
		byte[] message = new byte[length];
		byte[] contract = "A_260101C1.00".getBytes(StandardCharsets.US_ASCII);
		message[0] = (byte) contract.length;
		System.arraycopy(contract, 0, message, 1, contract.length);
		message[22] = type;
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
