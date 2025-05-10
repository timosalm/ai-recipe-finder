package com.example.semantickernel;

class OpenAiConnectionProperties {

	private String endpoint;
	private String apiKey;
	OpenAiConnectionProperties() {
	}

	String getEndpoint() {
		return endpoint;
	}

	void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	String getApiKey() {
		return apiKey;
	}

	void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
}
