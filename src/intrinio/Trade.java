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
	 SubProvider subProvider,
	 char marketCenter,
	 double price,
	 long size,
	 long timestamp,
	 long totalVolume,
	 String conditions) {

	public boolean isDarkpool(){
		return marketCenter == 'D' || marketCenter == 'E'  || marketCenter == '\0' || marketCenter == Character.MIN_VALUE;
	}
	
	public String toString() {
		return
			"Trade (" +
			"Symbol: " + this.symbol +
			", SubProvider: " + this.subProvider +
			", MarketCenter: " + this.marketCenter +
			", Price: " + this.price +
			", Size: " + this.size +
			", Total Volume: " + this.totalVolume +
			", Timestamp: " + this.timestamp +
			", Conditions: " + this.conditions +
			")";
	}
	
	public static Trade parse(byte[] bytes) {
		int symbolLength = bytes[2];
		int conditionLength = bytes[26 + symbolLength];
		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 3, symbolLength)).toString();

		SubProvider subProvider;
		switch (bytes[3 + symbolLength]) {
			case 0: subProvider = SubProvider.NONE;
				break;
			case 1: subProvider = SubProvider.CTA_A;
				break;
			case 2: subProvider = SubProvider.CTA_B;
				break;
			case 3: subProvider = SubProvider.UTP;
				break;
			case 4: subProvider = SubProvider.OTC;
				break;
			case 5: subProvider = SubProvider.NASDAQ_BASIC;
				break;
			case 6: subProvider = SubProvider.IEX;
				break;
			default: subProvider = SubProvider.IEX;
		}

		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, 6 + symbolLength, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getFloat();

		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, 10 + symbolLength, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());

		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 14 + symbolLength, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long nanoSecondsSinceEpoch = timeStampBuffer.getLong();

		ByteBuffer volumeBuffer = ByteBuffer.wrap(bytes, 22 + symbolLength, 4);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = Integer.toUnsignedLong(volumeBuffer.getInt());

		ByteBuffer marketCenterBuffer = ByteBuffer.wrap(bytes, 4 + symbolLength, 2);
		marketCenterBuffer.order(ByteOrder.LITTLE_ENDIAN);
		char marketCenter = marketCenterBuffer.getChar();

		String condition = "";
		if (conditionLength > 0) {
			condition = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 27 + symbolLength, conditionLength)).toString();
		}

		return new Trade(symbol, subProvider, marketCenter, price, size, nanoSecondsSinceEpoch, totalVolume, condition);
	}

	public static Trade parse(ByteBuffer bytes) {
		int symbolLength = bytes.get(2);
		int conditionLength = bytes.get(26 + symbolLength);
		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(3, symbolLength)).toString();

		SubProvider source;
		switch (bytes.get(3 + symbolLength)) {
			case 0: source = SubProvider.NONE;
				break;
			case 1: source = SubProvider.CTA_A;
				break;
			case 2: source = SubProvider.CTA_B;
				break;
			case 3: source = SubProvider.UTP;
				break;
			case 4: source = SubProvider.OTC;
				break;
			case 5: source = SubProvider.NASDAQ_BASIC;
				break;
			case 6: source = SubProvider.IEX;
				break;
			default: source = SubProvider.IEX;
		}

		ByteBuffer priceBuffer = bytes.slice(6 + symbolLength, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getFloat();

		ByteBuffer sizeBuffer = bytes.slice(10 + symbolLength, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());

		ByteBuffer timeStampBuffer = bytes.slice(14 + symbolLength, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long nanoSecondsSinceEpoch = timeStampBuffer.getLong();

		ByteBuffer volumeBuffer = bytes.slice(22 + symbolLength, 4);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = Integer.toUnsignedLong(volumeBuffer.getInt());

		ByteBuffer marketCenterBuffer = bytes.slice(4 + symbolLength, 2);
		marketCenterBuffer.order(ByteOrder.LITTLE_ENDIAN);
		char marketCenter = marketCenterBuffer.getChar();

		String condition = "";
		if (conditionLength > 0) {
			condition = StandardCharsets.US_ASCII.decode(bytes.slice(27 + symbolLength, conditionLength)).toString();
		}

		return new Trade(symbol, source, marketCenter, price, size, nanoSecondsSinceEpoch, totalVolume, condition);
	}
	
}