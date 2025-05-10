package com.example;

import org.springframework.util.StringUtils;

class AzureOpenAiModelProperties extends ModelProperties {

	private String deploymentName;

	AzureOpenAiModelProperties() {}

	String getDeploymentName() {
		return deploymentName;
	}

	void setDeploymentName(String deploymentName) {
		this.deploymentName = deploymentName;

		if (!StringUtils.hasText(this.getModelName())) {
			this.setModelName(deploymentName);
		}
	}
}
