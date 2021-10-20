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

enum QuoteType {
	INVALID,
	ASK,
	BID
}

public record Quote(QuoteType type, String symbol, double price, long size, ZonedDateTime timestamp) {
	
	public String toString() {
		String s =
				"Quote (" +
				"Type: " + this.type +
				", Symbol: " + this.symbol +
				", Price: " + this.price +
				", Size: " + this.size +
				", Timestamp: " + this.timestamp +
				")";
		return s;
	}
	
	public static Quote parse(byte[] bytes, int symbolLength) {
		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 2, symbolLength)).toString();
		QuoteType type;
		switch (bytes[0]) {
		case 1: type = QuoteType.ASK;
			break;
		case 2: type = QuoteType.BID;
			break;
		default: type = QuoteType.INVALID;
		}
		
		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, 2 + symbolLength, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getInt() / 10000.0;
		
		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, 6 + symbolLength, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 10 + symbolLength, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long nanoSecondsSinceEpoch = timeStampBuffer.getLong();
		long epochSecond = nanoSecondsSinceEpoch / 1000000000L;
		long nanoOfSecond = nanoSecondsSinceEpoch % 1000000000L;
	    ZoneId tz = ZoneId.of("America/New_York");
	    ZoneRules rules = tz.getRules();
        Instant instant = Instant.ofEpochSecond(epochSecond, (int)nanoOfSecond);
        ZoneOffset offset = rules.getOffset(instant);
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(epochSecond, (int)nanoOfSecond, offset);
        ZonedDateTime zdt = ZonedDateTime.ofLocal(ldt, tz, offset);
		
		return new Quote(type, symbol, price, size, zdt);
	}
	
	public static Quote parse(ByteBuffer bytes, int symbolLength) {
		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(2, symbolLength)).toString();
		QuoteType type;
		switch (bytes.get(0)) {
		case 1: type = QuoteType.ASK;
			break;
		case 2: type = QuoteType.BID;
			break;
		default: type = QuoteType.INVALID;
		}
		
		ByteBuffer priceBuffer = bytes.slice(2 + symbolLength, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getInt() / 10000.0;
		
		ByteBuffer sizeBuffer = bytes.slice(6 + symbolLength, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(10 + symbolLength, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long nanoSecondsSinceEpoch = timeStampBuffer.getLong();
		long epochSecond = nanoSecondsSinceEpoch / 1000000000L;
		long nanoOfSecond = nanoSecondsSinceEpoch % 1000000000L;
	    ZoneId tz = ZoneId.of("America/New_York");
	    ZoneRules rules = tz.getRules();
        Instant instant = Instant.ofEpochSecond(epochSecond, (int)nanoOfSecond);
        ZoneOffset offset = rules.getOffset(instant);
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(epochSecond, (int)nanoOfSecond, offset);
        ZonedDateTime zdt = ZonedDateTime.ofLocal(ldt, tz, offset);
		
		return new Quote(type, symbol, price, size, zdt);
	}
	
}