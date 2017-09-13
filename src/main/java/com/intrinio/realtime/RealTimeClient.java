package com.intrinio.realtime;

import com.neovisionaries.ws.client.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

public class RealTimeClient implements AutoCloseable {

    private String username;
    private String password;
    private String provider;
    private Logger logger;
    private Boolean ready;
    private ArrayBlockingQueue<Quote> queue;
    private Set<String> channels;
    private Set<String> joinedChannels;
    private String token;
    private WebSocket ws;
    private boolean debug;
    private long lastQueueWarningTime = 0;
    private List<Thread> threadsRunning = new ArrayList<Thread>();

    public static final String PROVIDER_IEX = "iex";
    private static final Integer MAX_QUEUE_SIZE = 10000;
    private static final Integer HEARTBEAT_INTERVAL = 20000;
    private static final Integer SELF_HEAL_TIME = 1000;
    private static final String IEX_HEARTBEAT_MSG = "{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":null}";

    public RealTimeClient(String username, String password, String provider) {
        this(username, password, provider, MAX_QUEUE_SIZE);
    }

    public RealTimeClient(String username, String password, String provider, Integer maxQueueSize) {
        this.username = username;
        this.password = password;
        this.provider = provider;

        this.logger = Logger.getLogger(RealTimeClient.class.getName());
        this.ready = false;
        this.queue = new ArrayBlockingQueue<Quote>(maxQueueSize);
        this.channels = new HashSet<String>();
        this.joinedChannels = new HashSet<String>();

        // Setup heartbeat
        final RealTimeClient client = this;
        Thread heartbeat = new Thread() {
            @Override
            public void run() {
                try {
                    while (true) {
                        sleep(HEARTBEAT_INTERVAL);
                        if (client.provider.equals(PROVIDER_IEX)) {
                            if (client.ws != null) {
                                client.ws.sendText(IEX_HEARTBEAT_MSG);
                            }
                        }
                    }
                }
                catch (InterruptedException e) { }
            }
        };

        heartbeat.start();
        this.threadsRunning.add(heartbeat);
    }

    public void connect() throws Exception {
        this.connectAsync();
        while (true) {}
    }

    public void connectAsync() throws Exception {
        this.logger.info("Connecting...");

        this.ready = false;
        this.joinedChannels = new HashSet<String>();

        if (this.ws != null) {
            this.ws.disconnect();
        }

        try {
            this.refreshToken();
            this.refreshWebSocket();
        }
        catch (Exception e) {
            this.logger.severe("Cannot connect: " + e.getMessage());
            if (this.debug) {
                e.printStackTrace();
            }
            this.trySelfHeal();
        }
    }

    public void disconnect() {
        this.ready = false;

        if (this.ws != null) {
            this.ws.disconnect();
        }

        this.logger.info("Disconnected");
    }

    @Override
    public void close() throws Exception {
        this.ready = false;

        if (this.ws != null) {
            this.ws.disconnect();
            this.ws.clearListeners();
        }

        for (Thread t : this.threadsRunning) {
            t.interrupt();
        }
    }

    public Quote getNextQuote() throws InterruptedException {
        return this.queue.take();
    }

    public void registerQuoteHandler(QuoteHandler handler) {
        handler.setClient(this);
        handler.start();
        this.threadsRunning.add(handler);
    }

    public int remainingQueueCapacity() {
        return this.queue.remainingCapacity();
    }

    public void onQueueFull() {
        long time = Calendar.getInstance().getTimeInMillis();
        if (time - this.lastQueueWarningTime > 1000) {
            this.logger.warning("Quote queue is full! Dropped some new quotes");
            this.lastQueueWarningTime = time;
        }
    }

    public void join(String channel) {
        this.join(new String[]{channel});
    }

    public void join(String[] channels) {
        for (String channel : channels) {
            this.channels.add(channel);
        }
        this.refreshChannels();
    }

    public void leave(String channel) {
        this.leave(new String[]{channel});
    }

    public void leave(String[] channels) {
        for (String channel : channels) {
            this.channels.remove(channel);
        }
        this.refreshChannels();
    }

    public void leaveAll() {
        this.channels = new HashSet<String>();
        this.refreshChannels();
    }

    public void setChannels(String[] channels) {
        this.channels = new HashSet<String>();
        for (String channel : channels) {
            this.channels.add(channel);
        }
        this.refreshChannels();
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    private String makeAuthUrl() {
        if (this.provider.equals(PROVIDER_IEX)) {
            return "https://realtime.intrinio.com/auth";
        }
        return null;
    }

    private void refreshToken() throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            String authUrl = this.makeAuthUrl();

            HttpGet httpGet = new HttpGet(authUrl);
            String encodedAuth = new String(Base64.encodeBase64((this.username + ":" + this.password).getBytes()));
            httpGet.setHeader("Authorization", "Basic " + encodedAuth);

            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                @Override
                public String handleResponse(final HttpResponse response) throws IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status == 200) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };

            this.token = httpClient.execute(httpGet, responseHandler);
            this.logger.info("Authentication successful!");
        } finally {
            httpClient.close();
        }

    }

    private String makeWebSocketUrl() throws URISyntaxException {
        if (this.provider.equals(PROVIDER_IEX)) {
            return "wss://realtime.intrinio.com/socket/websocket?vsn=1.0.0&token=" + this.token;
        }
        return null;
    }

    private void refreshWebSocket() throws Exception {
        final RealTimeClient client = this;

        String webSocketUrl = this.makeWebSocketUrl();

        this.ws = new WebSocketFactory().createSocket(webSocketUrl);
        this.ws.addListener(new WebSocketAdapter() {
            @Override
            public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
                client.logger.info("Websocket opened!");
                client.afterConnect();
            }

            @Override
            public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
                client.logger.info("Websocket closed!");
                if (clientCloseFrame == null || clientCloseFrame.getCloseCode() != 1000) {
                    client.trySelfHeal();
                }
            }

            @Override
            public void onError(WebSocket websocket, WebSocketException cause) {
                client.logger.severe("Websocket error: " + cause.getMessage());
            }

            @Override
            public void onTextMessage(WebSocket websocket, String message) throws Exception {
                client.logger.fine("Websocket message: " + message);

                JSONObject json = new JSONObject(message);
                Quote quote = null;

                if (client.provider.equals(PROVIDER_IEX)) {
                    if (json.getString("event").equals("quote")) {
                        JSONObject payload = json.getJSONObject("payload");
                        quote = new IexQuote(payload);
                    }
                }

                if (quote != null) {
                    boolean accepted = client.queue.offer(quote);
                    if (!accepted) {
                        client.onQueueFull();
                    }
                }
            }
        });

        this.logger.info("Trying websocket...");
        this.ws.connect();
    }

    private void afterConnect() {
        this.ready = true;
        this.refreshChannels();
    }


    private void trySelfHeal() throws Exception {
        sleep(SELF_HEAL_TIME);
        this.connectAsync();
    }

    private void refreshChannels() {
        if (!this.ready) {
            return;
        }

        // Join new channels
        Set<String> newChannels = new HashSet<String>(this.channels);
        newChannels.removeAll(this.joinedChannels);
        this.logger.fine("New channels: " + newChannels.toString());
        for (String channel : newChannels) {
            String msg = this.makeJoinMessage(channel);
            this.ws.sendText(msg);
            this.logger.info("Joined channel " + channel);
        }

        // Leave old channels
        Set<String> oldChannels = new HashSet<String>(this.joinedChannels);
        newChannels.removeAll(this.channels);
        this.logger.fine("Old channels: " + oldChannels.toString());
        for (String channel : oldChannels) {
            String msg = this.makeLeaveMessage(channel);
            this.ws.sendText(msg);
            this.logger.info("Left channel " + channel);
        }

        this.joinedChannels = new HashSet<String>(this.channels);
        this.logger.fine("Current channels: " + this.joinedChannels);
    }


    private String makeJoinMessage(String channel) {
        String message = "";

        if (this.provider.equals(PROVIDER_IEX)) {
            message = "{\"topic\":\"" + this.parseIexTopic(channel) + "\",\"event\":\"phx_join\",\"payload\":{},\"ref\":null}";
        }

        return message;
    }

    private String makeLeaveMessage(String channel) {
        String message = "";

        if (this.provider.equals(PROVIDER_IEX)) {
            message = "{\"topic\":\"" + this.parseIexTopic(channel) + "\",\"event\":\"phx_leave\",\"payload\":{},\"ref\":null}";
        }

        return message;
    }

    private String parseIexTopic(String channel) {
        switch (channel) {
            case "$lobby":
                return "iex:lobby";
            case "$lobby_last_price":
                return "iex:lobby:last_price";
            default:
                return "iex:securities:" + channel;
        }
    }
}
