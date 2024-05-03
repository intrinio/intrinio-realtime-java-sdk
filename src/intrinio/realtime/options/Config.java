package intrinio.realtime.options;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class Config {
	
	private static final String filename = "intrinio/realtime/options/config.json";
	
	private String optionsApiKey;
	private Provider optionsProvider = Provider.NONE;
	private String optionsIpAddress;
	private String[] optionsSymbols;
	private int optionsNumThreads = 4;
	
	public Config(String optionsApiKey, Provider optionsProvider, String optionsIpAddress, String[] optionsSymbols, int optionsNumThreads) throws Exception {
		this.optionsApiKey = optionsApiKey;
		this.optionsProvider = optionsProvider;
		this.optionsIpAddress = optionsIpAddress;
		this.optionsSymbols = optionsSymbols;
		this.optionsNumThreads = optionsNumThreads;
		
		if (this.optionsApiKey.isBlank()) {
			throw new Exception("You must provide a valid API key");
		}
		if (this.optionsProvider == Provider.NONE) {
			throw new Exception("You must specify a valid provider");
		}
		if ((this.optionsProvider == Provider.MANUAL) && this.optionsIpAddress.isBlank()) {
			throw new Exception("You must specify an IP address for manual configuration");
		}
	}
	
	public String getOptionsApiKey() {
		return optionsApiKey;
	}

	public Provider getOptionsProvider() {
		return optionsProvider;
	}

	public String getOptionsIpAddress() {
		return optionsIpAddress;
	}

	public String[] getOptionsSymbols() {
		return optionsSymbols;
	}

	public int getOptionsNumThreads() {
		return optionsNumThreads;
	}
	
	public String toString() {
		return String.format("apiKey = %s, provider = %s, ipAddress = %s, symbols = %s, numThreads = %d",
				this.optionsApiKey,
				this.optionsProvider,
				this.optionsIpAddress,
				(this.optionsSymbols == null ? "[]" : "[ " + String.join(", ", this.optionsSymbols) + " ]"),
				this.optionsNumThreads);
	}

	public static Config load() {
		System.out.println("Loading application configuration");
		try {
			InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(Config.filename);
			Reader reader = new InputStreamReader(inputStream);
			Gson gson = new Gson();
			Config config = gson.fromJson(reader, Config.class);
			System.out.println(config);
			return config;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}