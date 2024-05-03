package intrinio.realtime.equities;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import com.google.gson.Gson;

public class Config {
	
	private static final String filename = "intrinio/realtime/equities/config.json";
	
	private String equitiesApiKey;
	private Provider equitiesProvider = Provider.NONE;
	private String equitiesIpAddress;
	private String[] equitiesSymbols;
	private boolean equitiesTradesOnly = true;
	private int equitiesNumThreads = 4;
	
	public Config(String equitiesApiKey, Provider equitiesProvider, String equitiesIpAddress, String[] equitiesSymbols, boolean equitiesTradesOnly, int equitiesNumThreads) throws Exception {
		this.equitiesApiKey = equitiesApiKey;
		this.equitiesProvider = equitiesProvider;
		this.equitiesIpAddress = equitiesIpAddress;
		this.equitiesSymbols = equitiesSymbols;
		this.equitiesTradesOnly = equitiesTradesOnly;
		this.equitiesNumThreads = equitiesNumThreads;
		
		if (this.equitiesApiKey.isBlank()) {
			throw new Exception("You must provide a valid API key");
		}
		if (this.equitiesProvider == Provider.NONE) {
			throw new Exception("You must specify a valid provider");
		}
		if ((this.equitiesProvider == Provider.MANUAL) && this.equitiesIpAddress.isBlank()) {
			throw new Exception("You must specify an IP address for manual configuration");
		}
	}
	
	public String getEquitiesApiKey() {
		return equitiesApiKey;
	}

	public Provider getEquitiesProvider() {
		return equitiesProvider;
	}

	public String getEquitiesIpAddress() {
		return equitiesIpAddress;
	}

	public String[] getEquitiesSymbols() {
		return equitiesSymbols;
	}

	public boolean isEquitiesTradesOnly() {
		return equitiesTradesOnly;
	}

	public int getEquitiesNumThreads() {
		return equitiesNumThreads;
	}
	
	public String toString() {
		return String.format("apiKey = %s, provider = %s, ipAddress = %s, symbols = %s, tradesOnly = %b, numThreads = %d",
				this.equitiesApiKey,
				this.equitiesProvider,
				this.equitiesIpAddress,
				String.join(", ", this.equitiesSymbols),
				this.equitiesTradesOnly,
				this.equitiesNumThreads);
	}

	public static Config load() {
		System.out.println("Loading application configuration");
		try {
			InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(Config.filename);
			Reader reader = new InputStreamReader(inputStream);
			Gson gson = new Gson();
			Config config = gson.fromJson(reader, Config.class);
			return config;
		} catch (Exception e) {
			return null;
		}
	}
}