package intrinio.realtime.options;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
		double timestamp) {

	private static String formatContract(String functionalContract){
		//Transform from server format to normal format
		//From this: AAPL_201016C100.00 or ABC_201016C100.003
		//To this:   AAPL__201016C00100000 or ABC___201016C00100003
		char[] contractChars = new char[]{'_','_','_','_','_','_','2','2','0','1','0','1','C','0','0','0','0','0','0','0','0'};
		int underscoreIndex = functionalContract.indexOf('_');

		//copy symbol
		functionalContract.getChars(0, underscoreIndex, contractChars, 0);

		//copy date
		functionalContract.getChars(underscoreIndex + 1, underscoreIndex + 7, contractChars, 6);

		//copy put/call
		functionalContract.getChars(underscoreIndex + 7, underscoreIndex + 8, contractChars, 12);

		int decimalIndex = functionalContract.indexOf('.', 9);

		//whole number copy
		functionalContract.getChars(underscoreIndex + 8, decimalIndex, contractChars, 18 - (decimalIndex - underscoreIndex - 8));

		//decimal number copy
		functionalContract.getChars(decimalIndex + 1, functionalContract.length(), contractChars, 18);

		return new String(contractChars);
	}

	public float getStrikePrice() {
		int whole = (this.contract.charAt(13) - '0') * 10000 + (this.contract.charAt(14) - '0') * 1000 + (this.contract.charAt(15) - '0') * 100 + (this.contract.charAt(16) - '0') * 10 + (this.contract.charAt(17) - '0');
		float part = (this.contract.charAt(18) - '0') * 0.1f + (this.contract.charAt(19) - '0') * 0.01f + (this.contract.charAt(20) - '0') * 0.001f;
		return (whole + part);
	}

	public boolean isPut() {
		return this.contract.charAt(12) == 'P';
	}

	public boolean isCall() {
		return this.contract.charAt(12) == 'C';
	}

	public ZonedDateTime getExpirationDate() {
		int year = 2000 + (this.contract.charAt(6) - '0') * 10 + (this.contract.charAt(7) - '0');
		int month = (this.contract.charAt(8) - '0') * 10 + (this.contract.charAt(9) - '0');
		int day = (this.contract.charAt(10) - '0') * 10 + (this.contract.charAt(11) - '0');
		ZoneId tz = ZoneId.of("America/New_York");
		return ZonedDateTime.of(year, month, day, 12, 0, 0, 0, tz);
	}

	public String getUnderlyingSymbol() {
		int i;
		for (i = 5; i >= 0 && this.contract.charAt(i) == '_'; i--);
		return this.contract.substring(0,i+1);
	}
	
	public String toString() {
		return String.format("Unusual Activity (Contract: %s, Type: %s, Sentiment: %s, TotalValue: %s, TotalSize: %s, AveragePrice: %s, AskPriceAtExecution: %s, BidPriceAtExecution: %s, UnderlyingPriceAtExecution: %s, Timestamp: %s)",
				this.contract,
				this.type,
				this.sentiment,
				this.totalValue,
				this.totalSize,
				this.averagePrice,
				this.askPriceAtExecution,
				this.bidPriceAtExecution,
				this.underlyingPriceAtExecution,
				this.timestamp);
	}

	public static UnusualActivity parse(byte[] bytes) {
		//byte structure:
		// contract length [0]
		// contract [1-21]
		// event type [22]
		// sentiment [23]
		// price type [24]
		// underlying price type [25]
		// total value [26-33]
		// total size [34-37]
		// average price [38-41]
		// ask price at execution [42-45]
		// bid price at execution [46-49]
		// underlying price at execution [50-53]
		// timestamp [54-61]

		String contract = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 1, bytes[0])).toString();
		
		UnusualActivityType type;
		switch (bytes[22]) {
			case 3: type = UnusualActivityType.BLOCK;
				break;
			case 4: type = UnusualActivityType.SWEEP;
				break;
			case 5: type = UnusualActivityType.LARGE;
				break;
			case 6: type = UnusualActivityType.UNUSUAL_SWEEP;
				break;
			default: type = UnusualActivityType.INVALID;
		}
		
		UnusualActivitySentiment sentiment;
		switch (bytes[23]) {
			case 0: sentiment = UnusualActivitySentiment.NEUTRAL;
				break;
			case 1: sentiment = UnusualActivitySentiment.BULLISH;
				break;
			case 2: sentiment = UnusualActivitySentiment.BEARISH;
				break;
			default: sentiment = UnusualActivitySentiment.INVALID;
		}

		PriceType scaler = PriceType.fromInt(bytes[24]);
		PriceType underlyingScaler = PriceType.fromInt(bytes[25]);
		
		ByteBuffer totalValueBuffer = ByteBuffer.wrap(bytes, 26, 8);
		totalValueBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double totalValue = scaler.getScaledValue(totalValueBuffer.getLong());
		
		ByteBuffer totalSizeBuffer = ByteBuffer.wrap(bytes, 34, 4);
		totalSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalSize = Integer.toUnsignedLong(totalSizeBuffer.getInt());
		
		ByteBuffer averagePriceBuffer = ByteBuffer.wrap(bytes, 38, 4);
		averagePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double averagePrice = scaler.getScaledValue(averagePriceBuffer.getInt());
		
		ByteBuffer askAtExecutionBuffer = ByteBuffer.wrap(bytes, 42, 4);
		askAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askAtExecution = scaler.getScaledValue(askAtExecutionBuffer.getInt());
		
		ByteBuffer bidAtExecutionBuffer = ByteBuffer.wrap(bytes, 46, 4);
		bidAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidAtExecution = scaler.getScaledValue(bidAtExecutionBuffer.getInt());
		
		ByteBuffer underlyingPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 50, 4);
		underlyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underlyingPriceAtExecutionBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 54, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		return new UnusualActivity(UnusualActivity.formatContract(contract), type, sentiment, totalValue, totalSize, averagePrice, askAtExecution, bidAtExecution, underlyingPriceAtExecution, timestamp);
	}
	
	public static UnusualActivity parse(ByteBuffer bytes) {
		//byte structure:
		// contract length [0]
		// contract [1-21]
		// event type [22]
		// sentiment [23]
		// price type [24]
		// underlying price type [25]
		// total value [26-33]
		// total size [34-37]
		// average price [38-41]
		// ask price at execution [42-45]
		// bid price at execution [46-49]
		// underlying price at execution [50-53]
		// timestamp [54-61]

		String contract = StandardCharsets.US_ASCII.decode(bytes.slice(1, bytes.get(0))).toString();
		
		UnusualActivityType type;
		switch (bytes.get(22)) {
			case 3: type = UnusualActivityType.BLOCK;
				break;
			case 4: type = UnusualActivityType.SWEEP;
				break;
			case 5: type = UnusualActivityType.LARGE;
				break;
			case 6: type = UnusualActivityType.UNUSUAL_SWEEP;
				break;
			default: type = UnusualActivityType.INVALID;
		}
		
		UnusualActivitySentiment sentiment;
		switch (bytes.get(23)) {
		case 0: sentiment = UnusualActivitySentiment.NEUTRAL;
			break;
		case 1: sentiment = UnusualActivitySentiment.BULLISH;
			break;
		case 2: sentiment = UnusualActivitySentiment.BEARISH;
			break;
		default: sentiment = UnusualActivitySentiment.INVALID;
		}

		PriceType scaler = PriceType.fromInt(bytes.get(24));
		PriceType underlyingScaler = PriceType.fromInt(bytes.get(25));
		
		ByteBuffer totalValueBuffer = bytes.slice(26, 8);
		totalValueBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double totalValue = scaler.getScaledValue(totalValueBuffer.getLong());
		
		ByteBuffer totalSizeBuffer = bytes.slice(34, 4);
		totalSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalSize = Integer.toUnsignedLong(totalSizeBuffer.getInt());
		
		ByteBuffer averagePriceBuffer = bytes.slice(38, 4);
		averagePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double averagePrice = scaler.getScaledValue(averagePriceBuffer.getInt());
		
		ByteBuffer askAtExecutionBuffer = bytes.slice(42, 4);
		askAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askAtExecution = scaler.getScaledValue(askAtExecutionBuffer.getInt());
		
		ByteBuffer bidAtExecutionBuffer = bytes.slice(46, 4);
		bidAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidAtExecution = scaler.getScaledValue(bidAtExecutionBuffer.getInt());
		
		ByteBuffer underlyingPriceAtExecutionBuffer = bytes.slice(50, 4);
		underlyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underlyingPriceAtExecutionBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(54, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		return new UnusualActivity(UnusualActivity.formatContract(contract), type, sentiment, totalValue, totalSize, averagePrice, askAtExecution, bidAtExecution, underlyingPriceAtExecution, timestamp);
	}
}
