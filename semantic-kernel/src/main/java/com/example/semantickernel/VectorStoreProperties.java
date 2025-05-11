package com.example.semantickernel;

class VectorStoreProperties {

	private String host = "localhost";
	private String username;
	private String password;
	private int port;

	String getHost() {
		return host;
	}

	void setHost(String host) {
		this.host = host;
	}

	String getUsername() {
		return username;
	}

	void setUsername(String username) {
		this.username = username;
	}

	String getPassword() {
		return password;
	}

	void setPassword(String password) {
		this.password = password;
	}

	int getPort() {
		return port;
	}

	void setPort(int port) {
		this.port = port;
	}
}
