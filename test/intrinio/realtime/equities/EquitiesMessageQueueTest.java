package intrinio.realtime.equities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EquitiesMessageQueueTest {
	@Test
	void tradesAreDequeuedBeforeQuotesAlreadyWaiting() throws Exception {
		EquitiesMessageQueue queue = new EquitiesMessageQueue();
		queue.enqueueBatch(new byte[]{3, 1, 2, 0, 2, 2, 2});

		assertEquals(0, queue.poll(10, TimeUnit.MILLISECONDS).get(0));
		assertEquals(1, queue.poll(10, TimeUnit.MILLISECONDS).get(0));
		assertEquals(2, queue.poll(10, TimeUnit.MILLISECONDS).get(0));
	}

	@Test
	void batchCountIsTreatedAsUnsigned() throws Exception {
		EquitiesMessageQueue queue = new EquitiesMessageQueue();
		byte[] batch = new byte[1 + 130 * 2];
		batch[0] = (byte) 130;
		for (int i = 0; i < 130; i++) {
			batch[1 + i * 2] = i == 129 ? (byte) 0 : (byte) 1;
			batch[2 + i * 2] = 2;
		}
		queue.enqueueBatch(batch);

		assertEquals(130, queue.size());
		assertEquals(0, queue.poll(10, TimeUnit.MILLISECONDS).get(0));
	}

	@Test
	void clearRemovesTicksFromThePreviousSession() throws Exception {
		EquitiesMessageQueue queue = new EquitiesMessageQueue();
		queue.enqueueBatch(new byte[]{1, 0, 2});
		queue.clear();

		assertEquals(0, queue.size());
		assertEquals(null, queue.poll(1, TimeUnit.MILLISECONDS));
	}
}
