package intrinio.realtime.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuthServer implements AutoCloseable {
	private final ServerSocket serverSocket;
	private final Thread thread;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final AtomicInteger requests = new AtomicInteger();
	private final int failuresBeforeSuccess;

	public AuthServer(int failuresBeforeSuccess) throws IOException {
		this.failuresBeforeSuccess = failuresBeforeSuccess;
		this.serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
		this.thread = new Thread(this::serve, "intrinio-test-auth-server");
		this.thread.setDaemon(true);
		this.thread.start();
	}

	public String address() {
		return "127.0.0.1:" + serverSocket.getLocalPort();
	}

	public int requests() {
		return requests.get();
	}

	private void serve() {
		while (running.get()) {
			try (Socket socket = serverSocket.accept()) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
				String line;
				do {
					line = reader.readLine();
				} while (line != null && !line.isEmpty());
				int request = requests.incrementAndGet();
				boolean fail = request <= failuresBeforeSuccess;
				byte[] body = (fail ? "try again" : "test-token").getBytes(StandardCharsets.UTF_8);
				String status = fail ? "503 Service Unavailable" : "200 OK";
				String headers = "HTTP/1.1 " + status + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
				OutputStream output = socket.getOutputStream();
				output.write(headers.getBytes(StandardCharsets.US_ASCII));
				output.write(body);
				output.flush();
			} catch (IOException e) {
				if (running.get()) throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void close() throws Exception {
		running.set(false);
		serverSocket.close();
		thread.join(2_000L);
	}
}
