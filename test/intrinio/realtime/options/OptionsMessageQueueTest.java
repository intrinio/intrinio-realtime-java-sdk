package intrinio.realtime.options;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OptionsMessageQueueTest {
	@Test
	void messageTypesUseSeparatePriorityQueues() throws Exception {
		byte[] quote = message(52, (byte) 1);
		byte[] refresh = message(52, (byte) 2);
		byte[] unusual = message(74, (byte) 3);
		byte[] trade = message(72, (byte) 0);
		byte[] batch = new byte[1 + quote.length + refresh.length + unusual.length + trade.length];
		batch[0] = 4;
		int offset = 1;
		for (byte[] message : new byte[][]{quote, refresh, unusual, trade}) {
			System.arraycopy(message, 0, batch, offset, message.length);
			offset += message.length;
		}

		OptionsMessageQueue queue = new OptionsMessageQueue();
		queue.enqueueBatch(batch);

		assertEquals(0, queue.poll(10, TimeUnit.MILLISECONDS).get(22));
		assertEquals(3, queue.poll(10, TimeUnit.MILLISECONDS).get(22));
		assertEquals(2, queue.poll(10, TimeUnit.MILLISECONDS).get(22));
		assertEquals(1, queue.poll(10, TimeUnit.MILLISECONDS).get(22));
	}

	@Test
	void clearRemovesTicksFromThePreviousSession() throws Exception {
		byte[] trade = message(72, (byte) 0);
		byte[] batch = new byte[trade.length + 1];
		batch[0] = 1;
		System.arraycopy(trade, 0, batch, 1, trade.length);
		OptionsMessageQueue queue = new OptionsMessageQueue();
		queue.enqueueBatch(batch);
		queue.clear();

		assertEquals(0, queue.size());
		assertEquals(null, queue.poll(1, TimeUnit.MILLISECONDS));
	}

	private static byte[] message(int size, byte type) {
		byte[] message = new byte[size];
		message[22] = type;
		return message;
	}
}
