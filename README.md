# Intrinio Java SDK for Real-Time Stock Prices
SDK for working with Intrinio's realtime IEX, NASDAQ Basic, delayed SIP prices, and [OPRA](https://www.opraplan.com/) options feeds.

[Intrinio](https://intrinio.com/) provides real-time stock prices via a two-way WebSocket connection. To get started, [subscribe to a real-time data feed](https://docs.intrinio.com/tutorial/websocket) and follow the instructions below.

## Programming Language Requirements

- Java 14+

## Minimum Hardware Requirements - Trades only
Equities Client:
* Non-lobby mode: 1 hardware core and 1 thread in your configuration for roughly every 100 symbols, up to the lobby mode settings. Absolute minimum 2 cores and threads.
* Lobby mode: 4 hardware cores and 4 threads in your configuration
* 5 Mbps connection
* 0.5 ms latency

Options Client:
* Non-lobby mode: 1 hardware core and 1 thread in your configuration for roughly every 250 contracts, up to the lobby mode settings.  3 cores and 3 configured threads for each chain, up to the lobby mode settings. Absolute minimum 3 cores and threads.
* Lobby mode: 6 hardware cores and 6 threads in your configuration
* 25 Mbps connection
* 0.5 ms latency

## Minimum Hardware Requirements - Trades and Quotes
Equities Client:
* Non-lobby mode: 1 hardware core and 1 thread in your configuration for roughly every 25 symbols, up to the lobby mode settings. Absolute minimum 4 cores and threads.
* Lobby mode: 8 hardware cores and 8 threads in your configuration
* 25 Mbps connection
* 0.5 ms latency

Options Client:
* Non-lobby mode: 1 hardware core and 1 thread in your configuration for roughly every 100 contracts, up to the lobby mode settings.  4 cores and 4 configured threads for each chain, up to the lobby mode settings. Absolute minimum 4 cores and threads.
* Lobby mode: 12 hardware cores and 12 threads in your configuration
* 100 Mbps connection
* 0.5 ms latency

## Docker
Add your API key to the config.json file in src/intrinio.realtime.equities or src/intrinio.realtime.options, uncomment the desired example in SampleApp.java, then
```
docker compose build
docker compose run example
```

## Installation

Go to [Release](https://github.com/intrinio/intrinio-realtime-java-sdk/releases/), download the JAR, reference it in your project. The JAR contains dependencies necessary to the SDK.

## Sample Project

For a sample Java project see: [intrinio-realtime-java-sdk](https://github.com/intrinio/intrinio-realtime-java-sdk)

## Features

* Receive streaming, real-time equities price quotes (trades, bid, ask)
* Receive streaming, real-time options price quotes (trades, conflated bid/ask, unusual activity (block trades, sweeps, whale trades, unusual sweeps), open interest, open, close, high, low)
* Subscribe to updates from individual securities/chains/contracts, or
* Subscribe to updates for all securities (lobby)

## Equities Example Usage
* See [Equities Sample Websocket](https://github.com/intrinio/intrinio-realtime-java-sdk/blob/master/src/SampleApp/EquitiesSampleApp.java) and [Sample Websocket](https://github.com/intrinio/intrinio-realtime-java-sdk/blob/master/src/SampleApp/SampleApp.java)

## Options Example Usage
* See [Equities Sample Websocket](https://github.com/intrinio/intrinio-realtime-java-sdk/blob/master/src/SampleApp/OptionsSampleApp.java) and [Sample Websocket](https://github.com/intrinio/intrinio-realtime-java-sdk/blob/master/src/SampleApp/SampleApp.java)

## Options and Equities concurrently Example Usage
* See [Equities Sample Websocket](https://github.com/intrinio/intrinio-realtime-java-sdk/blob/master/src/SampleApp/CompositeSampleApp.java) and [Sample Websocket](https://github.com/intrinio/intrinio-realtime-java-sdk/blob/master/src/SampleApp/SampleApp.java)

## Handling Events

There are thousands of securities and millions of options contracts, each with their own feed of activity.  We highly encourage you to make your trade and quote handlers has short as possible and follow a queue pattern so your app can handle the volume of activity.
Note that quotes (ask and bid updates) comprise ~90% of the volume of the entire feed. Be cautious when deciding to receive ask/bid quote updates.

## Data Format

### Equities Trade Message

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


### Equities Quote Message

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

### Options Trade Message

```java
public record Trade(String contract, double price, long size, double timestamp, long totalVolume, double askPriceAtExecution, double bidPriceAtExecution, double underlyingPriceAtExecution)
```

* **contract** - Identifier for the options contract.  This includes the ticker symbol, put/call, expiry, and strike price.
* **exchange** - an `Exchange` enum indicating the specific exchange through which the trade occurred
* **price** - the price in USD
* **size** - the size of the last trade in hundreds (each contract is for 100 shares).
* **totalVolume** - The number of contracts traded so far today.
* **timestamp** - a Unix timestamp (with microsecond precision)
* **qualifiers** - a `Qualifiers` object containing 4 bytes: each byte represents one trade qualifier. See list of possible [Trade Qualifiers](#trade-qualifiers), below.
* **askPriceAtExecution** - the last best ask price in USD at execution.
* **bidPriceAtExecution** - the last best bid price in USD at execution.
* **underlyingPriceAtExecution** - the price of the underlying security at execution.

### Options Trade Qualifiers

The trade qualifiers field is represented by a tuple containing 4 integers. Each integer can take one of the following values:
* **`0`** - Regular transaction
* **`2`** - Cancel
* **`3`** - This is the last price and it's cancelled
* **`4`** - Late but in sequence / sold last late
* **`5`** - This was the open price and it's cancelled
* **`6`** - Late report of opening trade and is out of sequence: or set the open
* **`7`** - Cancel only trade reported
* **`8`** - Transaction was executed electronically
* **`9`** - Reopen of a previously halted contract
* **`11`** - Spread
* **`23`** - Intermarket Sweep
* **`30`** - Extended hours
* **`33`** - Crossed trade including Request For Cross RFC
* **`87`** - Complex trade with equity leg
* **`107`** - Auction
* **`123`** - Stock option trade
* **`136`** - Ex-Pit trade
* **`192`** - Message received locally out-of-sequence
* **`222`** - Combo trade
* **`0`** - Blank

Each trade can be qualified by a maximum of 4(four) values. The combination of these values can have special values. These special values are:

* **`107, 23`** - Single leg auction ISO
* **`23, 33`** - Single leg cross ISO
* **`8, 11`** - Multi leg auto-electronic trade
* **`107, 11`** - Multi leg auction
* **`11, 33`** - Multi leg cross
* **`136, 11`** - Multi leg floor trade
* **`8, 11, 87`** - Multi leg auto-electronic trade against single leg(s)
* **`107, 123`** - Stock options auction
* **`107, 11, 87`** - Multi leg auction against single leg(s)
* **`136, 11, 87`** - Multi leg floor trade against single leg(s)
* **`8, 123`** - Stock options auto-electronic trade
* **`123, 33`** - Stock options cross
* **`136, 123`** - Stock options floor trade
* **`8, 87, 123`** - Stock options auto-electronic trade against single leg(s)
* **`107, 87, 123`** - Stock options auction against single leg(s)
* **`136, 87, 123`** - Stock options floor trade against single leg(s)
* **`136, 11, 222`** - Multi leg floor trade of proprietary products
* **`222, 30`** - Multilateral Compression Trade of Proprietary Data Products

### Options Quote Message

```java
public record Quote(String contract, double askPrice, long askSize, double bidPrice, long bidSize, double timestamp)
```

* **contract** - Identifier for the options contract.  This includes the ticker symbol, put/call, expiry, and strike price.
* **askPrice** - the last best ask price in USD
* **askSize** - the last best ask size of the last ask or bid in hundreds (each contract is for 100 shares).
* **bidPrice** - the last best bid price in USD
* **bidSize** - the last best bid size of the last ask or bid in hundreds (each contract is for 100 shares).
* **timestamp** - a Unix timestamp (with microsecond precision)


### Options Open Interest Message

```java
public record Refresh (String contract, long openInterest, double openPrice, double closePrice, double highPrice, double lowPrice)
```

* **contract** - Identifier for the options contract.  This includes the ticker symbol, put/call, expiry, and strike price.
* **openInterest** - the total quantity of opened contracts as reported at the start of the trading day
* **openPrice** - the opening price for the day
* **closePrice** - the closing price for the day
* **highPrice** - the current high price for the day
* **lowPrice** - the current low price for the day

### Options Unusual Activity Message
```java
public record UnusualActivity(
        String contract,
        UnusualActivityType type,
        UnusualActivitySentiment sentiment,
        double totalValue,
        long totalSize,
        double averagePrice,
        double askPriceAtExecution,
        double bidPriceAtExecution,
        double underlyingPriceAtExecution,
        double timestamp)
```

* **contract** - Identifier for the options contract.  This includes the ticker symbol, put/call, expiry, and strike price.
* **type** - The type of unusual activity that was detected
  * **`Block`** - represents an 'block' trade
  * **`Sweep`** - represents an intermarket sweep
  * **`Large`** - represents a trade of at least $100,000
  * **`Unusual Sweep`** - represents an unusually large sweep near market open
* **sentiment** - The sentiment of the unusual activity event
  *    **`Neutral`** -
  *    **`Bullish`** -
  *    **`Bearish`** -
* **totalValue** - The total value of the trade in USD. 'Sweeps' and 'blocks' can be comprised of multiple trades. This is the value of the entire event.
* **totalSize** - The total size of the trade in number of contracts. 'Sweeps' and 'blocks' can be comprised of multiple trades. This is the total number of contracts exchanged during the event.
* **averagePrice** - The average price at which the trade was executed. 'Sweeps' and 'blocks' can be comprised of multiple trades. This is the average trade price for the entire event.
* **askAtExecution** - The 'ask' price of the contract at execution of the trade event.
* **bidAtExecution** - The 'bid' price of the contract at execution of the trade event.
* **underlyingPriceAtExecution** - The last trade price of the underlying security at execution of the trade event.
* **timestamp** - a Unix timestamp (with microsecond precision).

## API Keys

You will receive your Intrinio API Key after [creating an account](https://intrinio.com/signup). You will need a subscription to a [realtime data feed](https://intrinio.com/real-time-multi-exchange) as well.

### Overview

The Intrinio Realtime Client will handle authorization as well as establishment and management of all necessary WebSocket connections. All you need to get started is your API key.
The first thing that you'll do is create a new `Client` object.
After a `Client` object has been created, you will immediately register a series of callbacks, using the `setOnX` methods (e.g. `setOnTrade`). These callback methods tell the client what types of subscriptions you will be setting up.
You must register callbacks in order to receive data. And you will only receive data for the types of callbacks that you have registered (i.e. you will only receive trade updates if you register an `OnTrade` callback).
After registering your desired callbacks, you may subscribe to receive feed updates from the server.
You may subscribe to a static list of symbols (a mixed list of option contracts and/or option chains).
Or, you may subscribe, dynamically, to option contracts, option chains, or a mixed list thereof.
It is also possible to subscribe to the entire universe of option contracts by calling `JoinLobby`.
The volume of data provided by the lobby (`Firehose`) exceeds 100Mbps and requires higher hardware requirements.
Do not subscribe to lobby and individual symbols/chains/contracts at the same time - either join lobby by itself, or a list of symbols/chains/contracts.
After subscribing your starting list of symbols, you will call the `start` method. The client will immediately attempt to authorize your API key (provided in the config.json file). If authoriztion is successful, the necessary connection(s) will be opened.
If you are using the non-firehose feed, you may update your subscriptions on the fly, using the `join` and `leave` methods.
The WebSocket client is designed for near-indefinite operation. It will automatically reconnect if a connection drops/fails and when then servers turn on every morning.
If you wish to perform a graceful shutdown of the application, please call the `stop` method.

## Equities Methods

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

## Equities Configuration

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

### Options Methods

`Client client = new Client(Config config)` - Creates an Intrinio Real-Time client.
* **Parameter** `config`: Optional - The configuration to be used by the client. If this value is not provided, `config.json` will be picked up (from the project root) and used.

---------

`client.setOnTrade(OnTrade onTrade) throws Exception` - Registers a callback that is invoked for every trade update. If no `onTrade` callback is registered with this method, you will not receive trade updates from the server.
* **Parameter** `onTrade`: The handler for trade events.
* **Throws** `Exception`: If the start method has already been called. Or if `OnTrade` has already been set.

`client.setOnQuote(OnQuote onQuote) throws Exception` - Registers a callback that is invoked for every quote update. If no `onQuote` callback is registered with this method, you will not receive quote (ask, bid) updates from the server.
* **Parameter** `onQuote`: The handler for quote events.
* **Throws** `Exception`: If the start method has already been called. Or if `OnQuote` has already been set.

`client.setOnRefresh(OnRefresh onRefresh) throws Exception` - Registers a callback that is invoked for refresh update. If no `onRefresh` callback is registered with this method, you will not receive open interest, high, low, open, close data from the server.
* **Parameter** `onRefresh`: The handler for refresh events.
* **Throws** `Exception`: If the start method has already been called. Or if `OnRefresh` has already been set.

`client.setOnUnusualActivity(OnUnusualActivity onUnusualActivity) throws Exception` - Registers a callback that is invoked for every unusual trade. If no `onUnusualActivity` callback is registered with this method, you will not receive unusual trade updates from the server.
* **Parameter** `onUnusualActivity`: The handler for unusual trade events.
* **Throws** `Exception`: If the start method has already been called. Or if `OnUnusualActivity` has already been set.

---------

`client.start()` - Starts the Intrinio Realtime WebSocket Client.
This method will immediately attempt to authorize the API key (provided in config).
After successful authorization, all of the data processing threads will be started, and the websocket connections will be opened.
If a subscription has already been created with one of the `join` methods, data will begin to flow.

---------

`client.join()` - Joins channel(s) configured in config.json.
`client.join(String channel)` - Joins the provided channel. E.g. "AAPL" or "GOOG__210917C01040000"
`client.join(String[] channels)` - Joins the provided channels. E.g. [ "AAPL", "MSFT__210917C00180000", "GOOG__210917C01040000" ]
`client.joinLobby()` - Joins the 'lobby' (aka. firehose) channel. The provider must be set to `OPRA_FIREHOSE` for this to work. This requires special account permissions.

---------

`client.leave()` - Leaves all joined channels/subscriptions, including `lobby`.
`client.leave(String channel)` - Leaves the specified channel. E.g. "AAPL" or "GOOG__210917C01040000"
`client.leave(String[] channels)` - Leaves the specified channels. E.g. [ "AAPL", "MSFT__210917C00180000", "GOOG__210917C01040000" ]
`client.leaveLobby()` Leaves the `lobby` channel

---------
`client.stop();` - Stops the Intrinio Realtime WebSocket Client. This method will leave all joined channels, stop all threads, and gracefully close the websocket connection(s).

## Options Configuration

### config.json
```json
{
	"apiKey": "",
	"provider": "OPRA", //OPRA
	"symbols": [ "GOOG__210917C01040000", "MSFT", "AAPL__210917C00130000", "SPY" ], //Individual contracts, or option chains to subscribe to all contracts under a symbol.
	"numThreads": 4 //The number of threads to use for processing events.
}
```