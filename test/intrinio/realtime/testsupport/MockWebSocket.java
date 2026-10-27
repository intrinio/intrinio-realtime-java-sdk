package intrinio.realtime.testsupport;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MockWebSocket implements WebSocket {
	private final List<byte[]> sentBinary = new ArrayList<>();
	private volatile boolean inputClosed;
	private volatile boolean outputClosed;
	private volatile boolean aborted;
	private volatile boolean failSends;

	public synchronized List<byte[]> sentBinary() {
		return new ArrayList<>(sentBinary);
	}

	public void failSends(boolean failSends) {
		this.failSends = failSends;
	}

	public boolean aborted() {
		return aborted;
	}

	@Override
	public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
		return CompletableFuture.completedFuture(this);
	}

	@Override
	public synchronized CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
		if (failSends) return CompletableFuture.failedFuture(new IllegalStateException("scripted send failure"));
		byte[] copy = new byte[data.remaining()];
		data.get(copy);
		sentBinary.add(copy);
		return CompletableFuture.completedFuture(this);
	}

	@Override
	public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
		return CompletableFuture.completedFuture(this);
	}

	@Override
	public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
		return CompletableFuture.completedFuture(this);
	}

	@Override
	public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
		inputClosed = true;
		outputClosed = true;
		return CompletableFuture.completedFuture(this);
	}

	@Override
	public void request(long n) {
	}

	@Override
	public String getSubprotocol() {
		return "";
	}

	@Override
	public boolean isOutputClosed() {
		return outputClosed;
	}

	@Override
	public boolean isInputClosed() {
		return inputClosed;
	}

	@Override
	public void abort() {
		aborted = true;
		inputClosed = true;
		outputClosed = true;
	}
}
