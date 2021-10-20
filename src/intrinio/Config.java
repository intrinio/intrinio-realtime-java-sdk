package intrinio;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import com.google.gson.Gson;

public class Config {
	
	private static final String filename = "intrinio/config.json";
	
	private String apiKey;
	private Provider provider = Provider.NONE;
	private String ipAddress;
	private String[] symbols;
	private boolean tradesOnly = true;
	private int numThreads = 4;
	
	public Config(String apiKey, Provider provider, String ipAddress, String[] symbols, boolean tradesOnly, int numThreads) {
		this.apiKey = apiKey;
		this.provider = provider;
		this.ipAddress = ipAddress;
		this.symbols = symbols;
		this.tradesOnly = tradesOnly;
		this.numThreads = numThreads;
	}
	
	public String getApiKey() {
		return apiKey;
	}

	public Provider getProvider() {
		return provider;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public String[] getSymbols() {
		return symbols;
	}

	public boolean isTradesOnly() {
		return tradesOnly;
	}

	public int getNumThreads() {
		return numThreads;
	}
	
	public String toString() {
		return String.format("apiKey = %s, provider = %s, ipAddress = %s, symbols = %s, tradesOnly = %b, numThreads = %d",
				this.apiKey,
				this.provider,
				this.ipAddress,
				String.join(", ", this.symbols),
				this.tradesOnly,
				this.numThreads);
	}

	public static Config load() {
		System.out.println("Loading application configuration");
		try {
			InputStream inputStream = ClassLoader.getSystemResourceAsStream(Config.filename);
			Reader reader = new InputStreamReader(inputStream);
			Gson gson = new Gson();
			Config config = gson.fromJson(reader, Config.class);
			System.out.println(config);
			if (config.apiKey.isBlank()) {
				throw new Exception("You must provide a valid API key");
			}
			if (config.provider == Provider.NONE) {
				throw new Exception("You must specify a valid provider");
			}
			if ((config.provider == Provider.MANUAL) && config.ipAddress.isBlank()) {
				throw new Exception("You must specify an IP address for manual configuration");
			}
			return config;
		} catch (Exception e) {
			return null;
		}
	}
}