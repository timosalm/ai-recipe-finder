package com.example.recipe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.sound.midi.SysexMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@Service
class RecipeAiServices {

	// With the Spring Boot Starter chatModel, tools, etc. will be configured automatically.
	// Therefore, switching to explicit wiring mode is required when no tools should be registered.
	// To support multiple AI providers without relying on annotations like @Profile,
	// RecipeFinderConfiguration registers the auto-configured ChatModel bean under the generic name "chatModel".
	// This breaks LangChain4j's current automatic wiring mechanism, so other AiService beans must also be wired explicitly.
	@AiService(wiringMode = EXPLICIT, chatModel = "chatModel")
	interface Standard {
		@UserMessage(fromResource = "/prompts/recipe-for-ingredients")
		@SystemMessage(fromResource = "/prompts/fix-json-response")
		Recipe find(String ingredients);
	}

	@AiService(wiringMode = EXPLICIT, chatModel = "chatModel", tools = {"recipeService"})
	interface WithTools {
		@UserMessage(fromResource = "/prompts/recipe-for-available-ingredients")
		@SystemMessage(fromResource = "/prompts/fix-json-response")
		Recipe find(String ingredients);
	}

	@AiService(wiringMode = EXPLICIT, chatModel = "chatModel", contentRetriever = "embeddingStoreContentRetriever")
	interface WithRag {
		@UserMessage(fromResource = "/prompts/recipe-for-ingredients")
		@SystemMessage(fromResource = "/prompts/fix-json-response-and-prefer-own-recipe")
		Recipe find(String ingredients);
	}

	interface WithToolsAndRag {
		Recipe find(String ingredients);
	}

	@Bean
	RecipeAiServices.WithToolsAndRag recipeAiServiceWithToolsAndRag(ChatModel chatModel, RecipeService recipeService,
		ContentRetriever embeddingStoreContentRetriever,
		@Value("classpath:/prompts/recipe-for-available-ingredients") Resource userPromptResource,
		@Value("classpath:/prompts/fix-json-response-and-prefer-own-recipe") Resource systemPromptResource) {
		return ingredients -> {
			try {
				var userMessage = PromptTemplate.from(userPromptResource.getContentAsString(StandardCharsets.UTF_8))
						.apply(Map.of("ingredients", ingredients)).toUserMessage();
				var systemMessage = dev.langchain4j.data.message.SystemMessage.from(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));

				var relevantContents = embeddingStoreContentRetriever.retrieve(Query.from(userMessage.singleText()));
				var additionalContext = relevantContents.stream().map(c -> c.textSegment().text())
						.collect(Collectors.joining("\n\n"));
				var additionalContextMessage = dev.langchain4j.data.message.SystemMessage.from(additionalContext);

				var request = ChatRequest.builder()
						.messages(List.of(userMessage, systemMessage, additionalContextMessage))
						.toolSpecifications(ToolSpecifications.toolSpecificationsFrom(recipeService))
						.build();
				var response = chatModel.chat(request).aiMessage();
				// TODO Handle Tool Calls
				throw new NotImplementedException();
				// return new ObjectMapper().readValue(response.text(), Recipe.class);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
	}
}