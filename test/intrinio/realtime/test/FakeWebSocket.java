package intrinio.realtime.test;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class FakeWebSocket implements WebSocket {
	private volatile Listener listener;
	private volatile boolean inputClosed;
	private volatile boolean outputClosed;
	private volatile boolean aborted;
	private volatile boolean hangOnClose;
	private volatile boolean failSends;
	private final AtomicInteger sentBinaryCount = new AtomicInteger();

	public FakeWebSocket open(Listener listener) {
		this.listener = listener;
		listener.onOpen(this);
		return this;
	}

	public void closeFromServer() {
		inputClosed = true;
		outputClosed = true;
		Listener current = listener;
		if (current != null) current.onClose(this, NORMAL_CLOSURE, "server close");
	}

	public void error(Throwable error) {
		Listener current = listener;
		if (current != null) current.onError(this, error);
	}

	public void emitBinary(byte[] bytes) {
		Listener current = listener;
		if (current != null) current.onBinary(this, ByteBuffer.wrap(bytes), true);
	}

	public void setHangOnClose(boolean hangOnClose) {
		this.hangOnClose = hangOnClose;
	}

	public void setFailSends(boolean failSends) {
		this.failSends = failSends;
	}

	public boolean wasAborted() {
		return aborted;
	}

	public int sentBinaryCount() {
		return sentBinaryCount.get();
	}

	@Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
		return sendResult();
	}

	@Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
		sentBinaryCount.incrementAndGet();
		return sendResult();
	}

	@Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
		return sendResult();
	}

	@Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
		return sendResult();
	}

	@Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
		if (hangOnClose) return new CompletableFuture<>();
		outputClosed = true;
		return CompletableFuture.completedFuture(this);
	}

	@Override public void request(long n) { }
	@Override public String getSubprotocol() { return ""; }
	@Override public boolean isOutputClosed() { return outputClosed; }
	@Override public boolean isInputClosed() { return inputClosed; }

	@Override public void abort() {
		aborted = true;
		inputClosed = true;
		outputClosed = true;
	}

	private CompletableFuture<WebSocket> sendResult() {
		if (!failSends) return CompletableFuture.completedFuture(this);
		return CompletableFuture.failedFuture(new IllegalStateException("send failed"));
	}
}
