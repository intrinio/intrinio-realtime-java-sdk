package intrinio;

import java.net.http.WebSocket;
import java.time.LocalDateTime;

class WebSocketState {

    private WebSocket ws;
    private boolean isReady = false;
    private boolean isReconnecting = false;
    private LocalDateTime lastReset;

    WebSocketState() {}

    WebSocket getWebSocket() {
        return ws;
    }

    void setWebSocket(WebSocket ws) {
        this.ws = ws;
        reset();
        setReady(true);
        setReconnecting(false);
    }

    boolean isReady() {
        return isReady;
    }

    void setReady(boolean isReady) {
        this.isReady = isReady;
    }

    boolean isReconnecting() {
        return isReconnecting;
    }

    void setReconnecting(boolean isReconnecting) {
        this.isReconnecting = isReconnecting;
    }

    LocalDateTime getLastReset() {
        return lastReset;
    }

    void reset() {
        this.lastReset = LocalDateTime.now();
    }
}