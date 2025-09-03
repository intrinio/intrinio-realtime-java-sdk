package intrinio.realtime.composite;

public record Greek (double ImpliedVolatility, double Delta, double Gamma, double Theta, double Vega, boolean IsValid){}