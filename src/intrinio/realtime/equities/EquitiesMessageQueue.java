package intrinio.realtime.equities;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Splits feed batches into independently consumable events. Trades are always
 * selected before quotes that are already waiting in the client.
 */
final class EquitiesMessageQueue {
	private final LinkedBlockingDeque<ByteBuffer> trades = new LinkedBlockingDeque<>();
	private final LinkedBlockingDeque<ByteBuffer> quotes = new LinkedBlockingDeque<>();
	private final Semaphore available = new Semaphore(0);

	synchronized void enqueueBatch(byte[] batch) {
		if (batch == null || batch.length == 0) return;

		List<ByteBuffer> newTrades = new ArrayList<>();
		List<ByteBuffer> newQuotes = new ArrayList<>();
		int count = Byte.toUnsignedInt(batch[0]);
		int offset = 1;

		for (int i = 0; i < count; i++) {
			if (offset + 2 > batch.length) {
				Client.Log("Error parsing multi-part message. Incomplete equities message header");
				break;
			}

			int messageLength = Byte.toUnsignedInt(batch[offset + 1]);
			if (messageLength < 2 || offset + messageLength > batch.length) {
				Client.Log("Error parsing multi-part message. Invalid equities message length %d", messageLength);
				break;
			}

			ByteBuffer message = ByteBuffer.wrap(batch, offset, messageLength).slice();
			if (message.get(0) == 0) newTrades.add(message);
			else newQuotes.add(message);
			offset += messageLength;
		}

		// Publish permits only after the entire batch has been classified so a
		// worker cannot consume an early quote before a later trade in the batch.
		trades.addAll(newTrades);
		quotes.addAll(newQuotes);
		available.release(newTrades.size() + newQuotes.size());
	}

	ByteBuffer poll(long timeout, TimeUnit unit) throws InterruptedException {
		if (!available.tryAcquire(timeout, unit)) return null;
		ByteBuffer message = trades.pollFirst();
		if (message == null) message = quotes.pollFirst();
		return message;
	}

	int size() {
		return trades.size() + quotes.size();
	}

	synchronized void clear() {
		trades.clear();
		quotes.clear();
		available.drainPermits();
	}
}
