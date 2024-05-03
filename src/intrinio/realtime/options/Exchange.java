package intrinio.realtime.options;

import java.util.HashMap;

public enum Exchange {
	NYSE_AMERICAN((byte)'A'),
    BOSTON((byte)'B'),
    CBOE((byte)'C'),
    MIAMI_EMERALD((byte)'D'),
    BATS_EDGX((byte)'E'),
    ISE_GEMINI((byte)'H'),
    ISE((byte)'I'),
    MERCURY((byte)'J'),
    MIAMI((byte)'M'),
    MIAMI_PEARL((byte)'O'),
    NYSE_ARCA((byte)'P'),
    NASDAQ((byte)'Q'),
    NASDAQ_BX((byte)'T'),
    MEMX((byte)'U'),
    CBOE_C2((byte)'W'),
    PHLX((byte)'X'),
    BATS_BZX((byte)'Z');
    
	private static final HashMap<Byte,Exchange> exchangeCodeMap = new HashMap<Byte,Exchange>();
	
	static {
		for (Exchange e: values()) {
			exchangeCodeMap.put(e.code, e);
		}
	}
	
    public final byte code;
    private Exchange(byte code) {
    	this.code = code;
    }
    
    public static Exchange valueOfCode(byte code) {
    	return exchangeCodeMap.get(code);
    }
    
}