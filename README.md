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
package intrinio;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

class TradeHandler implements OnTrade {
	private final ConcurrentHashMap<String,Integer> symbols = new ConcurrentHashMap<String,Integer>();
	private int maxTradeCount = 0;
	private Trade maxTrade;
	
	public int getMaxTradeCount() {
		return maxTradeCount;
	}

	public Trade getMaxTrade() {
		return maxTrade;
	}
	
	public void onTrade(Trade trade) {
		symbols.compute(trade.symbol(), (String key, Integer value) -> {
			if (value == null) {
				if (maxTradeCount == 0) {
					maxTradeCount = 1;
					maxTrade = trade;
				}
				return 1;
			} else {
				if (value + 1 > maxTradeCount) {
					maxTradeCount = value + 1;
					maxTrade = trade;
				}
				return value + 1;
			}
		});
	}
	
	public void tryLog() {
		if (maxTradeCount > 0) {
			Client.Log("Most active trade symbol: %s (%d updates)", maxTrade.symbol(), maxTradeCount);
			Client.Log("%s - Trade (price = %f, size = %d, isPut = %b, isCall = %b, exp = %s)",
					maxTrade.symbol(),
					maxTrade.price(),
					maxTrade.size(),
					maxTrade.isPut(),
					maxTrade.isCall(),
					maxTrade.getExpirationDate().toString());
		}
	}
}

class QuoteHandler implements OnQuote {
	private final ConcurrentHashMap<String,Integer> symbols = new ConcurrentHashMap<String,Integer>();
	private int maxQuoteCount = 0;
	private Quote maxQuote;
	
	public int getMaxQuoteCount() {
		return maxQuoteCount;
	}

	public Quote getMaxQuote() {
		return maxQuote;
	}
	
	public void onQuote(Quote quote) {
		symbols.compute(quote.symbol() + ":" + quote.type(), (String key, Integer value) -> {
			if (value == null) {
				if (maxQuoteCount == 0) {
					maxQuoteCount = 1;
					maxQuote = quote;
				}
				return 1;
			} else {
				if (value + 1 > maxQuoteCount) {
					maxQuoteCount = value + 1;
					maxQuote = quote;
				}
				return value + 1;
			}
		});
	}
	
	public void tryLog() {
		if (maxQuoteCount > 0) {
			Client.Log("Most active quote symbol: %s:%s (%d updates)", maxQuote.symbol(), maxQuote.type(), maxQuoteCount);
			Client.Log("%s - Quote (type = %s, price = %f, size = %d, isPut = %b, isCall = %b, exp = %s)",
					maxQuote.symbol(),
					maxQuote.type(),
					maxQuote.price(),
					maxQuote.size(),
					maxQuote.isPut(),
					maxQuote.isCall(),
					maxQuote.getExpirationDate().toString());
		}
	}
}

class OpenInterestHandler implements OnOpenInterest {
	public void onOpenInterest(OpenInterest oi) {
		Client.Log("Open Interest (%s) = %d", oi.symbol(), oi.openInterest());
	}
}

public class SampleApp {	
	public static void main(String[] args) {
		Client.Log("Starting sample app");
		TradeHandler tradeHandler = new TradeHandler();
		QuoteHandler quoteHandler = new QuoteHandler();
		OpenInterestHandler openInterestHandler = new OpenInterestHandler();
		Client client = new Client(tradeHandler, quoteHandler, openInterestHandler);
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			public void run() {
				Client.Log(client.getStats());
				tradeHandler.tryLog();
				quoteHandler.tryLog();
			}
		};
		timer.schedule(task, 10000, 10000);
		client.join();
	}
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
public record Trade(String symbol, double price, long size, ZonedDateTime timestamp, long totalVolume)
```

* **symbol** - Ticker symbole.
* **price** - the price in USD
* **size** - the size of the last trade.
* **totalVolume** - The number of stocks traded so far today for this symbol.
* **timestamp** - a Unix timestamp


### Quote Message

```java
public record Quote(QuoteType type, String symbol, double price, long size, ZonedDateTime timestamp)
```

* **type** - the quote type
  *    **`ask`** - represents an ask type
  *    **`bid`** - represents a bid type  
* **symbol** - Ticker symbol.
* **price** - the price in USD
* **size** - the size of the last ask or bid).
* **timestamp** - a Unix timestamp

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

