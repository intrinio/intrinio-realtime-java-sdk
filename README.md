# intrinio-realtime-options-java-sdk
SDK for working with Intrinio's realtime prices feed

[Intrinio](https://intrinio.com/) provides real-time stock prices via a two-way WebSocket connection. To get started, [subscribe to a real-time data feed](https://intrinio.com/real-time-multi-exchange) and follow the instructions below.

## Requirements

- Java 7+

## Installation

Go to [Release](https://github.com/intrinio/intrinio-realtime-java-sdk/releases/), download the JAR, reference it in your project. The JAR contains dependencies necessary to the SDK.

## Sample Project

For a sample Java project see: [intrinio-realtime-java-sdk](https://github.com/intrinio/intrinio-realtime-java-sdk)

## Features

* Receive streaming, real-time price quotes (last trade, bid, ask)
* Subscribe to updates from individual securities
* Subscribe to updates for all securities

## Example Usage
```java
public static void main(String[] args) {
		Client.Log("Starting sample app");
		TradeHandler tradeHandler = new TradeHandler();
		QuoteHandler quoteHandler = new QuoteHandler();
		Client client = new Client(tradeHandler, quoteHandler);
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			public void run() {
				Client.Log(client.getStats());
				tradeHandler.tryLog();
				quoteHandler.tryLog();
			}
		};
		timer.schedule(task, 10000, 10000);
		client.join(); //Load symbols from config.json
		//client.join(new String[] {"AAPL", "GOOG", "MSFT"}, false); //specify symbols at runtime
	}
```

## Handling Quotes

There are thousands of securities, each with their own feed of activity.  We highly encourage you to make your trade and quote handlers has short as possible and follow a queue pattern so your app can handle the volume of activity.

## Providers

Currently, Intrinio offers realtime data for this SDK from the following providers:

* IEX  - [Homepage](https://iex.io)
* MEMX - [Homepage](https://memx.com)


## Data Format

### Trade Message

```java
public record Trade(String symbol, double price, long size, long timestamp, long totalVolume)
```

* **symbol** - Ticker symbole.
* **price** - the price in USD
* **size** - the size of the last trade.
* **totalVolume** - The number of stocks traded so far today for this symbol.
* **timestamp** - a Unix timestamp in nanoseconds since unix epoch.


### Quote Message

```java
public record Quote(QuoteType type, String symbol, double price, long size, long timestamp)
```

* **type** - the quote type
  *    **`ask`** - represents an ask type
  *    **`bid`** - represents a bid type  
* **symbol** - Ticker symbol.
* **price** - the price in USD
* **size** - the size of the last ask or bid).
* **timestamp** - a Unix timestamp in nanoseconds since unix epoch.

## API Keys

You will receive your Intrinio API Key after [creating an account](https://intrinio.com/signup). You will need a subscription to a [realtime data feed](https://intrinio.com/real-time-multi-exchange) as well.

## Documentation

### Methods

`Client client = new Client(tradeHandler, quoteHandler)` - Creates an Intrinio Real-Time client. The provided handlers implement OnTrade and OnQuote, which handle what happens when the associated event happens.
* **Parameter** `tradeHandler`: The handler for trade events.
* **Parameter** `quoteHandler`: The handler for quote events.

---------

`client.join();` - Joins channel(s) configured in config.json.

## Configuration

### config.json
```json
{
	"apiKey": "",
	"provider": "REALTIME",
	"symbols": [ "AAPL", "MSFT", "GOOG" ], //This is a list of individual tickers to subscribe to, or "lobby" to subscribe to all at once (firehose).
	"tradesOnly": true, //This indicates whether you only want trade events (true) or you want trade, ask, and bid events (false).
	"numThreads": 4 //The number of threads to use for processing events.
}
```

