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
    NYSE_ARCA((byte)'N'),
    MIAMI_PEARL((byte)'O'),
    NASDAQ((byte)'Q'),
    MIAX_SAPPHIRE((byte)'S'),
    NASDAQ_BX((byte)'T'),
    MEMX((byte)'U'),
    CBOE_C2((byte)'W'),
    PHLX((byte)'X'),
    BATS_BZX((byte)'Z'),
    UNKNOWN((byte)'?');
    
	private static final HashMap<Byte,Exchange> exchangeCodeMap = new HashMap<Byte,Exchange>();
	
	static {
		for (Exchange e: values()) {
			exchangeCodeMap.put(e.code, e);
            byte lower = (byte)(Character.toLowerCase((char)e.code));
            exchangeCodeMap.put(lower, e);
		}
        exchangeCodeMap.put((byte)'P', Exchange.NYSE_ARCA); //Two chars mapped to same exchange. This one is depreciated
        exchangeCodeMap.put((byte)'p', Exchange.NYSE_ARCA); //Two chars mapped to same exchange. This one is depreciated
	}
	
    public final byte code;
    private Exchange(byte code) {
    	this.code = code;
    }
    
    public static Exchange valueOfCode(byte code)
    {
        return exchangeCodeMap.getOrDefault(code, Exchange.UNKNOWN);
    }
    
}