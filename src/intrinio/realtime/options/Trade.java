package intrinio.realtime.options;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record Trade(String contract, Exchange exchange, double price, long size, double timestamp, long totalVolume, Qualifiers qualifiers, double askPriceAtExecution, double bidPriceAtExecution, double underlyingPriceAtExecution) {
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
		return this.getUnderlyingSymbol() != "SPX" ? ZonedDateTime.of(year, month, day, 16, 0, 0, 0, tz)
				: ZonedDateTime.of(year, month, day, 9, 30, 0, 0, tz);
	}

	public String getUnderlyingSymbol() {
		int i;
		for (i = 5; i >= 0 && this.contract.charAt(i) == '_'; i--);
		return this.contract.substring(0,i+1);
	}
	
	public String toString() {
		return String.format("Trade (Contract: %s, Exchange: %s, Price: %s, Size: %s, Timestamp: %s, TotalVolume: %s, Qualifiers: %s, AskPriceAtExecution: %s, BidPriceAtExecution: %s, UnderlyingPriceAtExecution: %s)",
				this.contract,
				this.exchange,
				this.price,
				this.size,
				this.timestamp,
				this.totalVolume,
				this.qualifiers,
				this.askPriceAtExecution,
				this.bidPriceAtExecution,
				this.underlyingPriceAtExecution);
	}

	public static Trade parse(byte[] bytes) {
		//byte structure:
		// contract length [0]
		// contract [1-21]
		// event type [22]
		// price type [23]
		// underlying price type [24]
		// price [25-28]
		// size [29-32]
		// timestamp [33-40]
		// total volume [41-48]
		// ask price at execution [49-52]
		// bid price at execution [53-56]
		// underlying price at execution [57-60]
		// qualifiers [61-64]
		// exchange [65]
		String contract = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 1, bytes[0])).toString();

		PriceType scaler = PriceType.fromInt(bytes[23]);
		PriceType underlyingScaler = PriceType.fromInt(bytes[24]);

		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, 25, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int unscaledPrice = priceBuffer.getInt();
		double price = scaler.getScaledValue(unscaledPrice);

		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, 29, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 33, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		ByteBuffer volumeBuffer = ByteBuffer.wrap(bytes, 41, 8);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = volumeBuffer.getLong();

		ByteBuffer askPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 49, 4);
		askPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askPriceAtExecution = scaler.getScaledValue(askPriceAtExecutionBuffer.getInt());

		ByteBuffer bidPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 53, 4);
		bidPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidPriceAtExecution = scaler.getScaledValue(bidPriceAtExecutionBuffer.getInt());

		ByteBuffer underlyingPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 57, 4);
		underlyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underlyingPriceAtExecutionBuffer.getInt());
		
		Qualifiers qualifiers = new Qualifiers(bytes[61], bytes[62], bytes[63], bytes[64]);
		
		Exchange exchange = Exchange.valueOfCode(bytes[65]);
		
		return new Trade(Trade.formatContract(contract), exchange, price, size, timestamp, totalVolume, qualifiers, askPriceAtExecution, bidPriceAtExecution, underlyingPriceAtExecution);
	}
	
	public static Trade parse(ByteBuffer bytes) {
		//byte structure:
		// contract length [0]
		// contract [1-21]
		// event type [22]
		// price type [23]
		// underlying price type [24]
		// price [25-28]
		// size [29-32]
		// timestamp [33-40]
		// total volume [41-48]
		// ask price at execution [49-52]
		// bid price at execution [53-56]
		// underlying price at execution [57-60]
		// qualifiers [61-64]
		// exchange [65]

		String contract = StandardCharsets.US_ASCII.decode(bytes.slice(1, bytes.get(0))).toString();

		PriceType scaler = PriceType.fromInt(bytes.get(23));
		PriceType underlyingScaler = PriceType.fromInt(bytes.get(24));

		ByteBuffer priceBuffer = bytes.slice(25, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int unscaledPrice = priceBuffer.getInt();
		double price = scaler.getScaledValue(unscaledPrice);
		
		ByteBuffer sizeBuffer = bytes.slice(29, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(33, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		ByteBuffer volumeBuffer = bytes.slice(41, 8);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = volumeBuffer.getLong();

		ByteBuffer askPriceAtExecutionBuffer = bytes.slice(49, 4);
		askPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askPriceAtExecution = scaler.getScaledValue(askPriceAtExecutionBuffer.getInt());

		ByteBuffer bidPriceAtExecutionBuffer = bytes.slice(53, 4);
		bidPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidPriceAtExecution = scaler.getScaledValue(bidPriceAtExecutionBuffer.getInt());

		ByteBuffer underyingPriceAtExecutionBuffer = bytes.slice(57, 4);
		underyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underyingPriceAtExecutionBuffer.getInt());
		
		Qualifiers qualifiers = new Qualifiers(bytes.get(61), bytes.get(62), bytes.get(63), bytes.get(64));
		
		Exchange exchange = Exchange.valueOfCode(bytes.get(65));

		return new Trade(Trade.formatContract(contract), exchange, price, size, timestamp, totalVolume, qualifiers, askPriceAtExecution, bidPriceAtExecution, underlyingPriceAtExecution);
	}
	
}