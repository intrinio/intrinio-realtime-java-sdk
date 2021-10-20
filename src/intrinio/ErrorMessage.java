package intrinio;

class Payload {
	private String status;
	private String response;
	Payload() {}
	public String getStatus() {
		return status;
	}
	public String getResponse() {
		return response;
	}
}

public class ErrorMessage {
	private Payload payload;
	private String event;
	ErrorMessage() {}
	public Payload getPayload() {
		return payload;
	}
	public String getEvent() {
		return event;
	}
}
