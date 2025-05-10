package com.example.semantickernel;

import java.util.Map;

class ModelProperties {

	private String modelName;
	private Map<String, String> options;

	String getModelName() {
		return modelName;
	}

	void setModelName(String modelName) {
		this.modelName = modelName;
	}

	Map<String, String> getOptions() {
		return options;
	}

	void setOptions(Map<String, String> options) {
		this.options = options;
	}
}
