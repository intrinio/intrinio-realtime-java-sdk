package SampleApp;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import intrinio.*;

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
			Client.Log("%s - Trade (price = %f, size = %d, time = %s)",
					maxTrade.symbol(),
					maxTrade.price(),
					maxTrade.size(),
					maxTrade.timestamp());
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
			Client.Log("%s - Quote (type = %s, price = %f, size = %d)",
					maxQuote.symbol(),
					maxQuote.type(),
					maxQuote.price(),
					maxQuote.size());
		}
	}
}

public class SampleApp {
	
	public static void main(String[] args) {
		Client.Log("Starting sample app");
		TradeHandler tradeHandler = new TradeHandler();
		QuoteHandler quoteHandler = new QuoteHandler();
		//Config config = null; //You can either create a config class, or load it from the intrinio/config.json file
		//try { config = new Config("apiKeyHere", Provider.REALTIME, null, null, false, 2); } catch (Exception e) {e.printStackTrace();}		
		//Client client = new Client(tradeHandler, quoteHandler, config);
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
		client.join(); //Loads symbols from config
		//client.join(new String[] {"AAPL", "GOOG", "MSFT"}, false); //specify symbols at runtime
	}

}
