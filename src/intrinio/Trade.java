package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRules;
import java.util.Date;

/**
 * A trade. "timestamp" is in nanoseconds since unix epoch.
 * @author Intrinio *
 */
public record Trade(String symbol, double price, long size, long timestamp, long totalVolume) {
	
	public String toString() {
		String s =
				"Trade (" +
				"Symbol: " + this.symbol +
				", Price: " + this.price +
				", Size: " + this.size +
				", Total Volume: " + this.totalVolume +
				", Timestamp: " + this.timestamp +
				")";
		return s;
	}
	
	public static Trade parse(byte[] bytes, int symbolLength) {
		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 2, symbolLength)).toString();
		
		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, 2 + symbolLength, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getInt() / 10000.0;
		
		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, 6 + symbolLength, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 10 + symbolLength, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long nanoSecondsSinceEpoch = timeStampBuffer.getLong();		
		
		ByteBuffer volumeBuffer = ByteBuffer.wrap(bytes, 18 + symbolLength, 4);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = Integer.toUnsignedLong(volumeBuffer.getInt());
		
		return new Trade(symbol, price, size, nanoSecondsSinceEpoch, totalVolume);
	}
	
	public static Trade parse(ByteBuffer bytes, int symbolLength) {
		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(2, symbolLength)).toString();
		
		ByteBuffer priceBuffer = bytes.slice(2 + symbolLength, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getInt() / 10000.0;
		
		ByteBuffer sizeBuffer = bytes.slice(6 + symbolLength, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(10 + symbolLength, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long nanoSecondsSinceEpoch = timeStampBuffer.getLong();
		
		ByteBuffer volumeBuffer = bytes.slice(18 + symbolLength, 4);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = Integer.toUnsignedLong(volumeBuffer.getInt());
		
		return new Trade(symbol, price, size, nanoSecondsSinceEpoch, totalVolume);
	}
	
}