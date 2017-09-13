# Intrinio Java SDK for Real-Time Stock Prices

[Intrinio](https://intrinio.com/) provides real-time stock prices via a two-way WebSocket connection. To get started, [subscribe to a real-time data feed](https://intrinio.com/marketplace/data/prices/realtime) and follow the instructions below.

## Requirements

- Java 7+

## Features

* Receive streaming, real-time price quotes (last trade, bid, ask)
* Subscribe to updates from individual securities
* Subscribe to updates for all securities (contact us for special access)

## Installation
Include in your Maven pom.xml file:
```
<dependencies>
    <dependency>
        <groupId>com.intrinio.realtime</groupId>
        <artifactId>intrinio-realtime</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

## Example Usage
```java
String username = "YOUR_INTRINIO_API_USERNAME";
String password = "YOUR_INTRINIO_API_PASSWORD";
String provider = RealTimeClient.PROVIDER_IEX;

try (RealTimeClient client = new RealTimeClient(username, password, provider)) {
    client.registerQuoteHandler(new QuoteHandler() {
        @Override
        public void onQuote(Quote quote) {
            System.out.println(quote.toString());
        }
    });

    String[] channels = new String[]{"AAPL", "MSFT", "GE"};
    client.join(channels);

    client.connect();
};
```

## Handling Quotes and the Queue

When the Intrinio Realtime library receives quotes from the websocket connection, it places them in an internal queue. This queue has a default maximum size of 10,000 quotes. You can modify this value by specifying a `maxQueueSize` parameter in the client constructor, as your environment memory contraints allow. Once a quote has been placed in the queue, a registered `QuoteHandler` will receive it and pass it along to its `onQuote` method. Your overriden `onQuote` method should process quotes quickly, so that the queue does not reach its maximum size (at which point, the system will log an error and ignore any incoming quotes until the queue has space). We recommend registering multiple `QuoteHandler` instances for operations such as writing quotes to a database (or anything else involving time-consuming I/O). The client also has a `remainingQueueCapacity()` method, which returns an integer specifying the approximate length of the quote queue. Monitor this to make sure you are processing quotes quickly enough.

## Providers

Currently, Intrinio offers realtime data for this SDK from the following providers:

* IEX - [Homepage](https://iextrading.com/)

Each has distinct price channels and quote formats, but a very similar API.

## Quote Data Format

Each data provider has a different format for their quote data.

### IEX

```json
{ "type": "ask",
  "timestamp": 1493409509.3932788,
  "ticker": "GE",
  "size": 13750,
  "price": 28.97 }
```

*   **type** - the quote type
  *    **`last`** - represents the last traded price
  *    **`bid`** - represents the top-of-book bid price
  *    **`ask`** - represents the top-of-book ask price
*   **timestamp** - a Unix timestamp (with microsecond precision)
*   **ticker** - the ticker of the security
*   **size** - the size of the `last` trade, or total volume of orders at the top-of-book `bid` or `ask` price
*   **price** - the price in USD

## Channels

### IEX

To receive price quotes from IEX, you need to instruct the client to "join" a channel. A channel can be
* A security ticker (`AAPL`, `MSFT`, `GE`, etc)
* The security lobby (`$lobby`) where all price quotes for all securities are posted
* The security last price lobby (`$lobby_last_price`) where only last price quotes for all securities are posted

Special access is required for both lobby channeles. [Contact us](mailto:sales@intrinio.com) for more information.

## API Keys
You will receive your Intrinio API Username and Password after [creating an account](https://intrinio.com/signup). You will need a subscription to a [realtime data feed](https://intrinio.com/marketplace/data/prices/realtime) as well.

## Documentation

### Methods

`RealTimeClient client = new RealTimeClient(String username, String password, String provider)` - Creates an Intrinio Real-Time client
* **Parameter** `username`: Your Intrinio API Username
* **Parameter** `password`: Your Intrinio API Password
* **Parameter** `provider`: The real-time data provider to use (for now "iex" only)

```java
String username = "YOUR_INTRINIO_API_USERNAME";
String password = "YOUR_INTRINIO_API_PASSWORD";
String provider = RealTimeClient.PROVIDER_IEX;

RealTimeClient client = new RealTimeClient(username, password, provider);
```

---------

`RealTimeClient client = new RealTimeClient(String username, String password, String provider, Integer maxQueueSize)` - Creates an Intrinio Real-Time client
* **Parameter** `username`: Your Intrinio API Username
* **Parameter** `password`: Your Intrinio API Password
* **Parameter** `provider`: The real-time data provider to use (for now "iex" only)
* **Parameter** `maxQueueSize`: The maximum size of the quote queue (default size is 10,000)

```java
String username = "YOUR_INTRINIO_API_USERNAME";
String password = "YOUR_INTRINIO_API_PASSWORD";
String provider = RealTimeClient.PROVIDER_IEX;
Integer maxQueueSize = 50000;

RealTimeClient client = new RealTimeClient(username, password, provider, maxQueueSize);
```

---------

`client.connect()` - Opens the WebSocket connection and joins the requested channels. This method blocks indefinitely.

---------

`client.connectAsync()` - Opens the WebSocket connection and joins the requested channels. This method returns after a connection has been established.

---------

`client.disconnect()` - Closes the WebSocket.

---------

`client.close()` - Closes the WebSocket, and disposes all internal resources. You _must_ either call this to dispose of the client or use try-with-resource.

---------

`client.registerQuoteHandler(QuoteHandler quoteHandler)` - Adds a QuoteHandler for handling quotes. Each quote handler will wait to receive a quote from the client's queue. Note that all quote handlers will not receive all quotes. Each handler receives the next quote in the queue once the handler finishes handling its current quote. Register multiple quote handlers to handle quotes quicker in cases of I/O.

```java
client.registerQuoteHandler(new QuoteHandler() {
    @Override
    public void onQuote(Quote quote) {
        // do something with the quote
    }
});
```

---------

`client.getNextQuote()` - Blocks until the queue provides the next quote, then returns the quote.

---------

`client.remainingQueueCapacity()` - Returns the remaining queue capacity. Check this periodically to make sure you are processing quotes quickly enough.

---------

`client.join(String channel)` - Joins the given channel. This can be called at any time. The client will automatically register joined channels and establish the proper subscriptions with the WebSocket connection.
* **Parameter** `channel` - The channel to join

```java
client.join("AAPL");
```

---------

`client.join(String[] channels)` - Joins the given channels.
* **Parameter** `channels` - The channels to join

```java
client.join(new String[]{"AAPL", "MSFT"});
```

---------

`client.leave(String channel)` - Leaves the given channel.
* **Parameter** `channel` - The channel to leave

```java
client.leave("AAPL");
```

---------

`client.leave(String[] channels)` - Leaves the given channels.
* **Parameter** `channel` - The channels to leave

```java
client.leave(new String[]{"AAPL", "MSFT"});
```

---------

`client.leaveAll()` - Leaves all channels.

---------

`client.setChannels(String[] channels)` - Joins/leaves channels so that only the given channels are joined.
* **Parameter** `channels` - The only channels to join

```java
client.setChannels(new String[]{"AAPL", "MSFT"});
```

---------

`client.setLogger(Logger logger)` - Replaces the internal logger with the provided java.util.logging.Logger instance.
* **Parameter** `logger` - A java.util.logging.Logger instance

---------

`client.setDebug(boolean debug)` - Turns debug mode on/off. Debug mode will print exception stack traces.
