package com.example.recipe;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import org.springframework.stereotype.Service;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@Service
class RecipeAiServices {

	// For AiService API example without annotations
	interface Standard {
		Recipe find(dev.langchain4j.data.message.UserMessage userMessage);
	}

	// With the Spring Boot Starter chatModel, tools, etc. will be configured automatically.
	// Therefore, switching to explicit wiring mode is required when more control is needed.
	// To support multiple AI providers without relying on annotations like @Profile,
	// RecipeFinderConfiguration registers the auto-configured ChatModel bean under the generic name "chatModel".
	// This breaks LangChain4j's current automatic wiring mechanism, so other AiService beans must also be wired explicitly.
	@AiService(wiringMode = EXPLICIT, chatModel = "chatModel", tools = {"recipeService"})
	interface WithTools {
		@UserMessage(fromResource = "/prompts/recipe-for-available-ingredients")
		@SystemMessage(fromResource = "/prompts/fix-json-response")
		Recipe find(String ingredients);
	}

	@AiService(wiringMode = EXPLICIT, chatModel = "chatModel", contentRetriever = "contentRetriever")
	interface WithRag {
		@UserMessage(fromResource = "/prompts/recipe-for-ingredients")
		@SystemMessage(fromResource = "/prompts/fix-json-response-and-prefer-own-recipe")
		Recipe find(String ingredients);
	}

	@AiService(wiringMode = EXPLICIT, chatModel = "chatModel", tools = {"recipeService"}, contentRetriever = "contentRetriever")
	interface WithToolsAndRag {
		@UserMessage(fromResource = "/prompts/recipe-for-ingredients")
		@SystemMessage(fromResource = "/prompts/fix-json-response-and-prefer-own-recipe")
		Recipe find(String ingredients);
	}
}