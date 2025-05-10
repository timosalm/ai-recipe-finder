package com.example.semantickernel;

import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.aiservices.openai.textcompletion.OpenAITextGenerationService;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.textcompletion.TextGenerationService;
import com.microsoft.semantickernel.services.textembedding.TextEmbeddingGenerationService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.StringUtils.capitalize;

public class AiModelsDisplayNames {

	private List<String> names;

	public AiModelsDisplayNames() {
		this.names = new ArrayList<>();
	}

	static AiModelsDisplayNames from(ChatCompletionService chatCompletionService) {
		var aiModelsDisplayNames = new AiModelsDisplayNames();
		var textGenerationServiceClass = chatCompletionService.getClass();
		var chatModelProvider = textGenerationServiceClass.getSimpleName().replace("ChatCompletion", "");
		if (chatCompletionService instanceof OpenAIChatCompletion &&
				StringUtils.hasText(((OpenAIChatCompletion)chatCompletionService).getDeploymentName())) {
			chatModelProvider = "Azure " + chatModelProvider;
		}
		var chatModelName = chatCompletionService.getModelId();
		if (StringUtils.hasText(chatModelName)) {
			aiModelsDisplayNames.addName("%s (%s)".formatted(chatModelProvider, capitalize(chatModelName)));
		} else {
			aiModelsDisplayNames.addName(chatModelProvider);
		}
		return aiModelsDisplayNames;
	}

	void addName(String name) {
		this.names.add(name);
	}

	public List<String> getNames() {
		return names;
	}

	void setNames(List<String> names) {
		this.names = names;
	}
}
