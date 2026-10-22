package intrinio.realtime.equities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import intrinio.realtime.test.FakeWebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ClientRestartTest {
	@Test
	void restartPreservesAndRejoinsSubscriptions() throws Exception {
		Config config = new Config("key", Provider.MANUAL, "localhost", new String[0], false, 1, false);
		List<FakeWebSocket> sockets = new ArrayList<>();
		Client client = new Client(config, () -> "token", (uri, listener) -> {
			FakeWebSocket socket = new FakeWebSocket().open(listener);
			sockets.add(socket);
			return CompletableFuture.completedFuture(socket);
		}, new long[]{5L}, 25);

		client.start();
		client.join("AAPL", true);
		assertEquals(1, sockets.get(0).sentBinaryCount());
		client.stop();
		client.start();

		assertEquals(1, sockets.get(1).sentBinaryCount(), "the new socket must rejoin the existing channel");
		client.stop();
	}
}
