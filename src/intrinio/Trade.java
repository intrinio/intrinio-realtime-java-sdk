package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A trade. "timestamp" is in nanoseconds since unix epoch.
 * @author Intrinio *
 */
public record Trade
	(String symbol,
	 byte source,
	 char marketCenter,
	 double price,
	 long size,
	 long timestamp,
	 long totalVolume,
	 String conditions) {
	
	public String toString() {
		String s =
			"Trade (" +
			"Symbol: " + this.symbol +
			", Source: " + this.source +
			", MarketCenter: " + this.marketCenter +
			", Price: " + this.price +
			", Size: " + this.size +
			", Total Volume: " + this.totalVolume +
			", Timestamp: " + this.timestamp +
			", Conditions: " + this.conditions +
			")";
		return s;
	}
	
	public static Trade parse(byte[] bytes, int startOffset) {
		
		byte symbolLen = bytes[startOffset + 2];
		
		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, startOffset + 3, symbolLen)).toString();
		
		byte source = bytes[startOffset + 3 + symbolLen];
		
		ByteBuffer marketCenterBuffer = ByteBuffer.wrap(bytes, startOffset + 4 + symbolLen, 2);
		marketCenterBuffer.order(ByteOrder.LITTLE_ENDIAN);
		char marketCenter = marketCenterBuffer.getChar();
		
		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, startOffset + 6 + symbolLen, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getFloat();
		
		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, startOffset + 10 + symbolLen, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, startOffset + 14 + symbolLen, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long nanoSecondsSinceEpoch = timeStampBuffer.getLong();
		
		ByteBuffer volumeBuffer = ByteBuffer.wrap(bytes, startOffset + 22 + symbolLen, 4);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = Integer.toUnsignedLong(volumeBuffer.getInt());
		
		byte conditionsLen = bytes[startOffset + 26 + symbolLen];
		
		String conditions = null;
		if (conditionsLen > 0) {
			conditions = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, startOffset + 27 + symbolLen, conditionsLen)).toString();
		}

		return new Trade(symbol, source, marketCenter, price, size, nanoSecondsSinceEpoch, totalVolume, conditions);
	}
	
}