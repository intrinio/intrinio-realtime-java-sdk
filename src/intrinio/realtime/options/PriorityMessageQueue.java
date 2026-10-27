package intrinio.realtime.options;

import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A small, internal strict-priority queue. Each message class has its own FIFO;
 * consumers always drain the lowest-numbered (highest-priority) non-empty FIFO.
 */
final class PriorityMessageQueue {
	private final ArrayDeque<byte[]>[] queues;
	private final int boundedQueue;
	private final int boundedQueueCapacity;
	private final ReentrantLock lock = new ReentrantLock();
	private final Semaphore available = new Semaphore(0);
	private final AtomicLong droppedCount = new AtomicLong();

	@SuppressWarnings("unchecked")
	PriorityMessageQueue(int queueCount, int boundedQueue, int boundedQueueCapacity) {
		if (queueCount < 1 || boundedQueue < 0 || boundedQueue >= queueCount || boundedQueueCapacity < 1) {
			throw new IllegalArgumentException("Invalid priority queue configuration");
		}
		this.queues = new ArrayDeque[queueCount];
		for (int i = 0; i < queueCount; i++) {
			this.queues[i] = new ArrayDeque<>();
		}
		this.boundedQueue = boundedQueue;
		this.boundedQueueCapacity = boundedQueueCapacity;
	}

	boolean offer(int priority, byte[] message) {
		lock.lock();
		try {
			ArrayDeque<byte[]> queue = queues[priority];
			if (priority == boundedQueue && queue.size() >= boundedQueueCapacity) {
				// The displaced message already holds a permit. Do not release another.
				queue.pollFirst();
				droppedCount.incrementAndGet();
				queue.addLast(message);
				return true;
			}
			queue.addLast(message);
			available.release();
			return true;
		} finally {
			lock.unlock();
		}
	}

	byte[] take() throws InterruptedException {
		while (true) {
			available.acquire();
			lock.lockInterruptibly();
			try {
				for (ArrayDeque<byte[]> queue : queues) {
					byte[] message = queue.pollFirst();
					if (message != null) {
						return message;
					}
				}
			} finally {
				lock.unlock();
			}
		}
	}

	int size() {
		lock.lock();
		try {
			int size = 0;
			for (ArrayDeque<byte[]> queue : queues) {
				size += queue.size();
			}
			return size;
		} finally {
			lock.unlock();
		}
	}

	long getDroppedCount() {
		return droppedCount.get();
	}

	void clear() {
		lock.lock();
		try {
			for (ArrayDeque<byte[]> queue : queues) {
				queue.clear();
			}
			available.drainPermits();
		} finally {
			lock.unlock();
		}
	}
}
