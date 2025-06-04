package com.example.recipe;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final RecipeAiServices.WithTools recipeAiServiceWithTools;
    private final RecipeAiServices.WithRag recipeAiServiceWithRag;
    private final RecipeAiServices.WithToolsAndRag recipeAiServiceWithToolsAndRag;
    private final Optional<ImageModel> imageModel;
    private final EmbeddingStoreIngestor embeddingStoreIngestor;
	private final ChatModel chatModel;

    @Value("classpath:/prompts/recipe-for-ingredients")
    private Resource recipeForIngredientsPromptResource;

    @Value("classpath:/prompts/fix-json-response")
    private Resource fixJsonResponsePromptResource;

	@Value("classpath:/prompts/image-for-recipe")
    private Resource imageForRecipePromptResource;

    @Value("${app.available-ingredients-in-fridge}")
    private List<String> availableIngredientsInFridge;

    // Constructor injection for autoconfigured AI services
    RecipeService(ChatModel chatModel, @Lazy RecipeAiServices.WithTools recipeAiServiceWithTools,
                  @Lazy RecipeAiServices.WithRag recipeAiServiceWithRag, @Lazy RecipeAiServices.WithToolsAndRag recipeAiServiceWithToolsAndRag,
                  Optional<ImageModel> imageModel, EmbeddingStoreIngestor embeddingStoreIngestor) {
		this.chatModel = chatModel;
        this.recipeAiServiceWithTools = recipeAiServiceWithTools;
        this.recipeAiServiceWithRag = recipeAiServiceWithRag;
        this.recipeAiServiceWithToolsAndRag = recipeAiServiceWithToolsAndRag;
        this.imageModel = imageModel;
        this.embeddingStoreIngestor = embeddingStoreIngestor;
	}

    // ETL pipeline orchestrating the flow from raw data sources to a structured vector store
    void addRecipeDocumentForRag(Resource pdfResource) throws IOException {
        log.info("Add recipe document {} for rag", pdfResource.getFilename());

        // Extract: Parses PDF document
        var documentParser = new ApachePdfBoxDocumentParser();
        var document = documentParser.parse(pdfResource.getInputStream());
        // Transforms(Splits text into chunks based on defined character count) and loads data into vector database
        embeddingStoreIngestor.ingest(document);
    }

    Recipe fetchRecipeFor(List<String> ingredients, boolean preferAvailableIngredients, boolean preferOwnRecipes) throws IOException {
        Recipe recipe;
        var ingredientsAsString = String.join(",", ingredients);
        if (!preferAvailableIngredients && !preferOwnRecipes) {
            recipe = fetchRecipeFor(ingredientsAsString);
        } else if (preferAvailableIngredients && !preferOwnRecipes) {
            recipe = recipeAiServiceWithTools.find(ingredientsAsString);
        } else if (!preferAvailableIngredients && preferOwnRecipes) {
            recipe = recipeAiServiceWithRag.find(ingredientsAsString);
        } else {
            recipe = recipeAiServiceWithToolsAndRag.find(ingredientsAsString);
        }

        if (imageModel.isPresent()) {
            log.info("Image generation for recipe '{}' started", recipe.name());
            // Only low-level API available for image models
            var imagePromptTemplate = PromptTemplate.from(imageForRecipePromptResource.getContentAsString(StandardCharsets.UTF_8))
						.apply(Map.of("recipe", recipe.name()));
			var generatedImage = imageModel.get().generate(imagePromptTemplate.text()).content();
            return new Recipe(recipe, generatedImage.url().toString());
        }

        return recipe;
    }

    // AiService API without annotations
    private Recipe fetchRecipeFor(String ingredientsAsString) throws IOException {
        // Prompt templates from the classpath
        var systemPrompt = fixJsonResponsePromptResource.getContentAsString(StandardCharsets.UTF_8);
        var userPromptTemplate = recipeForIngredientsPromptResource.getContentAsString(StandardCharsets.UTF_8);

        // Builder for high-abstraction API
        var recipeAiService = AiServices.builder(RecipeAiServices.Standard.class)
                .chatModel(chatModel)
                .systemMessageProvider(chatMemoryId -> systemPrompt)
                .build();

        // Helper class for prompt templating
        var userMessage = PromptTemplate.from(userPromptTemplate)
                .apply(Map.of("ingredients", ingredientsAsString))
                .toUserMessage();

        // The result is of type Recipe.class due to structured output
        return recipeAiService.find(userMessage);
    }

    // Defines a tool
    @Tool("Fetches ingredients that are available at home")
    List<String> fetchIngredientsAvailableAtHome() {
        log.info("Fetching ingredients available at home function called by LLM");
        return availableIngredientsInFridge;
    }
}