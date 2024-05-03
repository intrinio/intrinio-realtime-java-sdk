package intrinio.realtime.options;

public record Qualifiers(byte a, byte b, byte c, byte d) {
	public String toString() {
		return String.format("(%s, %s, %s, %s)", a, b, c, d);
	}
}