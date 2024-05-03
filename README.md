# Intrinio Java SDK for Real-Time Stock Prices
SDK for working with Intrinio's realtime Multi-Exchange or delayed SIP prices feed.  Intrinio’s Multi-Exchange feed bridges the gap by merging real-time equity pricing from the IEX and MEMX exchanges. Get a comprehensive view with increased market volume and enjoy no exchange fees, no per-user requirements, no permissions or authorizations, and little to no paperwork.

[Intrinio](https://intrinio.com/) provides real-time stock prices via a two-way WebSocket connection. To get started, [subscribe to a real-time data feed](https://intrinio.com/real-time-multi-exchange) and follow the instructions below.

## Requirements

- Java 14+

## Docker
Add your API key to the config.json file in src/intrinio, then
```
docker compose build
docker compose run example
```

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
		TradeHandler optionsTradeHandler = new TradeHandler();
		QuoteHandler optionsQuoteHandler = new QuoteHandler();
		Client client = new Client(optionsTradeHandler, optionsQuoteHandler);
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			public void run() {
				Client.Log(client.getStats());
				optionsTradeHandler.tryLog();
				optionsQuoteHandler.tryLog();
			}
		};
		timer.schedule(task, 10000, 10000);
		client.join(); //Load symbols from config.json
		//client.join(new String[] {"AAPL", "GOOG", "MSFT"}, false); //specify symbols at runtime
	}
```

## Handling Quotes

There are thousands of securities, each with their own feed of activity.  We highly encourage you to make your trade and quote handlers has short as possible and follow a queue pattern so your app can handle the volume of activity.

## Data Format

### Trade Message

```java
public record Trade(String symbol, SubProvider subProvider, char marketCenter, double price, long size, long timestamp, long totalVolume, String conditions)
```

* **symbol** - Ticker symbole.
* **subProvider** - Denotes the detailed source within grouped sources.
  *    **`NONE`** - No subtype specified.
  *    **`CTA_A`** - CTA_A in the DELAYED_SIP provider.
  *    **`CTA_B`** - CTA_B in the DELAYED_SIP provider.
  *    **`UTP`** - UTP in the DELAYED_SIP provider.
  *    **`OTC`** - OTC in the DELAYED_SIP provider.
  *    **`NASDAQ_BASIC`** - NASDAQ Basic in the NASDAQ_BASIC provider.
  *    **`IEX`** - From the IEX exchange in the REALTIME provider.
* **marketCenter** - Provides the market center
* **price** - the price in USD
* **size** - the size of the last trade.
* **totalVolume** - The number of stocks traded so far today for this symbol.
* **timestamp** - a Unix timestamp in nanoseconds since unix epoch.
* **conditions** - Provides the conditions


### Quote Message

```java
public record Quote(QuoteType type, String symbol, SubProvider subProvider, char marketCenter, double price, long size, long timestamp, String conditions)
```

* **type** - the quote type
  *    **`ask`** - represents an ask type
  *    **`bid`** - represents a bid type  
* **subProvider** - Denotes the detailed source within grouped sources.
  *    **`NONE`** - No subtype specified.
  *    **`CTA_A`** - CTA_A in the DELAYED_SIP provider.
  *    **`CTA_B`** - CTA_B in the DELAYED_SIP provider.
  *    **`UTP`** - UTP in the DELAYED_SIP provider.
  *    **`OTC`** - OTC in the DELAYED_SIP provider.
  *    **`NASDAQ_BASIC`** - NASDAQ Basic in the NASDAQ_BASIC provider.
  *    **`IEX`** - From the IEX exchange in the REALTIME provider.
* **marketCenter** - Provides the market center
* **symbol** - Ticker symbol.
* **price** - the price in USD
* **size** - the size of the last ask or bid).
* **timestamp** - a Unix timestamp in nanoseconds since unix epoch.
* **conditions** - Provides the conditions

## API Keys

You will receive your Intrinio API Key after [creating an account](https://intrinio.com/signup). You will need a subscription to a [realtime data feed](https://intrinio.com/real-time-multi-exchange) as well.

## Methods

`Client client = new Client(optionsTradeHandler, optionsQuoteHandler)` - Creates an Intrinio Real-Time client. The provided handlers implement OnTrade and OnQuote, which handle what happens when the associated event happens.
* **Parameter** `optionsTradeHandler`: The handler for trade events. This function will be invoked when a 'trade' has been received. The trade will be passed as an argument to the callback.
* **Parameter** `optionsQuoteHandler`: Optional. The handler for quote events. This function will be invoked when a 'quote' has been received. The quote will be passed as an argument to the callback. If 'onQuote' is not provided, the client will NOT request to receive quote updates from the server.
---------
`client.join(symbols, tradesOnly);` - Joins the given channels. This can be called at any time. The client will automatically register joined channels and establish the proper subscriptions with the WebSocket connection. If no arguments are provided, this function joins channel(s) configured in config.json.
* **Parameter** `symbols` - Optional. A string representing a single ticker symbol (e.g. "AAPL") or an array of ticker symbols (e.g. ["AAPL", "MSFT", "GOOG"]) to join. You can also use the special symbol, "lobby" to join the firehose channel and recieved updates for all ticker symbols. You must have a valid "firehose" subscription.
* **Parameter** `tradesOnly` - Optional (default: false). A boolean value indicating whether the server should return trade data only (as opposed to trade and quote data).
```java
client.join(["AAPL", "MSFT", "GOOG"])
client.join("GE", true)
client.join("lobby") //must have a valid 'firehose' subscription
```
---------
`client.leave(symbols)` - Leaves the given channels.
* **Parameter** `symbols` - Optional (default = all channels). A string representing a single ticker symbol (e.g. "AAPL") or an array of ticker symbols (e.g. ["AAPL", "MSFT", "GOOG"]) to leave. If not provided, all subscribed channels will be unsubscribed.
```java
client.leave(["AAPL", "MSFT", "GOOG"])
client.leave("GE")
client.leave("lobby")
client.leave()
```
---------
`client.stop()` - Closes the WebSocket, stops the self-healing and heartbeat intervals. Call this to properly dispose of the client.

## Configuration

### config.json
```json
{
	"apiKey": "",
	"provider": "REALTIME", //or DELAYED_SIP or NASDAQ_BASIC or MANUAL
	"symbols": [ "AAPL", "MSFT", "GOOG" ], //This is a list of individual tickers to subscribe to, or "lobby" to subscribe to all at once (firehose).
	"tradesOnly": true, //This indicates whether you only want trade events (true) or you want trade, ask, and bid events (false).
	"numThreads": 4 //The number of threads to use for processing events.
}
```

