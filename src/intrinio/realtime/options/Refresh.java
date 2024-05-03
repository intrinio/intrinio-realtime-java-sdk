package intrinio.realtime.options;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record Refresh(String contract, long openInterest, double openPrice, double closePrice, double highPrice, double lowPrice){
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
        return String.format("Refresh (Contract: %s, OpenInterest: %s, OpenPrice: %s, ClosePrice: %s, HighPrice: %s, LowPrice: %s)",
                this.contract,
                this.openInterest,
                this.openPrice,
                this.closePrice,
                this.highPrice,
                this.lowPrice);
    }

    public static Refresh parse(byte[] bytes) {
        //byte structure:
        // contract length [0]
        // contract [1-21]
        // event type [22]
        // price type [23]
        // open interest [24-27]
        // open price [28-31]
        // close price [32-35]
        // high price [36-39]
        // low price [40-43]

        String contract = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 1, bytes[0])).toString();

        PriceType scaler = PriceType.fromInt(bytes[23]);

        ByteBuffer openInterestBuffer = ByteBuffer.wrap(bytes, 24, 4);
        openInterestBuffer.order(ByteOrder.LITTLE_ENDIAN);
        long openInterest = Integer.toUnsignedLong(openInterestBuffer.getInt());

        ByteBuffer openPriceBuffer = ByteBuffer.wrap(bytes, 28, 4);
        openPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double openPrice = scaler.getScaledValue(openPriceBuffer.getInt());

        ByteBuffer closePriceBuffer = ByteBuffer.wrap(bytes, 32, 4);
        closePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double closePrice = scaler.getScaledValue(closePriceBuffer.getInt());

        ByteBuffer highPriceBuffer = ByteBuffer.wrap(bytes, 36, 4);
        highPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double highPrice = scaler.getScaledValue(highPriceBuffer.getInt());

        ByteBuffer lowPriceBuffer = ByteBuffer.wrap(bytes, 40, 4);
        lowPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double lowPrice = scaler.getScaledValue(lowPriceBuffer.getInt());

        return new Refresh(Refresh.formatContract(contract), openInterest, openPrice, closePrice, highPrice, lowPrice);
    }

    public static Refresh parse(ByteBuffer bytes) {
        //byte structure:
        // contract length [0]
        // contract [1-21]
        // event type [22]
        // price type [23]
        // open interest [24-27]
        // open price [28-31]
        // close price [32-35]
        // high price [36-39]
        // low price [40-43]

        String contract = StandardCharsets.US_ASCII.decode(bytes.slice(1, bytes.get(0))).toString();

        PriceType scaler = PriceType.fromInt(bytes.get(23));

        ByteBuffer openInterestBuffer = bytes.slice(24, 4);
        openInterestBuffer.order(ByteOrder.LITTLE_ENDIAN);
        long openInterest = Integer.toUnsignedLong(openInterestBuffer.getInt());

        ByteBuffer openPriceBuffer = bytes.slice(28, 4);
        openPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double openPrice = scaler.getScaledValue(openPriceBuffer.getInt());

        ByteBuffer closePriceBuffer = bytes.slice(32, 4);
        closePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double closePrice = scaler.getScaledValue(closePriceBuffer.getInt());

        ByteBuffer highPriceBuffer = bytes.slice(36, 4);
        highPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double highPrice = scaler.getScaledValue(highPriceBuffer.getInt());

        ByteBuffer lowPriceBuffer = bytes.slice(40, 4);
        lowPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double lowPrice = scaler.getScaledValue(lowPriceBuffer.getInt());

        return new Refresh(Refresh.formatContract(contract), openInterest, openPrice, closePrice, highPrice, lowPrice);
    }
}
