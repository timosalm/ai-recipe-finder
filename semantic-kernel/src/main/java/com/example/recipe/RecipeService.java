package com.example.recipe;

import com.example.semantickernel.DocumentEmbedding;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.data.VectorStoreTextSearch;
import com.microsoft.semantickernel.data.VectorStoreTextSearchOptions;
import com.microsoft.semantickernel.data.textsearch.DefaultTextSearchStringMapper;
import com.microsoft.semantickernel.data.textsearch.TextSearchOptions;
import com.microsoft.semantickernel.data.vectorsearch.VectorSearchResults;
import com.microsoft.semantickernel.data.vectorsearch.VectorizableTextSearch;
import com.microsoft.semantickernel.data.vectorsearch.VectorizedSearch;
import com.microsoft.semantickernel.data.vectorstorage.VectorStore;
import com.microsoft.semantickernel.data.vectorstorage.VectorStoreRecordCollectionOptions;
import com.microsoft.semantickernel.data.vectorstorage.options.GetRecordOptions;
import com.microsoft.semantickernel.data.vectorstorage.options.VectorSearchOptions;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.PromptExecutionSettings;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.semanticfunctions.KernelFunctionArguments;
import com.microsoft.semantickernel.semanticfunctions.PromptTemplateConfig;
import com.microsoft.semantickernel.semanticfunctions.PromptTemplateFactory;
import com.microsoft.semantickernel.semanticfunctions.annotations.DefineKernelFunction;
import com.microsoft.semantickernel.services.textembedding.TextEmbeddingGenerationService;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);
	private static String VECTORSTORE_COLLECTION_NAME = "recipes";

	private final Kernel defaultKernel;
	private final Kernel kernelWithToolCalling;
	private final VectorStore vectorStore;
	private final VectorStoreRecordCollectionOptions collectionOptions;
	private final TextEmbeddingGenerationService embeddingGenerationService;

	@Value("classpath:/prompts/recipe-for-ingredients")
	private Resource recipeForIngredientsPromptResource;

	@Value("classpath:/prompts/recipe-for-available-ingredients")
	private Resource recipeForAvailableIngredientsPromptResource;

	@Value("classpath:/prompts/prefer-own-recipe")
	private Resource preferOwnRecipePromptResource;

	@Value("classpath:/prompts/fix-json-response")
	private Resource fixJsonResponsePromptResource;

	@Value("${app.available-ingredients-in-fridge}")
    private List<String> availableIngredientsInFridge;

    RecipeService(Kernel defaultKernel, @Lazy @Qualifier("kernelWithToolCalling") Kernel kernelWithToolCalling,
				  VectorStore vectorStore, VectorStoreRecordCollectionOptions<String, DocumentEmbedding> collectionOptions,
				  TextEmbeddingGenerationService embeddingGenerationService) {
		this.defaultKernel = defaultKernel;
		this.kernelWithToolCalling = kernelWithToolCalling;
		this.vectorStore = vectorStore;
		this.collectionOptions = collectionOptions;
		this.embeddingGenerationService = embeddingGenerationService;
	}

    void addRecipeDocumentForRag(Resource pdfResource) throws IOException {
        log.info("Add recipe document {} for rag", pdfResource.getFilename());

		var pdfParser = new PDFParser(new RandomAccessReadBuffer(pdfResource.getInputStream()));
		var document = pdfParser.parse();
		var documents = new Splitter().split(document);

		var collection = vectorStore.<String, DocumentEmbedding>getCollection(VECTORSTORE_COLLECTION_NAME, collectionOptions);
		collection.createCollectionIfNotExistsAsync().block();

		var textStripper = new PDFTextStripper();
		List<DocumentEmbedding> documentsEmbeddings = documents.stream().map(d -> {
			try {
				var content = textStripper.getText(d);
				var embedding = embeddingGenerationService.generateEmbeddingAsync(content).block().getVector();
				return new DocumentEmbedding(UUID.randomUUID().toString(), embedding, content);
			} catch (IOException e) {
				return null;
			}
		}).filter((Objects::nonNull)).toList();
		/*
		var ids = (List<String>) collection.upsertBatchAsync(documentsEmbeddings, null).block();
		var data = (List<DocumentEmbedding>)collection.<String>getBatchAsync(ids, new GetRecordOptions(true)).block();

		System.out.println(data);

		var queryVector = embeddingGenerationService.generateEmbeddingAsync("Cheese").block().getVector();
		var results = collection.searchAsync(queryVector, VectorSearchOptions.createDefault("embedding")).block();
		System.out.println(results);*/

	}

    Recipe fetchRecipeFor(List<String> ingredients, boolean preferAvailableIngredients, boolean preferOwnRecipes) throws IOException {
        Recipe recipe;
        var ingredientsStr = String.join(",", ingredients);
        if (!preferAvailableIngredients && !preferOwnRecipes) {
			recipe = fetchRecipeFor(ingredientsStr);
		} else if (preferAvailableIngredients && !preferOwnRecipes) {
			recipe = fetchRecipeWithToolCallingFor(ingredientsStr);
        } else if (!preferAvailableIngredients && preferOwnRecipes) {
            recipe = fetchRecipeWithRagFor(ingredientsStr);
        } else {
            recipe = fetchRecipeWithRagAndToolCallingFor(ingredientsStr);
        }
		return recipe;
    }

	private Recipe fetchRecipeFor(String ingredientsStr) throws IOException {
		var systemPrompt = fixJsonResponsePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var userPromptTemplate = recipeForIngredientsPromptResource.getContentAsString(StandardCharsets.UTF_8);
		var combinedPromptTemplate = String.join(systemPrompt, "\n\n", userPromptTemplate);
		var arguments = KernelFunctionArguments.builder().withVariable("ingredients", ingredientsStr).build();

		var promptExecutionSettings = PromptExecutionSettings.builder()
				.withJsonSchemaResponseFormat(Recipe.class)
				.withMaxTokens(800)
				.build();

		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				.build();

		return 	defaultKernel.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				.withResultTypeAutoConversion(Recipe.class)
				.block().getResult();
	}

	private Recipe fetchRecipeWithToolCallingFor(String ingredientsStr) throws IOException {
		var systemPrompt = fixJsonResponsePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var userPromptTemplate = recipeForAvailableIngredientsPromptResource.getContentAsString(StandardCharsets.UTF_8);
		var combinedPromptTemplate = String.join(systemPrompt, "\n\n", userPromptTemplate);
		var arguments = KernelFunctionArguments.builder().withVariable("ingredients", ingredientsStr).build();

		var promptExecutionSettings = PromptExecutionSettings.builder()
				.withJsonSchemaResponseFormat(Recipe.class)
				.withMaxTokens(800)
				.build();

		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				.withToolCallBehavior(ToolCallBehavior.allowAllKernelFunctions(true))
				.build();

		return 	kernelWithToolCalling.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				.withResultTypeAutoConversion(Recipe.class)
				.block().getResult();
	}

	@DefineKernelFunction(name = "fetch_ingredients_available_at_home", description = "Fetches ingredients that are available at home",
            returnType = "java.util.List")
    public List<String> fetchIngredientsAvailableAtHome() {
        log.info("Fetching ingredients available at home function called by LLM");
        return availableIngredientsInFridge;
    }

	// WIP
	private Recipe fetchRecipeWithRagFor(String ingredientsStr) throws IOException {

		var systemPrompt = fixJsonResponsePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var ragSystemPrompt = preferOwnRecipePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var userPromptTemplate = recipeForAvailableIngredientsPromptResource.getContentAsString(StandardCharsets.UTF_8);
		var arguments = KernelFunctionArguments.builder().withVariable("ingredients", ingredientsStr).build();

		// Retrieve
		var userPrompt = PromptTemplateFactory.build(
				PromptTemplateConfig.builder().withTemplate(userPromptTemplate).build()
		).renderAsync(defaultKernel, arguments, null).block();

		var collection = vectorStore.getCollection(VECTORSTORE_COLLECTION_NAME, collectionOptions);
		collection.createCollectionIfNotExistsAsync().block();

		var vectorStoreTextSearch = VectorStoreTextSearch.builder()
				.withTextEmbeddingGenerationService(embeddingGenerationService)
				.withVectorizedSearch(collection).build();
		var results = vectorStoreTextSearch.getSearchResultsAsync(userPrompt, TextSearchOptions.createDefault()).block();

		var combinedPromptTemplate = "";/*String.join("\n\n",
				Stream.concat(results.getResults().stream(),
						List.of(systemPrompt, userPromptTemplate, ragSystemPrompt).stream()).toList());*/

		var promptExecutionSettings = PromptExecutionSettings.builder()
				.withJsonSchemaResponseFormat(Recipe.class)
				.withMaxTokens(800)
				.build();

		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				.build();

		return 	kernelWithToolCalling.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				.withResultTypeAutoConversion(Recipe.class)
				.block().getResult();
	}

	private Recipe fetchRecipeWithRagAndToolCallingFor(String ingredientsStr) {
		throw new NotImplementedException();
	}
}