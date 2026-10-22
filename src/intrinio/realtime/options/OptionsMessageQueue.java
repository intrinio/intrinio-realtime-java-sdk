package intrinio.realtime.options;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Splits feed batches into independent priority queues. Trades are processed
 * first, followed by unusual activity, refreshes, and quotes.
 */
final class OptionsMessageQueue {
	private static final int MESSAGE_TYPE_INDEX = 22;
	private static final int TRADE_MESSAGE_SIZE = 72;
	private static final int QUOTE_MESSAGE_SIZE = 52;
	private static final int REFRESH_MESSAGE_SIZE = 52;
	private static final int UNUSUAL_ACTIVITY_MESSAGE_SIZE = 74;

	private final LinkedBlockingDeque<ByteBuffer> trades = new LinkedBlockingDeque<>();
	private final LinkedBlockingDeque<ByteBuffer> unusualActivity = new LinkedBlockingDeque<>();
	private final LinkedBlockingDeque<ByteBuffer> refreshes = new LinkedBlockingDeque<>();
	private final LinkedBlockingDeque<ByteBuffer> quotes = new LinkedBlockingDeque<>();
	private final Semaphore available = new Semaphore(0);

	synchronized void enqueueBatch(byte[] batch) {
		if (batch == null || batch.length == 0) return;

		List<ByteBuffer> newTrades = new ArrayList<>();
		List<ByteBuffer> newUnusualActivity = new ArrayList<>();
		List<ByteBuffer> newRefreshes = new ArrayList<>();
		List<ByteBuffer> newQuotes = new ArrayList<>();
		int count = Byte.toUnsignedInt(batch[0]);
		int offset = 1;

		for (int i = 0; i < count; i++) {
			if (offset + MESSAGE_TYPE_INDEX >= batch.length) {
				Client.Log("Error parsing multi-part message. Incomplete options message header");
				break;
			}

			byte type = batch[offset + MESSAGE_TYPE_INDEX];
			int messageLength;
			if (type == 0) messageLength = TRADE_MESSAGE_SIZE;
			else if (type == 1) messageLength = QUOTE_MESSAGE_SIZE;
			else if (type == 2) messageLength = REFRESH_MESSAGE_SIZE;
			else messageLength = UNUSUAL_ACTIVITY_MESSAGE_SIZE;

			if (offset + messageLength > batch.length) {
				Client.Log("Error parsing multi-part message. Incomplete options message type %d", type);
				break;
			}

			ByteBuffer message = ByteBuffer.wrap(batch, offset, messageLength).slice();
			if (type == 0) newTrades.add(message);
			else if (type == 1) newQuotes.add(message);
			else if (type == 2) newRefreshes.add(message);
			else newUnusualActivity.add(message);
			offset += messageLength;
		}

		trades.addAll(newTrades);
		unusualActivity.addAll(newUnusualActivity);
		refreshes.addAll(newRefreshes);
		quotes.addAll(newQuotes);
		available.release(newTrades.size() + newUnusualActivity.size() + newRefreshes.size() + newQuotes.size());
	}

	ByteBuffer poll(long timeout, TimeUnit unit) throws InterruptedException {
		if (!available.tryAcquire(timeout, unit)) return null;
		ByteBuffer message = trades.pollFirst();
		if (message == null) message = unusualActivity.pollFirst();
		if (message == null) message = refreshes.pollFirst();
		if (message == null) message = quotes.pollFirst();
		return message;
	}

	int size() {
		return trades.size() + unusualActivity.size() + refreshes.size() + quotes.size();
	}

	synchronized void clear() {
		trades.clear();
		unusualActivity.clear();
		refreshes.clear();
		quotes.clear();
		available.drainPermits();
	}
}
